package dev.qtremors.acqua.downloader

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import dev.qtremors.acqua.data.history.HistoryRepository
import dev.qtremors.acqua.data.network.HttpMediaClient
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.platform.storage.MediaStorage
import java.io.File
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class DownloadExecutionResult(
    val uri: Uri,
    val title: String?,
    val mimeType: String,
    val mediaKind: MediaKind
)

interface DownloadExecution {
    suspend fun execute(
        request: DownloadWorkRequest,
        taskKey: String,
        onProgress: (YtDlpProgress) -> Unit,
        onPhase: (DownloadPhase) -> Unit = {}
    ): DownloadExecutionResult
}

class DownloadExecutor(
    context: Context,
    private val settings: AppSettingsRepository,
    history: HistoryRepository,
    mediaDownloader: HttpMediaClient,
    private val engineFactory: () -> YtDlpEngine = { YtDlpEngine(context.applicationContext) }
) : DownloadExecution {
    private val appContext = context.applicationContext
    private val storage = MediaStorage(appContext, history, settings, mediaDownloader)
    private val httpMedia = mediaDownloader

    override suspend fun execute(
        request: DownloadWorkRequest,
        taskKey: String,
        onProgress: (YtDlpProgress) -> Unit,
        onPhase: (DownloadPhase) -> Unit
    ): DownloadExecutionResult {
        onPhase(DownloadPhase.TRANSFER)
        DownloadNameOverrides.reopen(appContext, taskKey)
        val result = if (request.media.backend == dev.qtremors.acqua.domain.MediaBackend.DIRECT) {
            try {
                executeDirect(request.copy(media = request.media.copy(requestCookies = null,
                    explicitBrowserSessionAuthorized = false)), taskKey, onProgress, onPhase)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (publicFailure: Exception) {
                val couldNeedLogin = listOf("HTTP 401", "HTTP 403", "unsupported or incomplete file")
                    .any { publicFailure.message.orEmpty().contains(it, ignoreCase = true) }
                if (!settings.useBrowserSessions() || !couldNeedLogin) throw publicFailure
                val cookies = request.media.requestCookies.takeIf { request.media.explicitBrowserSessionAuthorized }
                    ?: runCatching { CookieManager.getInstance().getCookie(request.media.url) }
                    .getOrNull()?.takeIf(String::isNotBlank) ?: throw publicFailure
                executeDirect(request.copy(media = request.media.copy(requestCookies = cookies,
                    explicitBrowserSessionAuthorized = true)), taskKey, onProgress, onPhase)
            }
        } else {
            executeProcessed(request, taskKey, onProgress, onPhase)
        }
        DownloadNameOverrides.finish(appContext, taskKey)
        return result
    }

    private suspend fun executeDirect(
        request: DownloadWorkRequest,
        taskKey: String,
        onProgress: (YtDlpProgress) -> Unit,
        onPhase: (DownloadPhase) -> Unit
    ): DownloadExecutionResult = withContext(Dispatchers.IO) {
        val cookiesAllowed = settings.useBrowserSessions() && request.media.explicitBrowserSessionAuthorized
        val requestCookies = if (cookiesAllowed) {
            request.media.requestCookies ?: runCatching { CookieManager.getInstance().getCookie(request.media.url) }
                .getOrNull()
                ?.takeIf(String::isNotBlank)
        } else {
            null
        }
        val media = request.media.copy(requestCookies = requestCookies)
        val uri = if (!media.isAudio && !media.isVideo || !settings.mediaProcessingSettings().embedMetadata) storage.save(
            media,
            request.itemIndex,
            request.sourceUrl,
            itemCount = request.itemCount,
            finalDownloadName = { DownloadNameOverrides.freeze(appContext, taskKey) }
        ) { downloaded, total ->
            val percent = if (total > 0L) downloaded * 100f / total else 0f
            onProgress(
                YtDlpProgress(
                    percent = percent,
                    downloadedBytes = downloaded,
                    totalBytes = total
                )
            )
        } else {
            val directory = YtDlpTemporaryFiles.prepareTaskDirectory(appContext, "direct-$taskKey")
            try {
                val extension = media.fileExtension?.lowercase()?.replace(Regex("[^a-z0-9]"), "")
                    ?.take(12)?.takeIf(String::isNotBlank) ?: if (media.isAudio) "m4a" else "mp4"
                val source = File(directory, "source.$extension")
                val output = File(directory, "tagged.$extension")
                source.outputStream().use { stream ->
                    httpMedia.downloadToStreamCancellable(media, stream, requestCookies) { downloaded, total ->
                        onProgress(YtDlpProgress(percent = if (total > 0L) downloaded * 100f / total else 0f,
                            downloadedBytes = downloaded, totalBytes = total))
                    }
                }
                onPhase(DownloadPhase.PROCESSING)
                val preserve = settings.mediaProcessingSettings().preserveOriginalMetadata
                DirectMediaMetadata.write(appContext, source, output, request.sourceUrl,
                    if (preserve) null else System.currentTimeMillis())
                onPhase(DownloadPhase.FINALIZING)
                storage.saveProcessedFile(output,
                    media.copy(downloadName = DownloadNameOverrides.freeze(appContext, taskKey) ?: media.downloadName),
                    if (media.isAudio) DownloadContentType.AUDIO else DownloadContentType.VIDEO,
                    request.sourceUrl, preserveOriginalMetadata = preserve,
                    index = request.itemIndex, itemCount = request.itemCount)
            } finally {
                directory.deleteRecursively()
            }
        }
        onPhase(DownloadPhase.FINALIZING)
        DownloadExecutionResult(
            uri = uri,
            title = media.title ?: media.username,
            mimeType = media.mimeType ?: when (media.kind) {
                MediaKind.IMAGE -> "image/*"
                MediaKind.AUDIO -> "audio/*"
                MediaKind.VIDEO -> "video/*"
            },
            mediaKind = media.kind
        )
    }

    private suspend fun executeProcessed(
        request: DownloadWorkRequest,
        taskKey: String,
        onProgress: (YtDlpProgress) -> Unit,
        onPhase: (DownloadPhase) -> Unit
    ): DownloadExecutionResult {
        val engine = engineFactory()
        var completedFile: File? = null
        try {
            completedFile = try {
                runEngine(engine, request.copy(media = request.media.copy(requestCookies = null,
                    explicitBrowserSessionAuthorized = false)), taskKey, onProgress)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (publicFailure: Exception) {
                if (!settings.useBrowserSessions() ||
                    publicFailure.toYtDlpFailure() != YtDlpFailure.UNKNOWN
                ) throw publicFailure
                runEngine(engine, request.copy(media = request.media.copy(explicitBrowserSessionAuthorized = true)), taskKey, onProgress)
            }
            onPhase(DownloadPhase.PROCESSING)
            val uri = withContext(Dispatchers.IO) {
                storage.saveProcessedFile(
                    completedFile,
                    request.outputMedia.copy(downloadName = DownloadNameOverrides.freeze(appContext, taskKey) ?: request.outputMedia.downloadName),
                    request.options.contentType,
                    request.sourceUrl,
                    preserveOriginalMetadata = request.options.preserveOriginalMetadata
                )
            }
            onPhase(DownloadPhase.FINALIZING)
            val audio = request.options.contentType == DownloadContentType.AUDIO
            return DownloadExecutionResult(
                uri = uri,
                title = request.outputMedia.title ?: request.media.title ?: request.outputMedia.username,
                mimeType = if (audio) "audio/*" else "video/*",
                mediaKind = if (audio) MediaKind.AUDIO else MediaKind.VIDEO
            )
        } finally {
            completedFile?.let(engine::cleanup)
        }
    }

    private suspend fun runEngine(
        engine: YtDlpEngine,
        request: DownloadWorkRequest,
        taskKey: String,
        onProgress: (YtDlpProgress) -> Unit
    ): File = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { engine.cancelAll() }
        thread(name = "acqua-download-$taskKey") {
            try {
                val file = engine.download(request.media, request.options, onProgress, taskKey)
                if (continuation.isActive) {
                    continuation.resume(file) { _, abandonedFile, _ -> engine.cleanup(abandonedFile) }
                } else {
                    engine.cleanup(file)
                }
            } catch (error: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    }
}
