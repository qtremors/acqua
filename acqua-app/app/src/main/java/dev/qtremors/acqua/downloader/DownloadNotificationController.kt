package dev.qtremors.acqua.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import dev.qtremors.acqua.R
import java.util.UUID

/** Owns foreground-service and completion notification policy for a single background download. */
internal class DownloadNotificationController(
    context: Context,
    private val workId: UUID
) {
    private val applicationContext = context.applicationContext
    private val notificationManager =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notificationId = workId.hashCode() and Int.MAX_VALUE
    private val completedNotificationId = ((workId.hashCode() and Int.MAX_VALUE) + 1).coerceAtLeast(1)

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val progressChannel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.download_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val completedChannel = NotificationChannel(
            COMPLETED_CHANNEL_ID,
            applicationContext.getString(R.string.download_completed_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(progressChannel)
        notificationManager.createNotificationChannel(completedChannel)
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

    fun complete(
        title: String?,
        uri: Uri? = null,
        mimeType: String? = null
    ) {
        runCatching {
            notificationManager.cancel(notificationId)

            val contentIntent = uri?.let { targetUri ->
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(targetUri, mimeType ?: "video/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                PendingIntent.getActivity(
                    applicationContext,
                    completedNotificationId,
                    viewIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } ?: run {
                val launchIntent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                launchIntent?.let {
                    PendingIntent.getActivity(
                        applicationContext,
                        completedNotificationId,
                        it,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                }
            }

            val displayTitle = title?.takeIf(String::isNotBlank)
                ?: applicationContext.getString(R.string.download_completed_title)

            val notificationBuilder = NotificationCompat.Builder(applicationContext, COMPLETED_CHANNEL_ID)
                .setSmallIcon(R.drawable.acqua_monochrome)
                .setContentTitle(displayTitle)
                .setContentText(applicationContext.getString(R.string.saved_to_downloads))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(false)
                .setAutoCancel(true)

            if (contentIntent != null) {
                notificationBuilder.setContentIntent(contentIntent)
            }

            notificationManager.notify(completedNotificationId, notificationBuilder.build())
        }
    }

    fun cancel() {
        runCatching {
            notificationManager.cancel(notificationId)
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
        const val COMPLETED_CHANNEL_ID = "acqua_completed_downloads"
    }
}
