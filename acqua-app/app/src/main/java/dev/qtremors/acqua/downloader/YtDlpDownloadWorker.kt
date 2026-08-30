package dev.qtremors.acqua.downloader

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.qtremors.acqua.data.history.HistoryRepository
import dev.qtremors.acqua.data.network.MediaDownloader
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.platform.storage.MediaStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class YtDlpDownloadWorker(
    appContext: Context,
    parameters: WorkerParameters
) : CoroutineWorker(appContext, parameters) {
    private val notifications = DownloadNotificationController(appContext, id)

    override suspend fun doWork(): Result {
        val request = DownloadWorkData.decode(inputData)
            ?: return Result.failure(
                DownloadWorkData.error("The background download request is incomplete.")
            )
        notifications.ensureChannel()
        setForeground(notifications.foregroundInfo(request.media.title, 0f, 0L, 0L, 0L))

        val activeEngine = YtDlpEngine(applicationContext)
        var processedFile: File? = null
        return try {
            if (request.media.backend == MediaBackend.DIRECT) {
                val savedUri = runDirectDownload(request)
                notifications.complete(
                    title = request.media.title ?: request.media.username,
                    uri = savedUri,
                    mimeType = request.media.mimeType
                )
                return Result.success()
            }
            var downloadedBytes = 0L
            var totalBytes = 0L
            val completedFile = runDownload(activeEngine, request) { progress ->
                    val percent = progress.percent.coerceIn(0f, 100f)
                    if (progress.totalBytes > 0L) {
                        downloadedBytes = progress.downloadedBytes
                        totalBytes = progress.totalBytes
                    }
                    setProgressAsync(
                        DownloadWorkData.progress(
                            percent, progress.etaSeconds, downloadedBytes, totalBytes
                        )
                    )
                    notifications.update(
                        request.media.title,
                        percent,
                        progress.etaSeconds,
                        downloadedBytes,
                        totalBytes
                    )
            }
            processedFile = completedFile
            val savedUri = withContext(Dispatchers.IO) {
                val settings = AppSettingsRepository(applicationContext)
                val history = HistoryRepository(applicationContext)
                val downloader = MediaDownloader()
                MediaStorage(applicationContext, history, settings, downloader).saveProcessedFile(
                    completedFile,
                    request.outputMedia,
                    request.options.contentType,
                    request.sourceUrl
                )
            }
            notifications.complete(
                title = request.outputMedia.title ?: request.media.title ?: request.outputMedia.username,
                uri = savedUri,
                mimeType = if (request.options.contentType == DownloadContentType.AUDIO) "audio/*" else "video/*"
            )
            Result.success()
        } catch (cancelled: CancellationException) {
            notifications.cancel()
            throw cancelled
        } catch (error: Throwable) {
            notifications.cancel()
            if (error is VirtualMachineError || error is ThreadDeath) throw error
            if (error.hasNetworkCause() && runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                val failure = error.toYtDlpFailure()
                Log.e(TAG, "Background download failed (${failure.name})", error)
                Result.failure(
                    DownloadWorkData.error(failure.userMessage(error))
                )
            }
        } finally {
            processedFile?.let(activeEngine::cleanup)
        }
    }

    private suspend fun runDirectDownload(request: DownloadWorkRequest): Uri = withContext(Dispatchers.IO) {
        val settings = AppSettingsRepository(applicationContext)
        val cookiesAllowed = request.media.explicitBrowserSessionAuthorized || settings.useBrowserSessions()
        val requestCookies = if (cookiesAllowed) {
            runCatching { CookieManager.getInstance().getCookie(request.media.url) }
                .getOrNull()?.takeIf(String::isNotBlank)
        } else {
            null
        }
        val media = request.media.copy(requestCookies = requestCookies)
        val history = HistoryRepository(applicationContext)
        val downloader = MediaDownloader()
        MediaStorage(applicationContext, history, settings, downloader).save(
            media,
            request.itemIndex,
            request.sourceUrl
        ) { downloaded, total ->
            val percent = if (total > 0L) downloaded * 100f / total else 0f
            setProgressAsync(DownloadWorkData.progress(percent, 0L, downloaded, total))
            notifications.update(media.title ?: media.username, percent, 0L, downloaded, total)
        }
    }

    private suspend fun runDownload(
        activeEngine: YtDlpEngine,
        request: DownloadWorkRequest,
        onProgress: (YtDlpProgress) -> Unit
    ): File = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { activeEngine.cancelAll() }
        thread(name = "acqua-yt-dlp-${id}") {
            try {
                val file = activeEngine.download(
                    request.media,
                    request.options,
                    onProgress,
                    taskKey = id.toString()
                )
                if (continuation.isActive) {
                    continuation.resume(file) { _, completedFile, _ ->
                        activeEngine.cleanup(completedFile)
                    }
                } else {
                    activeEngine.cleanup(file)
                }
            } catch (error: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    }

    companion object {
        private const val TAG = "YtDlpDownloadWorker"
        private const val MAX_RETRIES = 2
    }
}

private fun YtDlpFailure.userMessage(error: Throwable): String = when (this) {
    YtDlpFailure.RUNTIME -> "The download engine could not start. Restart Acqua or reinstall this APK."
    YtDlpFailure.NETWORK -> "The connection was interrupted. Check your connection and try again."
    YtDlpFailure.STORAGE -> "Acqua could not save the download. Check storage space and permissions."
    YtDlpFailure.UPDATE_SERVICE -> "The download engine update service is temporarily unavailable."
    YtDlpFailure.UNKNOWN -> error.message?.takeIf(String::isNotBlank)
        ?: "The background download failed unexpectedly."
}
