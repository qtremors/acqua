package dev.qtremors.acqua.data.updater

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GitHubApiClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `repo request includes token and parses metadata`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "name": "sample",
                  "full_name": "owner/sample",
                  "description": "An Android app",
                  "stargazers_count": 42,
                  "html_url": "https://github.com/owner/sample",
                  "owner": {"login": "owner", "avatar_url": "https://example.com/avatar.png"}
                }
                """.trimIndent()
            )
        )
        val api = GitHubApiClient(
            tokenProvider = { "secret-token" },
            apiBaseUrl = server.url("/").toString().trimEnd('/')
        )

        val repo = api.getRepoInfo("owner", "sample")

        assertEquals("owner/sample", repo.full_name)
        assertEquals(42, repo.stargazers_count)
        val request = server.takeRequest()
        assertEquals("Bearer secret-token", request.getHeader("Authorization"))
        assertEquals("/repos/owner/sample", request.path)
    }

    @Test
    fun `rate limit response exposes reset time`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("X-RateLimit-Reset", "1800000000")
                .setBody("rate limited")
        )
        val api = GitHubApiClient(apiBaseUrl = server.url("/").toString().trimEnd('/'))

        val error = runCatching { api.getRepoInfo("owner", "sample") }.exceptionOrNull()

        assertTrue(error is GitHubApiException)
        assertEquals(1_800_000_000L, (error as GitHubApiException).rateLimitResetEpochSeconds)
    }
}
