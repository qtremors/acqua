package dev.qtremors.acqua.data.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledAppMatcher(private val context: Context) {
    suspend fun installedApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val activities = packageManager.queryIntentActivities(launcherIntent, 0)
        activities.mapNotNull { resolved ->
            val packageName = resolved.activityInfo?.packageName ?: return@mapNotNull null
            runCatching {
                val info = packageInfo(packageManager, packageName)
                InstalledApp(
                    packageName = packageName,
                    appName = resolved.loadLabel(packageManager).toString(),
                    versionName = info.versionName.orEmpty(),
                    versionCode = info.compatVersionCode()
                )
            }.getOrNull()
        }.distinctBy(InstalledApp::packageName).sortedBy { it.appName.lowercase() }
    }

    suspend fun findBestMatch(repoName: String): InstalledApp? {
        return findBestMatch(repoName, installedApps())
    }

    fun findBestMatch(repoName: String, apps: List<InstalledApp>): InstalledApp? {
        val target = normalize(repoName)
        return apps.maxByOrNull { app -> matchScore(target, normalize(app.appName)) }
            ?.takeIf { matchScore(target, normalize(it.appName)) >= 70 }
    }

    fun installedVersion(packageName: String): InstalledApp? = runCatching {
        val packageManager = context.packageManager
        val info = packageInfo(packageManager, packageName)
        InstalledApp(
            packageName = packageName,
            appName = info.applicationInfo?.loadLabel(packageManager)?.toString() ?: packageName,
            versionName = info.versionName.orEmpty(),
            versionCode = info.compatVersionCode()
        )
    }.getOrNull()

    companion object {
        fun normalize(value: String): String = value.lowercase().filter(Char::isLetterOrDigit)

        fun matchScore(target: String, candidate: String): Int = when {
            target.isBlank() || candidate.isBlank() -> 0
            target == candidate -> 100
            candidate.startsWith(target) || target.startsWith(candidate) -> 85
            candidate.contains(target) || target.contains(candidate) -> 70
            else -> 0
        }

        @Suppress("DEPRECATION")
        private fun packageInfo(packageManager: PackageManager, packageName: String): PackageInfo =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                packageManager.getPackageInfo(packageName, 0)
            }

        @Suppress("DEPRECATION")
        private fun PackageInfo.compatVersionCode(): Long =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()
    }
}
