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

    override suspend fun execute(
        request: DownloadWorkRequest,
        taskKey: String,
        onProgress: (YtDlpProgress) -> Unit,
        onPhase: (DownloadPhase) -> Unit
    ): DownloadExecutionResult {
        onPhase(DownloadPhase.TRANSFER)
        return if (request.media.backend == dev.qtremors.acqua.domain.MediaBackend.DIRECT) {
            executeDirect(request, onProgress, onPhase)
        } else {
            executeProcessed(request, taskKey, onProgress, onPhase)
        }
    }

    private suspend fun executeDirect(
        request: DownloadWorkRequest,
        onProgress: (YtDlpProgress) -> Unit,
        onPhase: (DownloadPhase) -> Unit
    ): DownloadExecutionResult = withContext(Dispatchers.IO) {
        val cookiesAllowed = request.media.explicitBrowserSessionAuthorized || settings.useBrowserSessions()
        val requestCookies = if (cookiesAllowed) {
            runCatching { CookieManager.getInstance().getCookie(request.media.url) }
                .getOrNull()
                ?.takeIf(String::isNotBlank)
        } else {
            null
        }
        val media = request.media.copy(requestCookies = requestCookies)
        val uri = storage.save(
            media,
            request.itemIndex,
            request.sourceUrl,
            itemCount = request.itemCount
        ) { downloaded, total ->
            val percent = if (total > 0L) downloaded * 100f / total else 0f
            onProgress(
                YtDlpProgress(
                    percent = percent,
                    downloadedBytes = downloaded,
                    totalBytes = total
                )
            )
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
            completedFile = runEngine(engine, request, taskKey, onProgress)
            onPhase(DownloadPhase.PROCESSING)
            val uri = withContext(Dispatchers.IO) {
                storage.saveProcessedFile(
                    completedFile,
                    request.outputMedia,
                    request.options.contentType,
                    request.sourceUrl
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
