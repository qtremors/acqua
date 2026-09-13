package dev.qtremors.acqua.data.updater

import android.util.Base64
import dev.qtremors.acqua.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class GitHubApiClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val tokenProvider: () -> String? = { null },
    private val apiBaseUrl: String = "https://api.github.com"
) : GitHubRepositoryService {
    private val json = Json { ignoreUnknownKeys = true }
    private val rateLimitedUntilEpochSeconds = AtomicLong(0L)

    override suspend fun getRepoInfo(owner: String, repo: String): GitHubRepositoryInfo =
        get("/repos/${path(owner)}/${path(repo)}", GitHubRepositoryInfo.serializer())

    override suspend fun getLatestRelease(owner: String, repo: String): GitHubRelease =
        get("/repos/${path(owner)}/${path(repo)}/releases/latest", GitHubRelease.serializer())

    override suspend fun getReleases(owner: String, repo: String): List<GitHubRelease> {
        val releases = mutableListOf<GitHubRelease>()
        for (page in 1..MAX_RELEASE_PAGES) {
            val batch = get(
                "/repos/${path(owner)}/${path(repo)}/releases?per_page=$RELEASE_PAGE_SIZE&page=$page",
                ListSerializer(GitHubRelease.serializer())
            )
            releases += batch
            if (batch.size < RELEASE_PAGE_SIZE) break
        }
        return releases
    }

    override suspend fun getReadme(owner: String, repo: String): String {
        val readme = get(
            "/repos/${path(owner)}/${path(repo)}/readme",
            GitHubReadme.serializer()
        )
        if (!readme.encoding.equals("base64", ignoreCase = true)) return readme.content
        return Base64.decode(readme.content.replace("\n", ""), Base64.DEFAULT).toString(Charsets.UTF_8)
    }

    override suspend fun getCurrentUser(token: String): GitHubUser =
        get("/user", GitHubUser.serializer(), token)

    private suspend fun <T> get(
        path: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        tokenOverride: String? = null
    ): T = withContext(Dispatchers.IO) {
        val blockedUntil = rateLimitedUntilEpochSeconds.get()
        if (blockedUntil > System.currentTimeMillis() / 1_000L) {
            throw GitHubApiException(429, blockedUntil)
        }
        val token = tokenOverride ?: tokenProvider()
        val requestBuilder = Request.Builder()
            .url(apiBaseUrl.trimEnd('/') + path)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Acqua-App/${BuildConfig.VERSION_NAME}")
        token?.takeIf(String::isNotBlank)?.let { requestBuilder.header("Authorization", "Bearer $it") }
        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            val remaining = response.header("X-RateLimit-Remaining")?.toLongOrNull()
            val reset = response.header("X-RateLimit-Reset")?.toLongOrNull()
            if (remaining == 0L && reset != null) rateLimitedUntilEpochSeconds.accumulateAndGet(reset, ::maxOf)
            if (!response.isSuccessful) {
                throw GitHubApiException(response.code, reset)
            }
            json.decodeFromString(serializer, responseBody)
        }
    }

    private fun path(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid repository name" }
        return value
    }

    private companion object {
        const val RELEASE_PAGE_SIZE = 100
        const val MAX_RELEASE_PAGES = 5

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}

class GitHubApiException(
    val statusCode: Int,
    val rateLimitResetEpochSeconds: Long? = null
) : IOException(
    when {
        statusCode == 403 || statusCode == 429 -> "GitHub rate limit reached"
        statusCode == 404 -> "Repository or release not found"
        else -> "GitHub request failed (HTTP $statusCode)"
    }
)
