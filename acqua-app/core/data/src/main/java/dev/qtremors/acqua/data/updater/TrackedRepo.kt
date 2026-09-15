package dev.qtremors.acqua.data.updater

import kotlinx.serialization.Serializable

@Serializable
data class TrackedRepo(
    val owner: String,
    val name: String,
    val fullName: String,
    val description: String? = null,
    val stars: Int = 0,
    val avatarUrl: String = "",
    val latestTagName: String = "",
    val latestReleaseName: String? = null,
    val latestReleaseBody: String? = null,
    val latestReleaseUrl: String? = null,
    val downloadUrl: String? = null,
    val downloadApiUrl: String? = null,
    val apkSizeBytes: Long = 0L,
    val publishedAt: String? = null,
    val selectedApkName: String = AUTO_APK,
    val mappedPackageName: String? = null,
    val mappedAppName: String? = null,
    val autoMatchInstalledApp: Boolean = true,
    val installedVersionName: String? = null,
    val isUpdateAvailable: Boolean = false,
    val allowPreReleases: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val AUTO_APK = "Auto"
    }
}

object DefaultTrackedRepos {
    val repositories: List<TrackedRepo> = listOf(
        "acqua",
        "arcile",
        "filion",
        "earnslate",
        "material-design",
        "osyster"
    ).map { name ->
        TrackedRepo(
            owner = "qtremors",
            name = name,
            fullName = "qtremors/$name",
            addedAt = 0L
        )
    }
}

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long
)
