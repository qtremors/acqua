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
