package dev.qtremors.acqua.feature.updater

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.qtremors.acqua.data.updater.AppUpdater
import dev.qtremors.acqua.data.updater.GitHubRepositoryService
import dev.qtremors.acqua.data.updater.GitHubApiException
import dev.qtremors.acqua.data.updater.GitHubAsset
import dev.qtremors.acqua.data.updater.GitHubAuthStore
import dev.qtremors.acqua.data.updater.GitHubRelease
import dev.qtremors.acqua.data.updater.GitHubRepositoryInfo
import dev.qtremors.acqua.data.updater.InstalledApp
import dev.qtremors.acqua.data.updater.InstalledAppCatalog
import dev.qtremors.acqua.data.updater.TrackedRepo
import dev.qtremors.acqua.data.updater.TrackedRepoStore
import dev.qtremors.acqua.data.updater.AppUpdateGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class RepoPreview(
    val repository: GitHubRepositoryInfo,
    val release: GitHubRelease,
    val readme: String,
    val selectedApkName: String,
    val linkedApp: InstalledApp?,
    val autoMatchInstalledApp: Boolean,
    val allowPreReleases: Boolean,
    val notificationsEnabled: Boolean
) {
    val apkAssets: List<GitHubAsset> get() = release.assets.filter { it.name.endsWith(".apk", true) }
}

data class FeedsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val hasRefreshed: Boolean = false,
    val refreshCompleted: Int = 0,
    val refreshTotal: Int = 0,
    val isSearching: Boolean = false,
    val preview: RepoPreview? = null,
    val installedApps: List<InstalledApp> = emptyList(),
    val downloadStates: Map<String, UpdateDownloadState> = emptyMap(),
    val authenticatedUser: String? = null,
    val isSavingToken: Boolean = false,
    val message: AppUpdateNotice? = null
)

