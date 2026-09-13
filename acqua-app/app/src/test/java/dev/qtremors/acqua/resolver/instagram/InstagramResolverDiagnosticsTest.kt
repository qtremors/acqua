package dev.qtremors.acqua.resolver.instagram

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramResolverDiagnosticsTest {
    @Test
    fun `remote response bodies never become public extraction messages`() {
        val secret = "token_0123456789_private@example.com"
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(503).setBody(
                    "<html><body>Cookie: sessionid=$secret</body></html>"
                )
            )
            server.enqueue(MockResponse().setResponseCode(200).setBody("home"))
            server.enqueue(
                MockResponse().setResponseCode(429).setBody(
                    "{\"message\":\"Authorization: Bearer $secret\"}"
                )
            )
            val resolver = InstagramResolver(server.url("/"))

            val failure = runCatching {
                resolver.resolve("https://www.instagram.com/p/safeShortcode/")
            }.exceptionOrNull()

            assertTrue(failure is InstagramExtractionException)
            assertEquals("Instagram extraction failed.", failure?.message)
            assertFalse(failure?.message.orEmpty().contains(secret))
            assertTrue(server.requestCount == 3)
        }
    }

    @Test
    fun `invalid input does not echo the submitted URL`() {
        val submitted = "https://example.com/account/private-token"

        val failure = runCatching { InstagramResolver().resolve(submitted) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertFalse(failure?.message.orEmpty().contains(submitted))
    }
}
