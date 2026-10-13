package dev.qtremors.acqua.feature.updater

import app.cash.turbine.test
import dev.qtremors.acqua.MainDispatcherRule
import dev.qtremors.acqua.data.updater.AppUpdateGateway
import dev.qtremors.acqua.data.updater.GitHubApiException
import dev.qtremors.acqua.data.updater.GitHubAsset
import dev.qtremors.acqua.data.updater.GitHubAuthStore
import dev.qtremors.acqua.data.updater.GitHubOwner
import dev.qtremors.acqua.data.updater.GitHubRelease
import dev.qtremors.acqua.data.updater.GitHubRepositoryInfo
import dev.qtremors.acqua.data.updater.GitHubRepositoryService
import dev.qtremors.acqua.data.updater.GitHubUser
import dev.qtremors.acqua.data.updater.InstalledApp
import dev.qtremors.acqua.data.updater.InstalledAppCatalog
import dev.qtremors.acqua.data.updater.TrackedRepo
import dev.qtremors.acqua.data.updater.TrackedRepoStore
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedsViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `search success produces preview through deterministic gateways`() = runTest {
        val store = FakeTrackedRepoStore()
        val api = FakeGitHubService()
        val viewModel = viewModel(store = store, api = api)

        viewModel.state.test {
            assertTrue(awaitItem().isLoading)
            advanceUntilIdle()
            assertFalse(expectMostRecentItem().isLoading)

            viewModel.searchRepository("qtremors/acqua")
            assertTrue(expectMostRecentItem().isSearching)
            advanceUntilIdle()
            val result = expectMostRecentItem()

            assertFalse(result.isSearching)
            assertEquals("qtremors/acqua", result.preview?.repository?.full_name)
            assertEquals(listOf("Acqua.apk"), result.preview?.apkAssets?.map(GitHubAsset::name))
            assertNull(result.message)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, store.ensureCalls)
    }

    @Test
    fun `new search cancels stale result before it can overwrite current preview`() = runTest {
        val api = FakeGitHubService().apply { delayFirstRepository = true }
        val viewModel = viewModel(api = api)
        advanceUntilIdle()

        viewModel.searchRepository("owner/slow")
        viewModel.searchRepository("owner/current")
        advanceUntilIdle()

        assertEquals("owner/current", viewModel.state.value.preview?.repository?.full_name)
        assertEquals(listOf("current"), api.completedRepoLookups)
    }

    @Test
    fun `rate limit and invalid input map to typed notices without exception text`() = runTest {
        val api = FakeGitHubService().apply {
            failure = GitHubApiException(429, 999L)
        }
        val viewModel = viewModel(api = api)
        advanceUntilIdle()

        viewModel.searchRepository("not a repository")
        assertEquals(
            AppUpdateNotice.Failure(AppUpdateFailure.INVALID_REPOSITORY),
            viewModel.state.value.message
        )

        viewModel.searchRepository("owner/repo")
        advanceUntilIdle()
        assertEquals(AppUpdateNotice.RateLimited(999L), viewModel.state.value.message)
    }

    @Test
    fun `download progress completes once and a second action requests install`() = runTest {
        val updater = FakeUpdateGateway()
        val viewModel = viewModel(updater = updater)
        val repo = trackedRepo()
        advanceUntilIdle()

        viewModel.download(repo)
        advanceUntilIdle()

        val completed = viewModel.state.value.downloadStates[repo.fullName]
        assertTrue(completed is UpdateDownloadState.Downloaded)
        assertEquals(1, updater.downloadCalls)
        assertTrue(updater.installedFiles.isEmpty())

        viewModel.download(repo)
        assertEquals(listOf(updater.downloadedFile), updater.installedFiles)
        assertEquals(1, updater.downloadCalls)
    }

    @Test fun `downloads refresh trusted asset metadata instead of using imported endpoints`() = runTest {
        var lookedUp = false
        val api = FakeGitHubService().apply { beforeRelease = { lookedUp = true } }
        val updater = FakeUpdateGateway()
        val viewModel = viewModel(api = api, updater = updater,
            auth = FakeAuthStore().apply { token = "dummy-token" })
        val imported = trackedRepo().copy(downloadUrl = "https://untrusted.example/evil.apk",
            downloadApiUrl = "https://untrusted.example/asset", selectedApkName = "Acqua.apk")
        advanceUntilIdle()
        viewModel.download(imported)
        advanceUntilIdle()
        assertTrue(lookedUp)
        assertEquals("https://api.github.com/repos/owner/repo/releases/assets/123", updater.requestedUrl)
        assertEquals("dummy-token", updater.requestedToken)
        assertEquals(1, updater.downloadCalls)
    }

    private fun viewModel(
        store: FakeTrackedRepoStore = FakeTrackedRepoStore(),
        api: FakeGitHubService = FakeGitHubService(),
        auth: FakeAuthStore = FakeAuthStore(),
        matcher: FakeInstalledApps = FakeInstalledApps(),
        updater: FakeUpdateGateway = FakeUpdateGateway()
    ) = FeedsViewModel(
        store,
        api,
        auth,
        matcher,
        updater,
        mainDispatcherRule.dispatcher
    )

    @Test
    fun `returning from installer refreshes version and removes the completed install action`() = runTest {
        val store = FakeTrackedRepoStore()
        val repo = trackedRepo().copy(latestTagName = "v0.3.1", isUpdateAvailable = true)
        store.trackedRepos.value = listOf(repo)
        val matcher = FakeInstalledApps()
        val api = FakeGitHubService()
        val viewModel = viewModel(store = store, matcher = matcher, api = api)
        advanceUntilIdle()
        viewModel.download(repo)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.downloadStates[repo.fullName] is UpdateDownloadState.Downloaded)

        matcher.app = InstalledApp("dev.qtremors.acqua", "Acqua", "0.3.1", 31)
        viewModel.refreshInstalledStatus()
        advanceUntilIdle()
        val refreshed = store.trackedRepos.value.single()
        assertEquals("0.3.1", refreshed.installedVersionName)
        assertFalse(refreshed.isUpdateAvailable)
        assertEquals(UpdateDownloadState.Idle, viewModel.state.value.downloadStates[repo.fullName])
        assertTrue(api.completedRepoLookups.isEmpty())
    }

    @Test
    fun `refresh updates a renamed and transferred repository using its stored identity`() = runTest {
        val store = FakeTrackedRepoStore()
        val original = trackedRepo()
        store.trackedRepos.value = listOf(original)
        val api = FakeGitHubService().apply {
            repositoryInfo = GitHubRepositoryInfo("acqua-renamed", "new-owner/acqua-renamed",
                html_url = "https://github.com/new-owner/acqua-renamed", owner = GitHubOwner("new-owner"))
        }
        val viewModel = viewModel(store = store, api = api)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.trackedRepos.collect {} }
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        val refreshed = store.trackedRepos.value.single()
        assertEquals("new-owner/acqua-renamed", refreshed.fullName)
        assertEquals("new-owner", refreshed.owner)
        assertEquals("acqua-renamed", refreshed.name)
        assertEquals("v0.2.4", refreshed.latestTagName)
        assertEquals(original.addedAt, refreshed.addedAt)
        assertTrue(refreshed.lastCheckedAt > 0L)
    }

    @Test
    fun `refresh selects the current APK after configuration changes during the request`() = runTest {
        val store = FakeTrackedRepoStore()
        store.trackedRepos.value = listOf(trackedRepo())
        val arm64 = GitHubAsset("Acqua-arm64.apk", "https://downloads.example/Acqua-arm64.apk",
            size = 1234L, url = "https://api.example/Acqua-arm64.apk")
        val api = FakeGitHubService().apply {
            extraAssets = listOf(arm64)
            beforeRelease = {
                store.trackedRepos.value = listOf(store.trackedRepos.value.single().copy(
                    selectedApkName = arm64.name, notificationsEnabled = false,
                    downloadUrl = arm64.browser_download_url, downloadApiUrl = arm64.url, apkSizeBytes = arm64.size))
            }
        }
        val viewModel = viewModel(store = store, api = api)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.trackedRepos.collect {} }
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        val refreshed = store.trackedRepos.value.single()
        assertEquals(arm64.name, refreshed.selectedApkName)
        assertEquals(arm64.browser_download_url, refreshed.downloadUrl)
        assertEquals(arm64.url, refreshed.downloadApiUrl)
        assertEquals(arm64.size, refreshed.apkSizeBytes)
        assertFalse(refreshed.notificationsEnabled)
    }

    @Test
    fun `refresh preserves release data when prerelease preferences change during the request`() = runTest {
        val store = FakeTrackedRepoStore()
        val latestTag = "v0.3.1-beta"
        store.trackedRepos.value = listOf(trackedRepo())
        val api = FakeGitHubService().apply {
            beforeRelease = {
                store.trackedRepos.value = listOf(store.trackedRepos.value.single().copy(
                    allowPreReleases = true, latestTagName = latestTag))
            }
        }
        val viewModel = viewModel(store = store, api = api)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.trackedRepos.collect {} }
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()
        assertTrue(store.trackedRepos.value.single().allowPreReleases)
        assertEquals(latestTag, store.trackedRepos.value.single().latestTagName)
    }

    @Test
    fun `refresh does not restore a repository removed during the request`() = runTest {
        val store = FakeTrackedRepoStore()
        store.trackedRepos.value = listOf(trackedRepo())
        val api = FakeGitHubService().apply { beforeRelease = { store.trackedRepos.value = emptyList() } }
        val viewModel = viewModel(store = store, api = api)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.trackedRepos.collect {} }
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()
        assertTrue(store.trackedRepos.value.isEmpty())
    }

    @Test
    fun `installed status keeps package edits made before the atomic write`() = runTest {
        val store = FakeTrackedRepoStore()
        store.trackedRepos.value = listOf(trackedRepo())
        val matcher = FakeInstalledApps().apply {
            app = InstalledApp("other.package", "Other app", "0.3.0", 30)
        }
        val viewModel = viewModel(store = store, matcher = matcher)
        advanceUntilIdle()
        store.beforeUpdate = {
            store.trackedRepos.value = listOf(store.trackedRepos.value.single().copy(
                mappedPackageName = "other.package", mappedAppName = "Other app", autoMatchInstalledApp = false))
        }

        viewModel.refreshInstalledStatus()
        advanceUntilIdle()

        val saved = store.trackedRepos.value.single()
        assertEquals("other.package", saved.mappedPackageName)
        assertEquals("Other app", saved.mappedAppName)
        assertEquals("0.3.0", saved.installedVersionName)
        assertFalse(saved.autoMatchInstalledApp)
    }

    @Test
    fun `installed status does not restore a cleared mapping or a removed repository`() = runTest {
        val store = FakeTrackedRepoStore()
        val original = trackedRepo()
        store.trackedRepos.value = listOf(original)
        val matcher = FakeInstalledApps().apply {
            app = InstalledApp("dev.qtremors.acqua", "Acqua", "0.3.0", 30)
        }
        val viewModel = viewModel(store = store, matcher = matcher)
        advanceUntilIdle()
        store.beforeUpdate = {
            store.trackedRepos.value = listOf(original.copy(
                mappedPackageName = null, mappedAppName = null, autoMatchInstalledApp = false))
        }
        viewModel.refreshInstalledStatus()
        advanceUntilIdle()
        assertNull(store.trackedRepos.value.single().mappedPackageName)
        assertNull(store.trackedRepos.value.single().installedVersionName)

        store.beforeUpdate = { store.trackedRepos.value = emptyList() }
        viewModel.refreshInstalledStatus()
        advanceUntilIdle()
        assertTrue(store.trackedRepos.value.isEmpty())
    }

    @Test
    fun `import during release refresh keeps imported membership and package choices`() = runTest {
        val store = FakeTrackedRepoStore()
        val original = trackedRepo()
        store.trackedRepos.value = listOf(original)
        val arm64 = GitHubAsset("arm64.apk", "https://downloads.example/arm64.apk", size = 123)
        store.importedRepos = listOf(
            original.copy(selectedApkName = arm64.name, mappedPackageName = "imported.package",
                autoMatchInstalledApp = false, notificationsEnabled = false),
            original.copy(owner = "new", name = "app", fullName = "new/app")
        )
        val api = FakeGitHubService().apply { extraAssets = listOf(arm64) }
        val viewModel = viewModel(store = store, api = api)
        api.beforeRelease = { viewModel.importBackup("backup").getOrThrow() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.trackedRepos.collect {} }
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(2, store.trackedRepos.value.size)
        val saved = store.trackedRepos.value.single { it.fullName == original.fullName }
        assertEquals("imported.package", saved.mappedPackageName)
        assertEquals(arm64.name, saved.selectedApkName)
        assertEquals(arm64.browser_download_url, saved.downloadUrl)
        assertFalse(saved.autoMatchInstalledApp)
        assertFalse(saved.notificationsEnabled)
        assertTrue(store.trackedRepos.value.any { it.fullName == "new/app" })
    }

    private fun trackedRepo() = TrackedRepo(
        owner = "qtremors",
        name = "acqua",
        fullName = "qtremors/acqua",
        downloadUrl = "https://downloads.example/Acqua.apk",
        downloadApiUrl = "https://api.example/Acqua.apk",
        selectedApkName = "Acqua.apk",
        mappedPackageName = "dev.qtremors.acqua",
        installedVersionName = "0.2.3"
    )

    private class FakeTrackedRepoStore : TrackedRepoStore {
        override val trackedRepos = MutableStateFlow<List<TrackedRepo>>(emptyList())
        var ensureCalls = 0
        var beforeUpdate: (() -> Unit)? = null
        var importedRepos: List<TrackedRepo> = emptyList()

        override suspend fun addOrUpdateRepo(repo: TrackedRepo) {
            trackedRepos.value = trackedRepos.value.filterNot { it.fullName == repo.fullName } + repo
        }

        override suspend fun ensureDefaultRepos(): Boolean {
            ensureCalls += 1
            return false
        }

        override suspend fun removeRepo(fullName: String) {
            trackedRepos.value = trackedRepos.value.filterNot { it.fullName == fullName }
        }

        override suspend fun updateAll(repos: List<TrackedRepo>) {
            trackedRepos.value = repos
        }

        override suspend fun updateRepo(fullName: String, transform: (TrackedRepo) -> TrackedRepo) {
            beforeUpdate?.also { beforeUpdate = null }?.invoke()
            trackedRepos.value = trackedRepos.value.map { if (it.fullName == fullName) transform(it) else it }
        }

        override suspend fun exportBackup(): String = "{}"

        override suspend fun importBackup(jsonString: String): Result<Int> {
            trackedRepos.value = (trackedRepos.value + importedRepos).associateBy { it.fullName }.values.toList()
            return Result.success(importedRepos.size)
        }
    }

    private class FakeGitHubService : GitHubRepositoryService {
        var failure: Throwable? = null
        var delayFirstRepository = false
        var repositoryInfo: GitHubRepositoryInfo? = null
        var beforeRelease: (suspend () -> Unit)? = null
        var extraAssets: List<GitHubAsset> = emptyList()
        val completedRepoLookups = mutableListOf<String>()

        override suspend fun getRepoInfo(owner: String, repo: String): GitHubRepositoryInfo {
            failure?.let { throw it }
            if (delayFirstRepository && repo == "slow") delay(1_000L)
            completedRepoLookups += repo
            return repositoryInfo ?: GitHubRepositoryInfo(
                name = repo,
                full_name = "$owner/$repo",
                html_url = "https://github.com/$owner/$repo",
                owner = GitHubOwner(owner)
            )
        }

        override suspend fun getLatestRelease(owner: String, repo: String): GitHubRelease {
            beforeRelease?.invoke()
            failure?.let { throw it }
            return GitHubRelease(
                tag_name = "v0.2.4",
                html_url = "https://github.com/$owner/$repo/releases/tag/v0.2.4",
                assets = listOf(
                    GitHubAsset(
                        name = "Acqua.apk",
                        browser_download_url = "https://downloads.example/Acqua.apk",
                        url = "https://api.github.com/repos/owner/repo/releases/assets/123"
                    )
                ) + extraAssets
            )
        }

        override suspend fun getReleases(owner: String, repo: String): List<GitHubRelease> =
            listOf(getLatestRelease(owner, repo))

        override suspend fun getReadme(owner: String, repo: String): String = "README"

        override suspend fun getCurrentUser(token: String): GitHubUser = GitHubUser("tester")
    }

    private class FakeAuthStore : GitHubAuthStore {
        override var token: String? = null
        override var username: String? = null

        override fun saveToken(token: String, username: String) {
            this.token = token
            this.username = username
        }

        override fun signOut() {
            token = null
            username = null
        }
    }

    private class FakeInstalledApps : InstalledAppCatalog {
        var app: InstalledApp? = null
        override suspend fun installedApps(): List<InstalledApp> = listOfNotNull(app)
        override suspend fun findBestMatch(repoName: String): InstalledApp? = null
        override fun findBestMatch(repoName: String, apps: List<InstalledApp>): InstalledApp? = null
        override fun installedVersion(packageName: String): InstalledApp? = app?.takeIf { it.packageName == packageName }
    }

    private class FakeUpdateGateway : AppUpdateGateway {
        var requestedUrl: String? = null
        var requestedToken: String? = null
        val downloadedFile = File("Acqua-test.apk")
        var downloadCalls = 0
        val installedFiles = mutableListOf<File>()

        override suspend fun downloadUpdate(
            downloadUrl: String,
            fileName: String,
            expectedSizeBytes: Long,
            expectedVersionCode: Int?,
            expectedPackageName: String?,
            requireNewerThanInstalled: Boolean,
            authorizationToken: String?,
            onProgress: (Long, Long, Int) -> Unit
        ): Result<File> {
            downloadCalls += 1
            requestedUrl = downloadUrl
            requestedToken = authorizationToken
            onProgress(50L, 100L, 50)
            onProgress(100L, 100L, 100)
            return Result.success(downloadedFile)
        }

        override fun canRequestPackageInstalls(): Boolean = false

        override fun openInstallPermissionSettings() = Unit

        override fun installApk(
            apkFile: File,
            expectedPackageName: String?,
            requireNewerThanInstalled: Boolean
        ): Result<Unit> {
            installedFiles += apkFile
            return Result.success(Unit)
        }
    }
}
