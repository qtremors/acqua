package dev.qtremors.acqua.downloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.data.history.*
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CaptionHistoryTest {
    @Test fun `saved captions remain in downloads without appearing as photos`() {
        val history = HistoryRepository(ApplicationProvider.getApplicationContext<Context>())
        val id = UUID.randomUUID().toString()
        val caption = HistoryEntry(id, System.currentTimeMillis(), "https://example.com/video", "video.en.srt",
            "content://downloads/$id", false, "application/x-subrip", 100L, true)
        history.add(caption)
        assertTrue(caption.isCaption)
        assertTrue(history.loadPage(HistoryPageRequest()).entries.any { it.id == id })
        assertFalse(history.loadPage(HistoryPageRequest(category = HistoryCategory.PHOTOS)).entries.any { it.id == id })
    }
}