class FeedsViewModel(
    private val repository: TrackedRepoStore,
    private val api: GitHubRepositoryService,
    private val auth: GitHubAuthStore,
    private val matcher: InstalledAppCatalog,
    private val updater: AppUpdateGateway,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private var previewJob: Job? = null
    val trackedRepos: StateFlow<List<TrackedRepo>> = repository.trackedRepos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(
        FeedsUiState(authenticatedUser = auth.username)
    )
    val state: StateFlow<FeedsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureDefaultRepos()
        }
        viewModelScope.launch {
            repository.trackedRepos.collect {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun searchRepository(query: String, allowPreReleases: Boolean = false) {
        val parsed = parseRepoQuery(query)
        if (parsed == null) {
            showMessage(AppUpdateNotice.Failure(AppUpdateFailure.INVALID_REPOSITORY))
            return
        }
        _state.value = _state.value.copy(isSearching = true, preview = null, message = null)
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            try {
                val (owner, name) = parsed
                val info = api.getRepoInfo(owner, name)
                val release = releaseFor(owner, name, allowPreReleases)
                val apks = release.assets.filter { it.name.endsWith(".apk", true) }
                if (apks.isEmpty()) throw AppUpdateWorkflowException(AppUpdateFailure.NO_APK)
                val readme = runCatching { api.getReadme(owner, name) }.getOrDefault("")
                val linkedApp = matcher.findBestMatch(info.name)
                _state.value = _state.value.copy(
                    isSearching = false,
                    preview = RepoPreview(
                        repository = info,
                        release = release,
                        readme = readme,
                        selectedApkName = TrackedRepo.AUTO_APK,
                        linkedApp = linkedApp,
                        autoMatchInstalledApp = true,
                        allowPreReleases = allowPreReleases || release.prerelease,
                        notificationsEnabled = true
                    )
                )
                loadInstalledApps()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _state.value = _state.value.copy(isSearching = false, message = noticeFor(error))
            }
        }
    }

    fun editRepo(repo: TrackedRepo) {
        _state.value = _state.value.copy(isSearching = true, preview = null, message = null)
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            try {
                val info = api.getRepoInfo(repo.owner, repo.name)
                val release = releaseFor(repo.owner, repo.name, repo.allowPreReleases)
                val apks = release.assets.filter { it.name.endsWith(".apk", true) }
                if (apks.isEmpty()) throw AppUpdateWorkflowException(AppUpdateFailure.NO_APK)
                val linked = repo.mappedPackageName?.let(matcher::installedVersion)
                val selected = repo.selectedApkName.takeIf { choice ->
                    choice == TrackedRepo.AUTO_APK || apks.any { it.name == choice }
                }
                    ?: AppUpdater.selectBestApkAsset(apks)?.name
                    ?: apks.first().name
                _state.value = _state.value.copy(
                    isSearching = false,
                    preview = RepoPreview(
                        info,
                        release,
                        runCatching { api.getReadme(repo.owner, repo.name) }.getOrDefault(""),
                        selected,
                        linked,
                        repo.autoMatchInstalledApp,
                        repo.allowPreReleases,
                        repo.notificationsEnabled
                    )
                )
                loadInstalledApps()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _state.value = _state.value.copy(isSearching = false, message = noticeFor(error))
            }
        }
    }

    fun selectApk(name: String) = updatePreview { copy(selectedApkName = name) }
    fun selectInstalledApp(app: InstalledApp?) = updatePreview {
        copy(linkedApp = app, autoMatchInstalledApp = false)
    }
    fun setAllowPreReleases(enabled: Boolean) = updatePreview { copy(allowPreReleases = enabled) }
    fun clearPreview() {
        previewJob?.cancel()
        previewJob = null
        _state.value = _state.value.copy(preview = null, isSearching = false)
    }
    fun clearMessage() { _state.value = _state.value.copy(message = null) }

    fun savePreview() {
        val preview = _state.value.preview ?: return
        val asset = preview.apkAssets.firstOrNull {
            preview.selectedApkName != TrackedRepo.AUTO_APK && it.name == preview.selectedApkName
        } ?: AppUpdater.selectBestApkAsset(preview.apkAssets)
            ?: return showMessage(AppUpdateNotice.Failure(AppUpdateFailure.NO_APK))
        viewModelScope.launch {
            val info = preview.repository
            val installed = preview.linkedApp
            repository.addOrUpdateRepo(
                TrackedRepo(
                    owner = info.owner.login,
                    name = info.name,
                    fullName = info.full_name,
                    description = info.description,
                    stars = info.stargazers_count,
                    avatarUrl = info.owner.avatar_url,
                    latestTagName = preview.release.tag_name,
                    latestReleaseName = preview.release.name,
                    latestReleaseBody = preview.release.body,
                    latestReleaseUrl = preview.release.html_url,
                    downloadUrl = asset.browser_download_url,
                    downloadApiUrl = asset.url,
                    apkSizeBytes = asset.size,
                    publishedAt = preview.release.published_at,
                    selectedApkName = preview.selectedApkName,
                    mappedPackageName = installed?.packageName,
                    mappedAppName = installed?.appName,
                    autoMatchInstalledApp = preview.autoMatchInstalledApp,
                    installedVersionName = installed?.versionName,
                    isUpdateAvailable = installed?.let {
                        AppUpdater.compareVersions(preview.release.tag_name, it.versionName) > 0
                    } ?: false,
                    allowPreReleases = preview.allowPreReleases,
                    notificationsEnabled = preview.notificationsEnabled
                )
            )
            _state.value = _state.value.copy(preview = null, message = AppUpdateNotice.RepositoryTracked)
        }
    }

    fun removeRepo(repo: TrackedRepo) {
        viewModelScope.launch {
            repository.removeRepo(repo.fullName)
            _state.value = _state.value.copy(message = AppUpdateNotice.RepositoryUntracked(repo.fullName))
        }
    }

    fun refresh() {
        val current = trackedRepos.value
        if (current.isEmpty() || _state.value.isRefreshing) return
        _state.value = _state.value.copy(
            isRefreshing = true,
            refreshCompleted = 0,
            refreshTotal = current.size,
            message = null
        )
        viewModelScope.launch {
            val updated = current.toMutableList()
            var firstError: Throwable? = null
            val installedApps = matcher.installedApps()
            for ((index, tracked) in current.withIndex()) {
                try {
                    val info = api.getRepoInfo(tracked.owner, tracked.name)
                    val release = try {
                        releaseFor(tracked.owner, tracked.name, tracked.allowPreReleases)
                    } catch (error: GitHubApiException) {
                        if (error.statusCode == 404) null else throw error
                    }
                    val apks = release?.assets?.filter { it.name.endsWith(".apk", true) }.orEmpty()
                    val asset = apks.firstOrNull { it.name == tracked.selectedApkName }
                        ?: AppUpdater.selectBestApkAsset(apks)
                    val installed = tracked.mappedPackageName?.let(matcher::installedVersion)
                        ?: tracked.takeIf { it.autoMatchInstalledApp }
                            ?.let { matcher.findBestMatch(it.name, installedApps) }
                    updated[index] = tracked.copy(
                        name = info.name,
                        fullName = info.full_name,
                        description = info.description,
                        stars = info.stargazers_count,
                        avatarUrl = info.owner.avatar_url,
                        latestTagName = release?.tag_name ?: tracked.latestTagName,
                        latestReleaseName = release?.name ?: tracked.latestReleaseName,
                        latestReleaseBody = release?.body ?: tracked.latestReleaseBody,
                        latestReleaseUrl = release?.html_url ?: tracked.latestReleaseUrl,
                        downloadUrl = asset?.browser_download_url,
                        downloadApiUrl = asset?.url,
                        apkSizeBytes = asset?.size ?: 0L,
                        publishedAt = release?.published_at ?: tracked.publishedAt,
                        mappedPackageName = installed?.packageName ?: tracked.mappedPackageName,
                        mappedAppName = installed?.appName ?: tracked.mappedAppName,
                        installedVersionName = installed?.versionName,
                        isUpdateAvailable = release?.let { latest -> installed?.let {
                            AppUpdater.compareVersions(latest.tag_name, it.versionName) > 0
                        } } ?: false
                    )
                } catch (error: Throwable) {
                    if (firstError == null) firstError = error
                }
                _state.value = _state.value.copy(refreshCompleted = index + 1)
            }
            repository.updateAll(updated)
            _state.value = _state.value.copy(
                isRefreshing = false,
                hasRefreshed = true,
                message = firstError?.let(::noticeFor)
                    ?: AppUpdateNotice.RefreshComplete(updated.size)
            )
        }
    }

    fun download(repo: TrackedRepo) {
        if (repo.downloadUrl == null && repo.downloadApiUrl == null) {
            return showMessage(AppUpdateNotice.Failure(AppUpdateFailure.NO_APK))
        }
        when (val download = _state.value.downloadStates[repo.fullName]) {
            is UpdateDownloadState.Downloading -> return
            is UpdateDownloadState.Downloaded -> return install(repo, download.apkFile)
            else -> Unit
        }
        viewModelScope.launch {
            val token = withContext(ioDispatcher) { auth.token }
            val url = (if (!token.isNullOrBlank()) repo.downloadApiUrl ?: repo.downloadUrl else repo.downloadUrl)
                ?: return@launch showMessage(AppUpdateNotice.Failure(AppUpdateFailure.NO_APK))
            _state.value = _state.value.withDownload(repo.fullName, UpdateDownloadState.Downloading(0, repo.apkSizeBytes, 0))
            updater.downloadUpdate(
                downloadUrl = url,
                fileName = repo.selectedApkName.takeUnless { it == TrackedRepo.AUTO_APK }
                    ?: repo.downloadUrl?.substringAfterLast('/')
                    ?: url.substringAfterLast('/'),
                expectedSizeBytes = repo.apkSizeBytes,
                expectedPackageName = repo.mappedPackageName,
                requireNewerThanInstalled = repo.installedVersionName != null,
                authorizationToken = token.takeIf { url == repo.downloadApiUrl }
            ) { bytes, total, percent ->
                _state.value = _state.value.withDownload(
                    repo.fullName,
                    UpdateDownloadState.Downloading(bytes, total, percent)
                )
            }.fold(
                onSuccess = { file ->
                    _state.value = _state.value.withDownload(repo.fullName, UpdateDownloadState.Downloaded(file))
                    if (updater.canRequestPackageInstalls()) install(repo, file)
                },
                onFailure = { error ->
                    _state.value = _state.value.withDownload(
                        repo.fullName,
                        UpdateDownloadState.Error(AppUpdateFailure.DOWNLOAD)
                    )
                }
            )
        }
    }

    fun install(repo: TrackedRepo, file: File) {
        updater.installApk(
            file,
            expectedPackageName = repo.mappedPackageName,
            requireNewerThanInstalled = repo.installedVersionName != null
        ).onFailure { showMessage(AppUpdateNotice.Failure(AppUpdateFailure.INSTALL)) }
    }

    fun canInstallPackages(): Boolean = updater.canRequestPackageInstalls()
    fun openInstallPermissionSettings() = updater.openInstallPermissionSettings()

    fun saveToken(token: String) {
        if (token.isBlank()) return showMessage(AppUpdateNotice.Failure(AppUpdateFailure.GITHUB_REQUEST))
        _state.value = _state.value.copy(isSavingToken = true, message = null)
        viewModelScope.launch {
            try {
                val user = api.getCurrentUser(token.trim())
                withContext(ioDispatcher) { auth.saveToken(token, user.login) }
                _state.value = _state.value.copy(
                    authenticatedUser = user.login,
                    isSavingToken = false,
                    message = AppUpdateNotice.Connected(user.login)
                )
            } catch (error: Throwable) {
                _state.value = _state.value.copy(isSavingToken = false, message = noticeFor(error))
            }
        }
    }

    fun signOut() {
        auth.signOut()
        _state.value = _state.value.copy(authenticatedUser = null, message = AppUpdateNotice.TokenRemoved)
    }

    suspend fun exportBackup(): String = repository.exportBackup()
    suspend fun importBackup(json: String): Result<Int> = repository.importBackup(json)

    private suspend fun releaseFor(owner: String, name: String, allowPreReleases: Boolean): GitHubRelease {
        return if (allowPreReleases) {
            api.getReleases(owner, name).firstOrNull { !it.draft }
                ?: throw AppUpdateWorkflowException(AppUpdateFailure.NO_RELEASE)
        } else {
            api.getLatestRelease(owner, name)
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            _state.value = _state.value.copy(installedApps = matcher.installedApps())
        }
    }

    private fun updatePreview(block: RepoPreview.() -> RepoPreview) {
        _state.value.preview?.let { _state.value = _state.value.copy(preview = it.block()) }
    }

    private fun showMessage(message: AppUpdateNotice) { _state.value = _state.value.copy(message = message) }

    private fun noticeFor(error: Throwable): AppUpdateNotice = when (error) {
        is AppUpdateWorkflowException -> AppUpdateNotice.Failure(error.reason)
        is GitHubApiException -> if (error.statusCode == 403 || error.statusCode == 429) {
            AppUpdateNotice.RateLimited(error.rateLimitResetEpochSeconds)
        } else if (error.statusCode == 404) {
            AppUpdateNotice.Failure(AppUpdateFailure.NOT_FOUND)
        } else AppUpdateNotice.Failure(AppUpdateFailure.GITHUB_REQUEST)
        else -> AppUpdateNotice.Failure(AppUpdateFailure.UNKNOWN)
    }

    private fun FeedsUiState.withDownload(name: String, value: UpdateDownloadState) =
        copy(downloadStates = downloadStates + (name to value))

    companion object {
        fun parseRepoQuery(query: String): Pair<String, String>? {
            val trimmed = query.trim().removeSuffix("/")
            val match = Regex("^(?:https?://)?(?:www\\.)?github\\.com/([^/]+)/([^/?#]+)(?:[/?#].*)?$", RegexOption.IGNORE_CASE)
                .matchEntire(trimmed)
            val parts = if (match != null) {
                match.groupValues[1] to match.groupValues[2].removeSuffix(".git")
            } else {
                val split = trimmed.removeSuffix(".git").split('/')
                if (split.size == 2 && !split[0].contains('.')) split[0] to split[1] else return null
            }
            val valid = Regex("[A-Za-z0-9_.-]+")
            return parts.takeIf { (owner, repo) -> owner.matches(valid) && repo.matches(valid) }
        }
    }
}

private class AppUpdateWorkflowException(val reason: AppUpdateFailure) : Exception()
