package dev.qtremors.acqua.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import dev.qtremors.acqua.R
import java.util.UUID

/** Owns foreground-service notification policy for a single background download. */
internal class DownloadNotificationController(
    context: Context,
    private val workId: UUID
) {
    private val applicationContext = context.applicationContext
    private val notificationManager =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notificationId = workId.hashCode() and Int.MAX_VALUE

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    fun foregroundInfo(
        title: String?,
        progress: Float,
        etaSeconds: Long,
        downloadedBytes: Long,
        totalBytes: Long
    ): ForegroundInfo {
        val notification = buildNotification(title, progress, etaSeconds, downloadedBytes, totalBytes)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    fun update(
        title: String?,
        progress: Float,
        etaSeconds: Long,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        runCatching {
            notificationManager.notify(
                notificationId,
                buildNotification(title, progress, etaSeconds, downloadedBytes, totalBytes)
            )
        }
    }

    private fun buildNotification(
        title: String?,
        progress: Float,
        etaSeconds: Long,
        downloadedBytes: Long,
        totalBytes: Long
    ): Notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
        .setSmallIcon(R.drawable.acqua_monochrome)
        .setContentTitle(title ?: applicationContext.getString(R.string.download_notification_title))
        .setContentText(statusText(etaSeconds, downloadedBytes, totalBytes))
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setProgress(100, progress.coerceIn(0f, 100f).toInt(), progress <= 0f)
        .addAction(
            0,
            applicationContext.getString(R.string.cancel_download),
            WorkManager.getInstance(applicationContext).createCancelPendingIntent(workId)
        )
        .build()

    private fun statusText(etaSeconds: Long, downloadedBytes: Long, totalBytes: Long): String {
        val size = when {
            downloadedBytes > 0L && totalBytes > 0L -> applicationContext.getString(
                R.string.download_size_progress,
                Formatter.formatShortFileSize(applicationContext, downloadedBytes),
                Formatter.formatShortFileSize(applicationContext, totalBytes)
            )
            downloadedBytes > 0L -> applicationContext.getString(
                R.string.download_size_downloaded,
                Formatter.formatShortFileSize(applicationContext, downloadedBytes)
            )
            else -> null
        }
        val eta = if (etaSeconds > 0L) applicationContext.resources.getQuantityString(
            R.plurals.download_eta,
            etaSeconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            etaSeconds
        ) else null
        return listOfNotNull(size, eta).joinToString(" · ").ifBlank {
            applicationContext.getString(R.string.download_notification_preparing)
        }
    }

    private companion object {
        const val CHANNEL_ID = "acqua_media_downloads"
    }
}
