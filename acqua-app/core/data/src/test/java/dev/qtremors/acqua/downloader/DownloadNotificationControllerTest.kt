package dev.qtremors.acqua.downloader

import android.content.Context
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadNotificationControllerTest {
    @Test
    fun `failure notifications open their own report without including media or credentials`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        shadowOf(context.packageManager).addResolveInfoForIntent(launch, ResolveInfo().apply {
            activityInfo = ActivityInfo().apply { packageName = context.packageName; name = "MainActivity" }
        })
        val ids = List(2) { UUID.randomUUID() }
        ids.forEach { id ->
            DownloadNotificationController(context, id).apply { ensureChannel(); failed(DownloadFailure.NETWORK) }
        }
        val notifications = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
        assertEquals(2, notifications.size)
        val routed = notifications.map { notification ->
            notification.contentIntent.send()
            val opened = shadowOf(context).nextStartedActivity
            assertEquals(setOf(DownloadDiagnosticsStore.EXTRA_TASK_ID), opened.extras!!.keySet())
            opened.getStringExtra(DownloadDiagnosticsStore.EXTRA_TASK_ID)
        }
        assertEquals(ids.map(UUID::toString).toSet(), routed.toSet())
    }

    @Test
    fun `Android 15 foreground type follows the active workload phase`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notifications = DownloadNotificationController(context, UUID.randomUUID())

        val transfer = notifications.foregroundInfo(null, 10f, 0L, 10L, 100L)
        val processing = notifications.foregroundInfo(
            null,
            80f,
            0L,
            80L,
            100L,
            DownloadPhase.PROCESSING
        )

        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, transfer.foregroundServiceType)
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,
            processing.foregroundServiceType
        )
    }
}
