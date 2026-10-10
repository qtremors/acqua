package dev.qtremors.acqua.downloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadNameOverridesTest {
    @Test
    fun `active names survive persistence and finalization closes renaming`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DownloadNameOverrides.register(context, "active-transfer")
        assertTrue(DownloadNameOverrides.rename(context, "active-transfer", "  My song  "))
        assertFalse(DownloadNameOverrides.rename(context, "active-transfer", " "))
        assertEquals("My song", context.getSharedPreferences("acqua_pending_download_names", Context.MODE_PRIVATE)
            .getString("active-transfer", null))
        assertEquals("My song", DownloadNameOverrides.freeze(context, "active-transfer"))
        assertFalse(DownloadNameOverrides.rename(context, "active-transfer", "Too late"))
        DownloadNameOverrides.reopen(context, "active-transfer")
        assertEquals("My song", DownloadNameOverrides.freeze(context, "active-transfer"))
        assertEquals("My song", DownloadNameOverrides.finish(context, "active-transfer"))
        assertFalse(DownloadNameOverrides.rename(context, "active-transfer", "Too late"))
    }
}
