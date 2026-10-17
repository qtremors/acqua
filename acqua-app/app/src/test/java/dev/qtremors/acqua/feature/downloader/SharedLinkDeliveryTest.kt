package dev.qtremors.acqua.feature.downloader

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.MainActivity
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedLinkDeliveryTest {
    @Test fun `warm shared batches deliver unique HTTP links and ignore untrusted browser authorization`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppSettingsRepository(context).setAutoUpdateYtDlp(false)
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            val shared = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "https://example.com/one\nfile:///private/file\nhttps://example.com/two\nhttps://example.com/one")
                .putExtra(DownloadActivity.EXTRA_MEDIA_JSON, "untrusted")
                .putExtra("automatic_download", true)
            controller.newIntent(shared)
            val coordinator = ViewModelProvider(controller.get())[BrowserResolutionCoordinator::class.java]
            assertEquals("https://example.com/one\nhttps://example.com/two", coordinator.urlHandoff)
            assertEquals(1, coordinator.downloadRequestRevision)
            assertNull(coordinator.pending)
            assertNull(shadowOf(controller.get()).nextStartedActivity)
            controller.newIntent(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "file:///private/file"))
            assertEquals(1, coordinator.downloadRequestRevision)
        } finally { controller.destroy() }
    }
}
