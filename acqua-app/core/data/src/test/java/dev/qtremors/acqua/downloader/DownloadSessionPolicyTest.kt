package dev.qtremors.acqua.downloader

import android.content.Context
import android.webkit.CookieManager
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.data.history.HistoryRepository
import dev.qtremors.acqua.data.network.HttpMediaClient
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadSessionPolicyTest {
    @Test
    fun `direct transfers try public access before using captured cookies`() {
        checkDirectTransfer(enabled = true, expectedAttempts = 2)
    }

    @Test
    fun `disabled sessions block captured cookies and transfer retry`() {
        checkDirectTransfer(enabled = false, expectedAttempts = 1)
    }

    @Test
    fun `turning sessions off before a failed public response prevents retry`() {
        checkDirectTransfer(enabled = true, expectedAttempts = 1, disableDuringRequest = true)
    }

    @Test
    fun `yt-dlp cookie export respects the live setting and request authorization`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = AppSettingsRepository(context)
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setCookie("https://www.youtube.com", "sessionid=saved-session; Path=/; Secure")
        val engine = YtDlpEngine(context)
        val create = YtDlpEngine::class.java.getDeclaredMethod("createCookieFile", String::class.java, Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }

        settings.setUseBrowserSessions(false)
        assertNull(create.invoke(engine, "https://youtu.be/private", true))
        settings.setUseBrowserSessions(true)
        assertNull(create.invoke(engine, "https://youtu.be/private", false))
        val file = create.invoke(engine, "https://youtu.be/private", true) as File
        try {
            assertTrue(file.readText().contains("sessionid\tsaved-session"))
            settings.setUseBrowserSessions(false)
            assertNull(create.invoke(engine, "https://youtu.be/private", true))
        } finally {
            file.delete()
            cookies.removeAllCookies(null)
        }
    }

    private fun checkDirectTransfer(enabled: Boolean, expectedAttempts: Int, disableDuringRequest: Boolean = false) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = AppSettingsRepository(context).apply { setUseBrowserSessions(enabled) }
        MockWebServer().use { server ->
            if (disableDuringRequest) {
                server.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        settings.setUseBrowserSessions(false)
                        return MockResponse().setResponseCode(401)
                    }
                }
            } else {
                repeat(expectedAttempts) { server.enqueue(MockResponse().setResponseCode(401)) }
            }
            val media = ResolvedMedia(server.url("private.jpg").toString(), MediaKind.IMAGE,
                requestCookies = "sessionid=captured-session", explicitBrowserSessionAuthorized = true)
            val request = DownloadWorkRequest(media = media, outputMedia = media, sourceUrl = "https://example.com/post",
                itemIndex = 0, options = YtDlpDownloadOptions())
            val executor = DownloadExecutor(context, settings, HistoryRepository(context), HttpMediaClient())

            val failure = assertThrows(IllegalStateException::class.java) {
                runBlocking { executor.execute(request, UUID.randomUUID().toString(), {}, {}) }
            }

            assertTrue(failure.message.orEmpty().contains("HTTP 401"))
            assertEquals(expectedAttempts, server.requestCount)
            assertNull(server.takeRequest().getHeader("Cookie"))
            if (expectedAttempts == 2) assertEquals("sessionid=captured-session", server.takeRequest().getHeader("Cookie"))
        }
    }
}
