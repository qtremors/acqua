package dev.qtremors.acqua.data.updater

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
