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

class GitHubApiClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val tokenProvider: () -> String? = { null },
    private val apiBaseUrl: String = "https://api.github.com"
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getRepoInfo(owner: String, repo: String): GitHubRepositoryInfo =
        get("/repos/${path(owner)}/${path(repo)}", GitHubRepositoryInfo.serializer())

    suspend fun getLatestRelease(owner: String, repo: String): GitHubRelease =
        get("/repos/${path(owner)}/${path(repo)}/releases/latest", GitHubRelease.serializer())

    suspend fun getReleases(owner: String, repo: String): List<GitHubRelease> =
        get(
            "/repos/${path(owner)}/${path(repo)}/releases?per_page=30",
            ListSerializer(GitHubRelease.serializer())
        )

    suspend fun getReadme(owner: String, repo: String): String {
        val readme = get(
            "/repos/${path(owner)}/${path(repo)}/readme",
            GitHubReadme.serializer()
        )
        if (!readme.encoding.equals("base64", ignoreCase = true)) return readme.content
        return Base64.decode(readme.content.replace("\n", ""), Base64.DEFAULT).toString(Charsets.UTF_8)
    }

    suspend fun getCurrentUser(token: String): GitHubUser =
        get("/user", GitHubUser.serializer(), token)

    private suspend fun <T> get(
        path: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        tokenOverride: String? = null
    ): T = withContext(Dispatchers.IO) {
        val token = tokenOverride ?: tokenProvider()
        val requestBuilder = Request.Builder()
            .url(apiBaseUrl.trimEnd('/') + path)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Acqua-App/${BuildConfig.VERSION_NAME}")
        token?.takeIf(String::isNotBlank)?.let { requestBuilder.header("Authorization", "Bearer $it") }
        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val reset = response.header("X-RateLimit-Reset")?.toLongOrNull()
                throw GitHubApiException(response.code, reset, responseBody)
            }
            json.decodeFromString(serializer, responseBody)
        }
    }

    private fun path(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid repository name" }
        return value
    }
}

class GitHubApiException(
    val statusCode: Int,
    val rateLimitResetEpochSeconds: Long? = null,
    responseBody: String = ""
) : IOException(
    when {
        statusCode == 403 || statusCode == 429 -> "GitHub rate limit reached"
        statusCode == 404 -> "Repository or release not found"
        else -> "GitHub request failed (HTTP $statusCode)"
    } + responseBody.takeIf { it.isNotBlank() }?.let { ": ${it.take(160)}" }.orEmpty()
)
