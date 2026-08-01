package dev.qtremors.acqua.downloader

import android.content.Context
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
import java.io.IOException
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
        setForeground(notifications.foregroundInfo(request.media.title, 0f, 0L))

        val activeEngine = YtDlpEngine(applicationContext)
        var processedFile: File? = null
        return try {
            if (request.media.backend == MediaBackend.DIRECT) {
                runDirectDownload(request)
                return Result.success()
            }
            val completedFile = runDownload(activeEngine, request) { progress ->
                    val percent = progress.percent.coerceIn(0f, 100f)
                    setProgressAsync(DownloadWorkData.progress(percent, progress.etaSeconds))
                    notifications.update(request.media.title, percent, progress.etaSeconds)
            }
            processedFile = completedFile
            withContext(Dispatchers.IO) {
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
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (error is IOException && runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.failure(DownloadWorkData.error(error.message))
            }
        } finally {
            processedFile?.let(activeEngine::cleanup)
        }
    }

    private suspend fun runDirectDownload(request: DownloadWorkRequest) = withContext(Dispatchers.IO) {
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
            setProgressAsync(DownloadWorkData.progress(percent))
            notifications.update(media.title ?: media.username, percent, 0L)
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
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    }

    companion object {
        private const val MAX_RETRIES = 2
    }
}
