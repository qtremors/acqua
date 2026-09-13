package dev.qtremors.acqua.downloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UserInitiatedDownloadRegistryTest {
    @Test
    fun `durable checkpoint restores retry state phase and progress`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = UUID.randomUUID()
        val checkpoint = DownloadQueueItem(
            id = id.toString(),
            state = DownloadQueueState.RETRYING,
            progress = 47.5f,
            etaSeconds = 21L,
            downloadedBytes = 475L,
            totalBytes = 1_000L,
            error = DownloadFailure.PLATFORM_LIMIT.code,
            phase = DownloadPhase.PROCESSING
        )

        try {
            UserInitiatedDownloadRegistry.update(context, checkpoint)
            UserInitiatedDownloadRegistry.reload(context)

            assertEquals(checkpoint, UserInitiatedDownloadRegistry.observe(context).value[id])
        } finally {
            UserInitiatedDownloadRegistry.remove(context, id)
        }
    }

    @Test
    fun `missing optional failure stays null after reload`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = UUID.randomUUID()
        val checkpoint = DownloadQueueItem(
            id = id.toString(),
            state = DownloadQueueState.RUNNING,
            phase = DownloadPhase.TRANSFER
        )

        try {
            UserInitiatedDownloadRegistry.update(context, checkpoint)
            UserInitiatedDownloadRegistry.reload(context)

            assertNull(UserInitiatedDownloadRegistry.observe(context).value[id]?.error)
        } finally {
            UserInitiatedDownloadRegistry.remove(context, id)
        }
    }
}
