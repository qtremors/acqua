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
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
data class GitHubAsset(
    val name: String,
    val browser_download_url: String,
    val size: Long = 0L,
    val content_type: String? = null
)

data class AppUpdateInfo(
    val currentVersionName: String,
    val currentVersionCode: Int,
    val latestVersionName: String,
    val latestVersionCode: Int,
    val isUpdateAvailable: Boolean,
    val releaseTitle: String,
    val releaseNotes: String,
    val releaseUrl: String,
    val apkName: String?,
    val apkDownloadUrl: String?,
    val apkSizeBytes: Long,
    val publishedAt: String?,
    val isDebugBuild: Boolean = false
)

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progressPercent: Int
    ) : UpdateDownloadState
    data class Downloaded(val apkFile: File) : UpdateDownloadState
    data class Error(val message: String) : UpdateDownloadState
}
