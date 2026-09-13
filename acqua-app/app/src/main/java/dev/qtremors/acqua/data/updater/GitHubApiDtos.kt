package dev.qtremors.acqua.data.updater

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val name: String? = null,
    val body: String? = null,
    val html_url: String,
    val published_at: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
data class GitHubAsset(
    val name: String,
    val browser_download_url: String,
    val size: Long = 0L,
    val content_type: String? = null,
    val url: String? = null
)

@Serializable
data class GitHubRepositoryInfo(
    val name: String,
    val full_name: String,
    val description: String? = null,
    val stargazers_count: Int = 0,
    val html_url: String,
    val owner: GitHubOwner
)

@Serializable
data class GitHubOwner(
    val login: String,
    val avatar_url: String = ""
)

@Serializable
data class GitHubReadme(
    val content: String = "",
    val encoding: String = "base64"
)

@Serializable
data class GitHubUser(
    val login: String,
    val avatar_url: String = ""
)
