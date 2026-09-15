package dev.qtremors.acqua.downloader

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadNotificationControllerTest {
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
