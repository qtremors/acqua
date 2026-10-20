package dev.qtremors.acqua.downloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLResponse
import dev.qtremors.acqua.domain.MediaResolutionException
import dev.qtremors.acqua.domain.MediaResolutionFailure
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class YtDlpInspectionTest {
    private fun engine() = YtDlpEngine(ApplicationProvider.getApplicationContext<Context>())

    @Test fun `flat playlist entries retain thumbnails duration positions and video urls`() {
        val json = """{"title":"Walks","entries":[
            {"_type":"url","ie_key":"Youtube","id":"abcdefghijk","title":"First","duration":245,
             "thumbnails":[{"url":"https://img.example/low.jpg","width":120,"height":90},
                           {"url":"https://img.example/high.jpg","width":480,"height":360}]},
            null,
            {"_type":"url","ie_key":"Youtube","id":"123456789ab","title":"Third","availability":"private"}
        ]}"""
        val items = engine().parseInspection(json, "https://www.youtube.com/playlist?list=PL123", false)
        assertEquals(3, items.size)
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk", items[0].url)
        assertEquals("https://img.example/high.jpg", items[0].thumbnailUrl)
        assertEquals(245, items[0].durationSeconds)
        assertEquals(3, items[2].collectionIndex)
        assertEquals("private", items[2].unavailableReason)
        assertEquals("https://i.ytimg.com/vi/123456789ab/hqdefault.jpg", items[2].thumbnailUrl)
        assertEquals("Unavailable", items[1].unavailableReason)
    }

    @Test
    fun `thrown login errors become session required with their original cause`() {
        val original = YoutubeDLException("WARNING: Retrying\nERROR: Sign in with cookies to continue")
        val failure = assertThrows(MediaResolutionException::class.java) {
            engine().executeInspection { throw original }
        }
        assertEquals(MediaResolutionFailure.SESSION_REQUIRED, failure.failure)
        assertEquals("Sign in with cookies to continue", failure.message)
        assertSame(original, failure.cause)
    }

    @Test
    fun `other thrown extraction errors remain unknown`() {
        val original = YoutubeDLException("ERROR: Unsupported URL")
        val failure = assertThrows(MediaResolutionException::class.java) {
            engine().executeInspection { throw original }
        }
        assertEquals(MediaResolutionFailure.UNKNOWN, failure.failure)
        assertSame(original, failure.cause)
    }

    @Test
    fun `inspection preserves cancellation without classifying it as an extraction error`() {
        val cancelled = CancellationException("Cancelled")
        val failure = assertThrows(CancellationException::class.java) {
            engine().executeInspection { throw cancelled }
        }
        assertSame(cancelled, failure)
    }

    @Test
    fun `returned errors use the same classification and successful responses pass through`() {
        val engine = engine()
        val failure = assertThrows(MediaResolutionException::class.java) {
            engine.executeInspection { YoutubeDLResponse(emptyList(), 1, 0L, "", "ERROR: Login required") }
        }
        assertEquals(MediaResolutionFailure.SESSION_REQUIRED, failure.failure)
        val response = YoutubeDLResponse(emptyList(), 0, 0L, "{}", "")
        assertSame(response, engine.executeInspection { response })
    }
}
