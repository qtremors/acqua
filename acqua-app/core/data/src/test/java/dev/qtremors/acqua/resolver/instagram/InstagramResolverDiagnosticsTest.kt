package dev.qtremors.acqua.resolver.instagram

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `public posts are resolved without sending the saved session`() {
        MockWebServer().use { server ->
            server.enqueue(mediaPage("https://cdn.test/public.jpg"))
            val resolver = InstagramResolver(server.url("/"))
            resolver.setSessionCookies("sessionid=private-session; csrftoken=private-csrf", "Acqua test")

            val media = resolver.resolve("https://www.instagram.com/p/publicPost/")

            assertEquals("https://cdn.test/public.jpg", media.single().url)
            val request = server.takeRequest()
            assertEquals("/p/publicPost/embed/captioned/", request.path)
            assertNull(request.getHeader("Cookie"))
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `saved session is used only after anonymous access requires login`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("<a href=\"/accounts/login/\">Log in</a>"))
            server.enqueue(mediaPage("https://cdn.test/private.jpg"))
            val resolver = InstagramResolver(server.url("/"))
            resolver.setSessionCookies("sessionid=private-session; csrftoken=private-csrf", "Acqua test")

            val media = resolver.resolve("https://www.instagram.com/p/privatePost/")

            assertEquals("https://cdn.test/private.jpg", media.single().url)
            assertNull(server.takeRequest().getHeader("Cookie"))
            assertTrue(server.takeRequest().getHeader("Cookie").orEmpty().contains("sessionid=private-session"))
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun `server cookies do not leak into the next resolution`() {
        MockWebServer().use { server ->
            server.enqueue(
                mediaPage("https://cdn.test/first.jpg")
                    .addHeader("Set-Cookie", "challenge=stale; Path=/")
            )
            server.enqueue(mediaPage("https://cdn.test/second.jpg"))
            val resolver = InstagramResolver(server.url("/"))

            resolver.resolve("https://www.instagram.com/p/firstPost/")
            resolver.resolve("https://www.instagram.com/p/secondPost/")

            assertNull(server.takeRequest().getHeader("Cookie"))
            assertNull(server.takeRequest().getHeader("Cookie"))
        }
    }

    private fun mediaPage(url: String) = MockResponse()
        .setResponseCode(200)
        .setBody(
            """<html><head><meta property="og:image" content="$url"></head>""" +
                """<body><a href="/accounts/login/">Log in</a></body></html>"""
        )
}
