package dev.qtremors.acqua.data.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import androidx.core.net.toUri
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
) : AppUpdateGateway {

    companion object {
        const val GITHUB_RELEASES_URL = "https://api.github.com/repos/qtremors/acqua/releases/latest"

        fun parseVersionComponents(version: String): List<Int> {
            val clean = version.trim()
                .replace(Regex("^(?:v|release[-_])", RegexOption.IGNORE_CASE), "")
                .substringBefore('+')
            return Regex("\\d+").findAll(clean.substringBefore('-')).map { it.value.toInt() }.toList()
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
            return preReleaseRank(version1).compareTo(preReleaseRank(version2))
        }

        fun deriveVersionCode(versionName: String): Int {
            val digits = parseVersionComponents(versionName).joinToString("")
            return digits.toIntOrNull() ?: 0
        }

        fun selectBestApkAsset(assets: List<GitHubAsset>, supportedAbis: Array<String> = Build.SUPPORTED_ABIS): GitHubAsset? {
            val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
            if (apkAssets.isEmpty()) return null

            for (abi in supportedAbis) {
                val normalizedAbi = normalizeAssetName(abi)
                val architectureSuffixGuard = if (normalizedAbi == "x86") "(?!-64)" else ""
                val abiPattern = Regex(
                    "(?<![a-z0-9])${Regex.escape(normalizedAbi)}$architectureSuffixGuard(?![a-z0-9])"
                )
                val match = apkAssets.firstOrNull { abiPattern.containsMatchIn(normalizeAssetName(it.name)) }
                if (match != null) return match
            }

            val universalMatch = apkAssets.firstOrNull {
                it.name.lowercase().contains("universal") || it.name.lowercase().contains("all")
            }
            if (universalMatch != null) return universalMatch

            val knownAbiPatterns = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86").map { abi ->
                Regex("(?<![a-z0-9])${Regex.escape(normalizeAssetName(abi))}(?![a-z0-9])")
            }
            return apkAssets.firstOrNull { asset ->
                knownAbiPatterns.none { it.containsMatchIn(normalizeAssetName(asset.name)) }
            }
        }

        private fun normalizeAssetName(value: String): String = value.lowercase()
            .replace("x86_64", "x86-64")
            .replace('_', '-')

        private fun preReleaseRank(version: String): Int {
            val normalized = version.lowercase().substringBefore('+')
            return when {
                "alpha" in normalized -> 0
                "beta" in normalized -> 1
                Regex("(?:^|[-_.])rc(?:[-_.]?\\d+)?").containsMatchIn(normalized) -> 2
                else -> 3
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
                releaseTitle = release.name ?: latestVersionName,
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

    override suspend fun downloadUpdate(
        downloadUrl: String,
        fileName: String,
        expectedSizeBytes: Long,
        expectedVersionCode: Int?,
        expectedPackageName: String?,
        requireNewerThanInstalled: Boolean,
        authorizationToken: String?,
        onProgress: (bytesDownloaded: Long, totalBytes: Long, progressPercent: Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val requestedFileName = fileName.substringAfterLast('/').substringAfterLast('\\')
            require(requestedFileName.isNotBlank() && requestedFileName.endsWith(".apk", ignoreCase = true)) {
                "Update asset is not an APK"
            }
            val baseName = requestedFileName.dropLast(4).replace(Regex("[^A-Za-z0-9._-]"), "_")
            val safeFileName = "$baseName-${downloadUrl.hashCode().toUInt().toString(16)}.apk"
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val targetFile = File(updatesDir, safeFileName)
            val partialFile = File(updatesDir, ".$safeFileName.part")
            updatesDir.listFiles()?.filter {
                it != targetFile && it != partialFile && System.currentTimeMillis() - it.lastModified() > 86_400_000L
            }?.forEach(File::delete)
            targetFile.delete()
            if (expectedSizeBytes > 0L && partialFile.length() >= expectedSizeBytes) partialFile.delete()

            val existingBytes = partialFile.length()
            val requestBuilder = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "Acqua-App/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/octet-stream")
            if (existingBytes > 0L) requestBuilder.header("Range", "bytes=$existingBytes-")
            authorizationToken?.takeIf(String::isNotBlank)?.let {
                requestBuilder.header("Authorization", "Bearer $it")
            }

            try {
                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("Failed to download APK: HTTP ${response.code}")
                    }
                    val body = response.body ?: error("Download body is null")
                    val contentLength = body.contentLength()
                    val resumed = response.code == 206 && existingBytes > 0L
                    if (!resumed && existingBytes > 0L) partialFile.delete()
                    val initialBytes = if (resumed) existingBytes else 0L

                    body.byteStream().use { input: InputStream ->
                        FileOutputStream(partialFile, resumed).use { output: FileOutputStream ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalRead = initialBytes

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                val progressTotal = expectedSizeBytes.takeIf { it > 0 }
                                    ?: contentLength.takeIf { it > 0 }?.plus(initialBytes)
                                    ?: 0L
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
                        require(partialFile.length() == contentLength + initialBytes) { "Downloaded APK is incomplete" }
                    }
                    if (expectedSizeBytes > 0L) {
                        require(partialFile.length() == expectedSizeBytes) { "Downloaded APK size does not match the release" }
                    }
                }
                validateApk(
                    partialFile,
                    expectedVersionCode = expectedVersionCode,
                    expectedPackageName = expectedPackageName,
                    requireNewerThanInstalled = requireNewerThanInstalled
                ).getOrThrow()
                require(partialFile.renameTo(targetFile)) { "Could not finalize downloaded APK" }
            } catch (error: Throwable) {
                if (error !is java.io.IOException) partialFile.delete()
                throw error
            }

            targetFile
        }
    }

    override fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    override fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = "package:${context.packageName}".toUri()
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

    override fun installApk(
        apkFile: File,
        expectedPackageName: String?,
        requireNewerThanInstalled: Boolean
    ): Result<Unit> = runCatching {
        validateApk(
            apkFile,
            expectedPackageName = expectedPackageName,
            requireNewerThanInstalled = requireNewerThanInstalled
        ).getOrThrow()
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

    fun validateApk(
        apkFile: File,
        expectedVersionCode: Int? = null,
        expectedPackageName: String? = context.packageName,
        requireNewerThanInstalled: Boolean = true
    ): Result<Unit> = runCatching {
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
        if (expectedPackageName != null) {
            require(archive.packageName == expectedPackageName) { "Downloaded APK belongs to a different app" }
        }
        val archiveVersionCode = archive.compatVersionCode()
        if (expectedVersionCode != null && expectedVersionCode > 0) {
            require(archiveVersionCode == expectedVersionCode.toLong()) {
                "Downloaded APK version does not match the release"
            }
        }
        val minSdk = archive.applicationInfo?.minSdkVersion ?: 1
        require(minSdk <= Build.VERSION.SDK_INT) { "Downloaded APK requires a newer Android version" }

        val installedPackage = expectedPackageName?.let { packageName ->
            runCatching {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, flags)
            }.getOrNull()
        }
        if (installedPackage != null) {
            if (requireNewerThanInstalled) {
                require(archiveVersionCode > installedPackage.compatVersionCode()) {
                    "Downloaded APK is not a newer version"
                }
            }
            val archiveDigests = archive.signingCertificates().mapTo(hashSetOf(), ::sha256)
            val installedDigests = installedPackage.signingCertificates().mapTo(hashSetOf(), ::sha256)
            require(archiveDigests.isNotEmpty() && archiveDigests.any { it in installedDigests }) {
                "Downloaded APK signature does not match the installed app"
            }
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
