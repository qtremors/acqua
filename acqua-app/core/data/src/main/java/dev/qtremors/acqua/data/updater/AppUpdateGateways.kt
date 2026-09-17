package dev.qtremors.acqua.data.updater

import java.io.File
import kotlinx.coroutines.flow.Flow

interface TrackedRepoStore {
    val trackedRepos: Flow<List<TrackedRepo>>
    suspend fun addOrUpdateRepo(repo: TrackedRepo)
    suspend fun ensureDefaultRepos(): Boolean
    suspend fun removeRepo(fullName: String)
    suspend fun updateAll(repos: List<TrackedRepo>)
    suspend fun exportBackup(): String
    suspend fun importBackup(jsonString: String): Result<Int>
}

interface GitHubRepositoryService {
    suspend fun getRepoInfo(owner: String, repo: String): GitHubRepositoryInfo
    suspend fun getLatestRelease(owner: String, repo: String): GitHubRelease
    suspend fun getReleases(owner: String, repo: String): List<GitHubRelease>
    suspend fun getReadme(owner: String, repo: String): String
    suspend fun getCurrentUser(token: String): GitHubUser
}

interface GitHubAuthStore {
    val token: String?
    val username: String?
    fun saveToken(token: String, username: String)
    fun signOut()
}

interface InstalledAppCatalog {
    suspend fun installedApps(): List<InstalledApp>
    suspend fun findBestMatch(repoName: String): InstalledApp?
    fun findBestMatch(repoName: String, apps: List<InstalledApp>): InstalledApp?
    fun installedVersion(packageName: String): InstalledApp?
}

interface AppUpdateGateway {
    suspend fun downloadUpdate(
        downloadUrl: String,
        fileName: String,
        expectedSizeBytes: Long = 0L,
        expectedVersionCode: Int? = null,
        expectedPackageName: String? = null,
        requireNewerThanInstalled: Boolean = true,
        authorizationToken: String? = null,
        onProgress: (bytesDownloaded: Long, totalBytes: Long, progressPercent: Int) -> Unit
    ): Result<File>

    fun canRequestPackageInstalls(): Boolean
    fun openInstallPermissionSettings()
    fun installApk(
        apkFile: File,
        expectedPackageName: String? = null,
        requireNewerThanInstalled: Boolean = true
    ): Result<Unit>
}
