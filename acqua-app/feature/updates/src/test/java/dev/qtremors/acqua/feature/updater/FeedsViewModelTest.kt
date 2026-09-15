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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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

        override suspend fun exportBackup(): String = "{}"

        override suspend fun importBackup(jsonString: String): Result<Int> = Result.success(0)
    }

    private class FakeGitHubService : GitHubRepositoryService {
        var failure: Throwable? = null
        var delayFirstRepository = false
        val completedRepoLookups = mutableListOf<String>()

        override suspend fun getRepoInfo(owner: String, repo: String): GitHubRepositoryInfo {
            failure?.let { throw it }
            if (delayFirstRepository && repo == "slow") delay(1_000L)
            completedRepoLookups += repo
            return GitHubRepositoryInfo(
                name = repo,
                full_name = "$owner/$repo",
                html_url = "https://github.com/$owner/$repo",
                owner = GitHubOwner(owner)
            )
        }

        override suspend fun getLatestRelease(owner: String, repo: String): GitHubRelease {
            failure?.let { throw it }
            return GitHubRelease(
                tag_name = "v0.2.4",
                html_url = "https://github.com/$owner/$repo/releases/tag/v0.2.4",
                assets = listOf(
                    GitHubAsset(
                        name = "Acqua.apk",
                        browser_download_url = "https://downloads.example/Acqua.apk",
                        url = "https://api.example/Acqua.apk"
                    )
                )
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
        override suspend fun installedApps(): List<InstalledApp> = emptyList()
        override suspend fun findBestMatch(repoName: String): InstalledApp? = null
        override fun findBestMatch(repoName: String, apps: List<InstalledApp>): InstalledApp? = null
        override fun installedVersion(packageName: String): InstalledApp? = null
    }

    private class FakeUpdateGateway : AppUpdateGateway {
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
