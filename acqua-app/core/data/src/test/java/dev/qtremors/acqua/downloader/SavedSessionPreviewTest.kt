package dev.qtremors.acqua.downloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.data.network.HttpMediaClient
import dev.qtremors.acqua.data.network.MediaPreviewCache
import dev.qtremors.acqua.data.session.CookieProfile
import dev.qtremors.acqua.data.session.SessionCookie
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import java.util.UUID
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SavedSessionPreviewTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val image = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+cK4YAAAAASUVORK5CYII=")

    @Test fun previewUsesTheSelectedSavedSession() {
        okhttp3.OkHttp.initialize(context)
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(image)))
            val url = server.url("/private/cover.png").toString()
            val profile = CookieProfile(UUID.randomUUID().toString(), "Chosen account", url, "saved-agent", System.currentTimeMillis(), listOf(SessionCookie("sid", "chosen-account")))
            val media = ResolvedMedia(url, MediaKind.IMAGE, authorizedSession = profile.json(),
                requestUserAgent = profile.userAgent, explicitBrowserSessionAuthorized = true)
            assertNotNull(MediaPreviewCache(context, HttpMediaClient()).loadBitmap(media, 16, 16))
            assertEquals("The named profile must also authenticate its preview", "sid=chosen-account", server.takeRequest().getHeader("Cookie"))
        }
    }

    @Test fun previewsAreCachedSeparatelyForEachSavedSessionAndPublicAccess() {
        okhttp3.OkHttp.initialize(context)
        MockWebServer().use { server ->
            repeat(3) { server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(image))) }
            val url = server.url("/private/cover.png").toString()
            val cache = MediaPreviewCache(context, HttpMediaClient())
            val first = CookieProfile(UUID.randomUUID().toString(), "First", url, "saved-agent", System.currentTimeMillis(), listOf(SessionCookie("sid", "first")))
            val second = first.copy(id = UUID.randomUUID().toString(), name = "Second", cookies = listOf(SessionCookie("sid", "second")))
            val public = ResolvedMedia(url, MediaKind.IMAGE)
            for (profile in listOf(first, second)) {
                val media = public.copy(authorizedSession = profile.json(), requestUserAgent = profile.userAgent,
                    explicitBrowserSessionAuthorized = true)
                assertNotNull(cache.loadBitmap(media, 16, 16))
                assertNotNull(cache.loadBitmap(media, 16, 16))
            }
            assertNotNull(cache.loadBitmap(public, 16, 16))
            assertEquals(3, server.requestCount)
            assertEquals("sid=first", server.takeRequest().getHeader("Cookie"))
            assertEquals("sid=second", server.takeRequest().getHeader("Cookie"))
            assertNull(server.takeRequest().getHeader("Cookie"))
        }
    }
}
