package dev.qtremors.acqua.data.updater

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AssetDownloadPolicyTest {
    @Test fun `only canonical authenticated asset endpoints are trusted`() {
        assertTrue(AssetDownloadPolicy.isTrusted("https://api.github.com/repos/owner/repo/releases/assets/123".toHttpUrl()))
        listOf("http://api.github.com/repos/owner/repo/releases/assets/123",
            "https://api.github.com.evil.example/repos/owner/repo/releases/assets/123",
            "https://user:secret@api.github.com/repos/owner/repo/releases/assets/123",
            "https://api.github.com:444/repos/owner/repo/releases/assets/123",
            "https://api.github.com/repos/owner/repo/releases/latest",
            "https://api.github.com/repos/owner/repo/releases/assets/123?redirect=1",
            "https://api.github.com/repos/owner/repo/releases/assets/123#fragment",
            "https://api.github.com/repos/owner/repo/releases/assets/123/extra").forEach {
            assertFalse(it, AssetDownloadPolicy.isTrusted(it.toHttpUrl()))
        }
    }
    @Test fun `untrusted imported download URL receives no request or credential`() = runBlocking {
        MockWebServer().use { server ->
            val result = AppUpdater(ApplicationProvider.getApplicationContext<Context>()).downloadUpdate(
                server.url("malicious.apk").toString(), "malicious.apk", 0, null, null, false, "dummy-token") { _, _, _ -> }
            assertTrue(result.isFailure)
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun `network boundary strips credentials from redirected external requests`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("final.apk")))
            server.enqueue(MockResponse().setBody("fixture"))
            val client = OkHttpClient.Builder().addNetworkInterceptor(AssetDownloadPolicy.stripUntrustedCredentials).build()
            client.newCall(Request.Builder().url(server.url("initial.apk")).header("Authorization", "Bearer dummy-token").build())
                .execute().use { assertTrue(it.isSuccessful) }
            assertNull(server.takeRequest().getHeader("Authorization"))
            assertNull(server.takeRequest().getHeader("Authorization"))
        }
    }
}
