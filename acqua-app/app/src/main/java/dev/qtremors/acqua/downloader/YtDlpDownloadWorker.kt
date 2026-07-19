package dev.qtremors.acqua.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.qtremors.acqua.R
import dev.qtremors.acqua.data.history.HistoryRepository
import dev.qtremors.acqua.data.network.MediaDownloader
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
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
    override suspend fun doWork(): Result {
        val request = inputData.toRequest()
            ?: return Result.failure(errorData("The background download request is incomplete."))
        createNotificationChannel()
        setForeground(foregroundInfo(request.media.title, 0f, 0L))

        val activeEngine = YtDlpEngine(applicationContext)
        var processedFile: File? = null
        return try {
            val completedFile = runDownload(activeEngine, request) { progress ->
                    val percent = progress.percent.coerceIn(0f, 100f)
                    setProgressAsync(
                        Data.Builder()
                            .putFloat(KEY_PROGRESS, percent)
                            .putLong(KEY_ETA_SECONDS, progress.etaSeconds.coerceAtLeast(0L))
                            .build()
                    )
                    updateNotification(request.media.title, percent, progress.etaSeconds)
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
            Result.failure(errorData(error.message ?: "The background download failed."))
        } finally {
            processedFile?.let(activeEngine::cleanup)
        }
    }

    private suspend fun runDownload(
        activeEngine: YtDlpEngine,
        request: WorkerRequest,
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

    private fun updateNotification(title: String?, progress: Float, etaSeconds: Long) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching {
            manager.notify(notificationId, notification(title, progress, etaSeconds))
        }
    }

    private fun foregroundInfo(title: String?, progress: Float, etaSeconds: Long): ForegroundInfo {
        val notification = notification(title, progress, etaSeconds)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun notification(title: String?, progress: Float, etaSeconds: Long) =
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_notification)
            .setContentTitle(title ?: applicationContext.getString(R.string.download_notification_title))
            .setContentText(
                if (etaSeconds > 0L) {
                    applicationContext.resources.getQuantityString(
                        R.plurals.download_eta,
                        etaSeconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        etaSeconds
                    )
                } else {
                    applicationContext.getString(R.string.download_notification_preparing)
                }
            )
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress.toInt(), progress <= 0f)
            .addAction(
                0,
                applicationContext.getString(R.string.cancel_download),
                WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
            )
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun Data.toRequest(): WorkerRequest? {
        val mediaUrl = getString(KEY_MEDIA_URL) ?: return null
        val sourceUrl = getString(KEY_SOURCE_URL) ?: return null
        val contentType = getString(KEY_CONTENT_TYPE)?.let {
            runCatching { DownloadContentType.valueOf(it) }.getOrNull()
        } ?: return null
        val audioFormat = getString(KEY_AUDIO_FORMAT)?.let {
            runCatching { AudioOutputFormat.valueOf(it) }.getOrNull()
        } ?: return null
        val media = ResolvedMedia(
            url = mediaUrl,
            kind = MediaKind.VIDEO,
            thumbnailUrl = getString(KEY_THUMBNAIL_URL),
            width = getInt(KEY_MEDIA_WIDTH, 0),
            height = getInt(KEY_MEDIA_HEIGHT, 0),
            username = getString(KEY_USERNAME),
            explicitBrowserSessionAuthorized = getBoolean(KEY_BROWSER_SESSION_AUTHORIZED, false),
            backend = MediaBackend.YT_DLP,
            title = getString(KEY_TITLE)
        )
        return WorkerRequest(
            media = media,
            outputMedia = media.copy(
                width = getInt(KEY_OUTPUT_WIDTH, media.width),
                height = getInt(KEY_OUTPUT_HEIGHT, media.height)
            ),
            sourceUrl = sourceUrl,
            options = YtDlpDownloadOptions(
                contentType = contentType,
                maximumVideoHeight = getInt(KEY_MAXIMUM_VIDEO_QUALITY, 0),
                audioFormat = audioFormat,
                embedMetadata = getBoolean(KEY_EMBED_METADATA, true),
                embedThumbnail = getBoolean(KEY_EMBED_THUMBNAIL, true)
            )
        )
    }

    private data class WorkerRequest(
        val media: ResolvedMedia,
        val outputMedia: ResolvedMedia,
        val sourceUrl: String,
        val options: YtDlpDownloadOptions
    )

    companion object {
        const val KEY_MEDIA_URL = "media_url"
        const val KEY_SOURCE_URL = "source_url"
        const val KEY_CONTENT_TYPE = "content_type"
        const val KEY_AUDIO_FORMAT = "audio_format"
        const val KEY_MAXIMUM_VIDEO_QUALITY = "maximum_video_quality"
        const val KEY_EMBED_METADATA = "embed_metadata"
        const val KEY_EMBED_THUMBNAIL = "embed_thumbnail"
        const val KEY_BROWSER_SESSION_AUTHORIZED = "browser_session_authorized"
        const val KEY_MEDIA_WIDTH = "media_width"
        const val KEY_MEDIA_HEIGHT = "media_height"
        const val KEY_OUTPUT_WIDTH = "output_width"
        const val KEY_OUTPUT_HEIGHT = "output_height"
        const val KEY_THUMBNAIL_URL = "thumbnail_url"
        const val KEY_USERNAME = "username"
        const val KEY_TITLE = "title"
        const val KEY_PROGRESS = "progress"
        const val KEY_ETA_SECONDS = "eta_seconds"
        const val KEY_ERROR = "error"

        private const val CHANNEL_ID = "acqua_media_downloads"

        private fun errorData(message: String): Data =
            Data.Builder().putString(KEY_ERROR, message).build()
    }

    private val notificationId: Int
        get() = id.hashCode() and Int.MAX_VALUE
}
