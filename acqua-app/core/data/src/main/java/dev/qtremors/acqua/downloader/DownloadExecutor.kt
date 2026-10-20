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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class DownloadExecutionResult(
    val uri: Uri,
    val title: String?,
    val mimeType: String,
    val mediaKind: MediaKind,
    val artifactUris: List<Uri> = listOf(uri),
    val skipped: Boolean = false
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
    private val history: HistoryRepository,
    mediaDownloader: HttpMediaClient,
    private val profiles: dev.qtremors.acqua.data.session.CookieProfileStore = dev.qtremors.acqua.data.session.CookieProfileStore(context),
    private val engineFactory: () -> DownloadProcessingEngine = { YtDlpEngine(context.applicationContext) }
) : DownloadExecution {
    private val appContext = context.applicationContext
    private fun storageFor(request: DownloadWorkRequest) = MediaStorage(appContext,
        if (request.options.recordHistory) history else dev.qtremors.acqua.data.history.HistoryWriter { 0L }, settings,
        httpMedia.withNetworkOptions(request.options.network).withSession(request.media.authorizedSession))
    private val httpMedia = mediaDownloader
    private val requests = DownloadRequestStore(appContext)
    private val library = DownloadLibraryStore(appContext)

    override suspend fun execute(
        request: DownloadWorkRequest,
        taskKey: String,
        onProgress: (YtDlpProgress) -> Unit,
        onPhase: (DownloadPhase) -> Unit
    ): DownloadExecutionResult {
        return if (request.options.skipArchived) DownloadDuplicateGate.run(DownloadLibraryStore.identity(request)) {
            executeRequest(request, taskKey, onProgress, onPhase)
        } else executeRequest(request, taskKey, onProgress, onPhase)
    }

    private suspend fun executeRequest(request: DownloadWorkRequest, taskKey: String, onProgress: (YtDlpProgress) -> Unit,
        onPhase: (DownloadPhase) -> Unit): DownloadExecutionResult {
        request.options.cookieProfileId?.let { id ->
            val profile = request.media.authorizedSession?.let(dev.qtremors.acqua.data.session.CookieProfile::parse)
                ?: throw dev.qtremors.acqua.data.session.CookieProfileException()
            if (profile.id != id) throw dev.qtremors.acqua.data.session.CookieProfileException()
            profiles.requireAuthorized(profile, request.sourceUrl)
        }
        if (request.options.skipArchived) library.existing(request)?.let { existing ->
            DownloadNameOverrides.finish(appContext, taskKey)
            return DownloadExecutionResult(Uri.parse(existing.uris.first()), existing.title,
                request.media.mimeType ?: "application/octet-stream", request.media.kind,
                existing.uris.map(Uri::parse), skipped = true)
        }
        onPhase(DownloadPhase.TRANSFER)
        DownloadNameOverrides.reopen(appContext, taskKey)
        val mediaResult = if (request.media.backend == dev.qtremors.acqua.domain.MediaBackend.DIRECT) {
            if (request.options.cookieProfileId != null) executeDirect(request, taskKey, onProgress, onPhase) else
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
        val storage = storageFor(request)
        val result = DownloadThumbnail(appContext, httpMedia.withNetworkOptions(request.options.network).withSession(request.media.authorizedSession)).save(request, mediaResult, taskKey, settings.useBrowserSessions()) { image ->
            storage.saveProcessedFile(image, request.outputMedia,
                if (mediaResult.mediaKind == MediaKind.AUDIO) DownloadContentType.AUDIO else DownloadContentType.VIDEO, request.sourceUrl,
                preserveOriginalMetadata = request.options.preserveOriginalMetadata,
                index = request.itemIndex, itemCount = request.itemCount,
                settings = request.storageSettings ?: settings.downloadSettings(),
                artifactSuffix = if (mediaResult.mediaKind == MediaKind.AUDIO) ".artwork" else ".thumbnail",
                publishedMediaName = storage.queryDisplayName(mediaResult.uri, image.name), thumbnail = true)
        }
        DownloadNameOverrides.finish(appContext, taskKey)
        library.record(request, result)
        return result
    }

    private suspend fun executeDirect(
        request: DownloadWorkRequest,
        taskKey: String,
        onProgress: (YtDlpProgress) -> Unit,
        onPhase: (DownloadPhase) -> Unit
    ): DownloadExecutionResult = withContext(Dispatchers.IO) {
        val storage = storageFor(request)
        val cookiesAllowed = settings.useBrowserSessions() && request.media.explicitBrowserSessionAuthorized
        if (!cookiesAllowed && request.options.cookieProfileId != null) throw dev.qtremors.acqua.data.session.CookieProfileException()
        val requestCookies = if (cookiesAllowed && request.media.authorizedSession == null) {
            request.media.requestCookies ?: runCatching { CookieManager.getInstance().getCookie(request.media.url) }
                .getOrNull()
                ?.takeIf(String::isNotBlank)
        } else {
            null
        }
        val browserUserAgent = request.media.requestUserAgent ?: dev.qtremors.acqua.data.session.BrowserIdentityStore(appContext)
            .userAgent(request.media.referer ?: request.media.url)
        val media = request.media.copy(requestCookies = requestCookies, requestUserAgent = browserUserAgent.takeIf { cookiesAllowed })
        val publicationId = request.publicationId ?: taskKey
        val uri = requests.published(publicationId, "direct") ?: (if (!media.isAudio && !media.isVideo || !request.options.embedMetadata) storage.save(
            media,
            request.itemIndex,
            request.sourceUrl,
            itemCount = request.itemCount,
            settings = request.storageSettings ?: settings.downloadSettings(),
            embedMetadata = request.options.embedMetadata,
            preserveOriginalMetadata = request.options.preserveOriginalMetadata,
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
                    httpMedia.withNetworkOptions(request.options.network).withSession(media.authorizedSession).downloadToStreamCancellable(media, stream, requestCookies) { downloaded, total ->
                        onProgress(YtDlpProgress(percent = if (total > 0L) downloaded * 100f / total else 0f,
                            downloadedBytes = downloaded, totalBytes = total))
                    }
                }
                onPhase(DownloadPhase.PROCESSING)
                val preserve = request.options.preserveOriginalMetadata
                DirectMediaMetadata.write(appContext, source, output, request.sourceUrl,
                    if (preserve) null else System.currentTimeMillis())
                onPhase(DownloadPhase.FINALIZING)
                storage.saveProcessedFile(output,
                    media.copy(downloadName = DownloadNameOverrides.freeze(appContext, taskKey) ?: media.downloadName),
                    if (media.isAudio) DownloadContentType.AUDIO else DownloadContentType.VIDEO,
                    request.sourceUrl, preserveOriginalMetadata = preserve,
                    index = request.itemIndex, itemCount = request.itemCount,
                    settings = request.storageSettings ?: settings.downloadSettings())
            } finally {
                directory.deleteRecursively()
            }
        }).also { requests.recordPublished(publicationId, "direct", it) }
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
        val storage = storageFor(request)
        val engine = engineFactory()
        var completedFile: DownloadOutputSet? = null
        try {
            completedFile = try {
                if (request.options.cookieProfileId != null) runEngine(engine, request, taskKey, onProgress) else
                    runEngine(engine, request.copy(media = request.media.copy(requestCookies = null,
                        explicitBrowserSessionAuthorized = false)), taskKey, onProgress)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (publicFailure: Exception) {
                if (request.options.cookieProfileId != null) throw publicFailure
                if (publicFailure is MediaTimelineException || publicFailure is NoDownloadableChaptersException || publicFailure is SourceStreamsChangedException ||
                    publicFailure is InvalidCommandTemplateException || publicFailure is MissingCommandTemplateException || !settings.useBrowserSessions() ||
                    publicFailure.toYtDlpFailure() != YtDlpFailure.UNKNOWN
                ) throw publicFailure
                runEngine(engine, request.copy(media = request.media.copy(explicitBrowserSessionAuthorized = true)), taskKey, onProgress)
            }
            onPhase(DownloadPhase.PROCESSING)
            val outputs = completedFile
            val publicationId = request.publicationId ?: taskKey
            val uris = withContext(Dispatchers.IO) {
                val name = DownloadNameOverrides.freeze(appContext, taskKey) ?: request.outputMedia.downloadName
                val mediaCount = outputs.artifacts.count { !it.caption }
                var mediaIndex = -1
                var publishedName: String? = null
                outputs.artifacts.map { artifact ->
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    if (!artifact.caption) mediaIndex++
                    val storageSettings = request.storageSettings ?: settings.downloadSettings()
                    val uri = requests.published(publicationId, artifact.checkpointKey) ?: storage.saveProcessedFile(
                        artifact.file, request.outputMedia.copy(downloadName = if (artifact.title != null && name != null) {
                            listOfNotNull(name, artifact.title).joinToString(" - ")
                        } else name), request.options.contentType,
                        request.sourceUrl, preserveOriginalMetadata = request.options.preserveOriginalMetadata,
                        index = if (mediaCount > 1) mediaIndex else request.itemIndex,
                        itemCount = if (mediaCount > 1) mediaCount else request.itemCount,
                        settings = storageSettings,
                        caption = artifact.caption, artifactSuffix = artifact.suffix,
                        publishedMediaName = publishedName.takeIf { artifact.caption }, chapterTitle = artifact.title
                    ).also { requests.recordPublished(publicationId, artifact.checkpointKey, it) }
                    if (!artifact.caption) publishedName = storage.queryDisplayName(uri, artifact.file.name)
                    uri
                }
            }
            onPhase(DownloadPhase.FINALIZING)
            val audio = request.options.contentType == DownloadContentType.AUDIO
            return DownloadExecutionResult(
                uri = uris.first(),
                title = request.outputMedia.title ?: request.media.title ?: request.outputMedia.username,
                mimeType = if (audio) "audio/*" else "video/*",
                mediaKind = if (audio) MediaKind.AUDIO else MediaKind.VIDEO,
                artifactUris = uris
            )
        } finally {
            completedFile?.let(engine::cleanup)
        }
    }

    private suspend fun runEngine(
        engine: DownloadProcessingEngine,
        request: DownloadWorkRequest,
        taskKey: String,
        onProgress: (YtDlpProgress) -> Unit
    ): DownloadOutputSet = suspendCancellableCoroutine { continuation ->
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
