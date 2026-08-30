package dev.qtremors.acqua.data.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dev.qtremors.acqua.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

class AppUpdater(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient()
) {

    companion object {
        const val GITHUB_RELEASES_URL = "https://api.github.com/repos/qtremors/acqua/releases/latest"

        fun parseVersionComponents(version: String): List<Int> {
            val clean = version.trim().removePrefix("v").removePrefix("V").split("-", "+")[0]
            return clean.split(".").mapNotNull { it.toIntOrNull() }
        }

        fun compareVersions(version1: String, version2: String): Int {
            val v1 = parseVersionComponents(version1)
            val v2 = parseVersionComponents(version2)
            val maxLen = maxOf(v1.size, v2.size)
            for (i in 0 until maxLen) {
                val p1 = v1.getOrElse(i) { 0 }
                val p2 = v2.getOrElse(i) { 0 }
                if (p1 != p2) return p1.compareTo(p2)
            }
            return 0
        }

        fun deriveVersionCode(versionName: String): Int {
            val clean = versionName.trim().removePrefix("v").removePrefix("V").split("-", "+")[0]
            val digits = clean.replace(".", "").filter { it.isDigit() }
            return digits.toIntOrNull() ?: 0
        }

        fun selectBestApkAsset(assets: List<GitHubAsset>, supportedAbis: Array<String> = Build.SUPPORTED_ABIS): GitHubAsset? {
            val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
            if (apkAssets.isEmpty()) return null

            for (abi in supportedAbis) {
                val normalizedAbi = abi.lowercase()
                val abiPattern = Regex(
                    "(?<![a-z0-9_])${Regex.escape(normalizedAbi)}(?![a-z0-9_])"
                )
                val match = apkAssets.firstOrNull { abiPattern.containsMatchIn(it.name.lowercase()) }
                if (match != null) return match
            }

            val universalMatch = apkAssets.firstOrNull {
                it.name.lowercase().contains("universal") || it.name.lowercase().contains("all")
            }
            if (universalMatch != null) return universalMatch

            val knownAbiPatterns = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86").map { abi ->
                Regex("(?<![a-z0-9_])${Regex.escape(abi)}(?![a-z0-9_])")
            }
            return apkAssets.firstOrNull { asset ->
                knownAbiPatterns.none { it.containsMatchIn(asset.name.lowercase()) }
            }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val isDebugBuild: Boolean
        get() = BuildConfig.DEBUG

    suspend fun checkForUpdate(
        currentVersionName: String = BuildConfig.VERSION_NAME,
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        forceCheckOnDebug: Boolean = false
    ): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            if (isDebugBuild && !forceCheckOnDebug) {
                return@runCatching AppUpdateInfo(
                    currentVersionName = currentVersionName,
                    currentVersionCode = currentVersionCode,
                    latestVersionName = currentVersionName,
                    latestVersionCode = currentVersionCode,
                    isUpdateAvailable = false,
                    releaseTitle = "",
                    releaseNotes = "",
                    releaseUrl = "",
                    apkName = null,
                    apkDownloadUrl = null,
                    apkSizeBytes = 0L,
                    publishedAt = null,
                    isDebugBuild = true
                )
            }

            val request = Request.Builder()
                .url(GITHUB_RELEASES_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Acqua-App/$currentVersionName")
                .build()

            val bodyString = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Failed to check for updates: HTTP ${response.code}")
                }
                response.body?.string() ?: error("Empty response from GitHub API")
            }
            val release: GitHubRelease = json.decodeFromString(bodyString)

            val latestVersionName = release.tag_name.trim().removePrefix("v").removePrefix("V")
            val latestVersionCode = deriveVersionCode(latestVersionName)
            val isUpdateAvailable = compareVersions(latestVersionName, currentVersionName) > 0 ||
                (latestVersionCode > currentVersionCode && latestVersionCode > 0)

            val bestAsset = selectBestApkAsset(release.assets)

            AppUpdateInfo(
                currentVersionName = currentVersionName,
                currentVersionCode = currentVersionCode,
                latestVersionName = latestVersionName,
                latestVersionCode = latestVersionCode,
                isUpdateAvailable = isUpdateAvailable,
                releaseTitle = release.name ?: "Version $latestVersionName",
                releaseNotes = release.body.orEmpty(),
                releaseUrl = release.html_url,
                apkName = bestAsset?.name,
                apkDownloadUrl = bestAsset?.browser_download_url,
                apkSizeBytes = bestAsset?.size ?: 0L,
                publishedAt = release.published_at,
                isDebugBuild = isDebugBuild
            )
        }
    }

    suspend fun downloadUpdate(
        downloadUrl: String,
        fileName: String,
        expectedSizeBytes: Long = 0L,
        expectedVersionCode: Int? = null,
        onProgress: (bytesDownloaded: Long, totalBytes: Long, progressPercent: Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            updatesDir.listFiles()?.forEach { it.delete() }

            val safeFileName = fileName.substringAfterLast('/').substringAfterLast('\\')
            require(safeFileName.isNotBlank() && safeFileName.endsWith(".apk", ignoreCase = true)) {
                "Update asset is not an APK"
            }
            val targetFile = File(updatesDir, safeFileName)
            val partialFile = File(updatesDir, ".$safeFileName.part")
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "Acqua-App/${BuildConfig.VERSION_NAME}")
                .build()

            try {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("Failed to download APK: HTTP ${response.code}")
                    }
                    val body = response.body ?: error("Download body is null")
                    val contentLength = body.contentLength()

                    body.byteStream().use { input: InputStream ->
                        FileOutputStream(partialFile).use { output: FileOutputStream ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalRead = 0L

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                val progressTotal = contentLength.takeIf { it > 0 } ?: expectedSizeBytes
                                val percent = if (progressTotal > 0) {
                                    ((totalRead * 100) / progressTotal).toInt().coerceIn(0, 100)
                                } else 0
                                onProgress(totalRead, progressTotal, percent)
                            }
                            output.flush()
                        }
                    }

                    require(partialFile.length() > 0L) { "Downloaded APK is empty" }
                    if (contentLength > 0L) {
                        require(partialFile.length() == contentLength) { "Downloaded APK is incomplete" }
                    }
                    if (expectedSizeBytes > 0L) {
                        require(partialFile.length() == expectedSizeBytes) { "Downloaded APK size does not match the release" }
                    }
                }
                validateApk(partialFile, expectedVersionCode).getOrThrow()
                require(partialFile.renameTo(targetFile)) { "Could not finalize downloaded APK" }
            } catch (error: Throwable) {
                partialFile.delete()
                throw error
            }

            targetFile
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            runCatching { context.startActivity(intent) }.recoverCatching {
                context.startActivity(
                    Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            }.getOrThrow()
        }
    }

    fun installApk(apkFile: File): Result<Unit> = runCatching {
        validateApk(apkFile).getOrThrow()
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    fun validateApk(apkFile: File, expectedVersionCode: Int? = null): Result<Unit> = runCatching {
        require(apkFile.isFile && apkFile.length() > 0L) { "Downloaded APK is unavailable" }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        @Suppress("DEPRECATION")
        val archive = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: error("Downloaded file is not a valid APK")
        require(archive.packageName == context.packageName) { "Downloaded APK belongs to a different app" }
        val archiveVersionCode = archive.compatVersionCode()
        require(archiveVersionCode > BuildConfig.VERSION_CODE) { "Downloaded APK is not a newer version" }
        if (expectedVersionCode != null && expectedVersionCode > 0) {
            require(archiveVersionCode == expectedVersionCode.toLong()) {
                "Downloaded APK version does not match the release"
            }
        }
        val minSdk = archive.applicationInfo?.minSdkVersion ?: 1
        require(minSdk <= Build.VERSION.SDK_INT) { "Downloaded APK requires a newer Android version" }

        @Suppress("DEPRECATION")
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val archiveDigests = archive.signingCertificates().mapTo(hashSetOf(), ::sha256)
        val installedDigests = installed.signingCertificates().mapTo(hashSetOf(), ::sha256)
        require(archiveDigests.isNotEmpty() && archiveDigests.any { it in installedDigests }) {
            "Downloaded APK signature does not match the installed app"
        }
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.signingCertificates(): List<Signature> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && signingInfo != null) {
            val info = signingInfo ?: return emptyList()
            return if (info.hasMultipleSigners()) {
                info.apkContentsSigners?.toList().orEmpty()
            } else {
                info.signingCertificateHistory?.toList().orEmpty()
            }
        }
        return signatures?.toList().orEmpty()
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.compatVersionCode(): Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        longVersionCode
    } else {
        versionCode.toLong()
    }

    private fun sha256(signature: Signature): String = MessageDigest.getInstance("SHA-256")
        .digest(signature.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
