package dev.qtremors.acqua.feature

import android.app.Application
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.MainDependencies
import dev.qtremors.acqua.MainDispatcherRule
import dev.qtremors.acqua.data.network.HttpMediaClient
import dev.qtremors.acqua.data.updater.GitHubRepositoryService
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.MediaResolutionGateway
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.downloader.DownloadPhase
import dev.qtremors.acqua.downloader.DownloadQueueGateway
import dev.qtremors.acqua.downloader.DownloadQueueItem
import dev.qtremors.acqua.downloader.DownloadQueueSnapshot
import dev.qtremors.acqua.downloader.DownloadQueueState
import dev.qtremors.acqua.downloader.YtDlpCancellation
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions
import dev.qtremors.acqua.downloader.YtDlpMaintenanceGateway
import dev.qtremors.acqua.downloader.YtDlpMaintenanceResult
import dev.qtremors.acqua.feature.browser.BrowserViewModel
import dev.qtremors.acqua.feature.downloader.DownloaderViewModel
import dev.qtremors.acqua.feature.downloader.history.HistoryViewModel
import dev.qtremors.acqua.feature.settings.SettingsViewModel
import dev.qtremors.acqua.feature.updater.FeedsViewModel
import dev.qtremors.acqua.platform.permissions.DownloadPermissionDialogs
import dev.qtremors.acqua.platform.permissions.DownloadPermissionGate
import dev.qtremors.acqua.platform.permissions.DownloadPermissionPrompt
import dev.qtremors.acqua.ui.theme.AcquaTheme
import dev.qtremors.acqua.ui.theme.AccentColor
import dev.qtremors.acqua.ui.theme.ThemeMode
import dev.qtremors.acqua.ui.theme.ThemeState
import java.lang.reflect.Proxy
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardWorkflowTest {
    @get:Rule(order = 0) val main = MainDispatcherRule()
    @get:Rule(order = 1) val compose = createComposeRule(effectContext = main.dispatcher)
    private val models = ViewModelStore()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val graph = MainDependencies(context)
    private val queue = FixtureQueue()
    private val permissions = DownloadPermissionGate(SavedStateHandle())
    private var resolutions = 0
    private val resolver = object : MediaResolutionGateway {
        override suspend fun resolve(input: String, browserSessionsEnabled: Boolean, forceBrowserResolution: Boolean,
            engine: DownloadEngine, allowBrowserFallback: Boolean,
            browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>): List<ResolvedMedia> {
            resolutions++
            return listOf(ResolvedMedia(input, MediaKind.AUDIO, backend = MediaBackend.YT_DLP, title = "Fixture audio"))
        }
    }
    private lateinit var downloader: DownloaderViewModel
    private val route = mutableStateOf(DashboardRouteState("", null, 0, 0,
        ThemeState(themeMode = ThemeMode.LIGHT, accentColor = AccentColor.BLUE, vibrationsEnabled = false)))
    private val font = mutableStateOf(1f)
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    @After fun dispose() { models.clear() }

    @Test fun `fetch permission denial recovery progress and completion use the actual dashboard`() {
        content()
        compose.onNode(hasSetTextAction()).performClick().assertIsFocused().performTextReplacement("https://example.com/audio")
        compose.onNodeWithText("Fetch").performClick()
        compose.waitUntil(5000) { downloader.state.value.media != null }
        compose.onNodeWithText("Download audio").performClick()
        compose.onNodeWithText("Continue without notifications").assertIsDisplayed()
        assertTrue(queue.items.value.isEmpty())
        compose.onNodeWithText("Continue without notifications").performClick()
        compose.waitUntil(5000) { queue.items.value.size == 1 }
        compose.runOnIdle { queue.update(DownloadQueueState.RUNNING, 45f) }
        compose.waitUntil(5000) { downloader.state.value.downloadProgress == 45f }
        compose.onNodeWithTag("download-status").assertIsDisplayed()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo(0.45f, 0f..1f)), useUnmergedTree = true).assertExists()
        compose.runOnIdle { queue.update(DownloadQueueState.SUCCEEDED, 100f) }
        compose.waitUntil(5000) { downloader.state.value.saved }
        assertEquals(1, resolutions)
        compose.onNodeWithTag("download-status").assertDoesNotExist()
    }

    @Test fun `navigation settings and browser handoff restore without automatic fetching`() {
        val restore = content()
        nav("Browser").performClick().assertIsSelected()
        restore.emulateSavedInstanceStateRestore()
        nav("Browser").assertIsSelected()
        nav("Feeds").performClick().assertIsSelected()
        nav("Downloader").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Appearance"))
        compose.onNodeWithText("Appearance").assertIsDisplayed()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Appearance"))
        compose.onNodeWithText("Appearance").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { route.value = route.value.copy(urlHandoff = "https://example.com/handoff", downloadRequestRevision = 1) }
        compose.waitForIdle()
        compose.waitUntil(5000) { downloader.state.value.url == "https://example.com/handoff" }
        nav("Downloader").assertIsSelected()
        assertEquals(0, resolutions)
    }

    @Test @Config(qualifiers = "w1000dp-h1000dp-mdpi")
    fun `expanded navigation remains reachable with dark high contrast RTL and large text`() {
        route.value = route.value.copy(theme = route.value.theme.copy(themeMode = ThemeMode.DARK, highContrast = true))
        direction.value = LayoutDirection.Rtl; font.value = 2f
        content()
        java.io.File(System.getProperty("java.io.tmpdir"), "acqua-dashboard-expanded.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        nav("Browser").assertIsDisplayed().performClick().assertIsSelected()
        nav("Feeds").assertIsDisplayed().performClick().assertIsSelected()
        nav("Downloader").assertIsDisplayed().performClick().assertIsSelected()
        compose.onNodeWithText("Progress & History").performClick()
        compose.onNodeWithText("No history records found").assertExists()
    }

    private fun content(): StateRestorationTester {
        android.provider.Settings.Global.putFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        graph.history.clear(); graph.settings.setAutoUpdateYtDlp(false)
        downloader = DownloaderViewModel(graph.history, resolver, graph.settings, YtDlpCancellation {}, queue, SavedStateHandle(), main.dispatcher)
        val browser = BrowserViewModel(graph.settings, graph.savedWebsites, graph.browserData, graph.instagramSessions,
            graph.instagramResolver, SavedStateHandle())
        val settings = SettingsViewModel(graph.settings, object : YtDlpMaintenanceGateway {
            override suspend fun update(force: Boolean) = YtDlpMaintenanceResult("fixture", null, 0)
        })
        val history = HistoryViewModel(graph.history, SavedStateHandle(), main.dispatcher)
        val api = Proxy.newProxyInstance(GitHubRepositoryService::class.java.classLoader,
            arrayOf(GitHubRepositoryService::class.java)) { _, method, _ -> error("Unexpected GitHub call: ${method.name}") } as GitHubRepositoryService
        val feeds = FeedsViewModel(graph.trackedRepos, api, graph.githubAuth, graph.installedAppMatcher, graph.appUpdater)
        for ((name, model) in listOf("downloader" to downloader, "browser" to browser, "settings" to settings, "history" to history, "feeds" to feeds)) models.put(name, model)
        val client = HttpMediaClient(OkHttpClient.Builder().addInterceptor { error("Unexpected HTTP request") }.build())
        return StateRestorationTester(compose).also { restore -> restore.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font.value), LocalLayoutDirection provides direction.value) {
                AcquaTheme(route.value.theme) {
                    DashboardScreen(DashboardStateHolders(downloader, browser, history, settings, feeds),
                        DashboardServices(client, graph.fileActions, null, graph.appUpdater), route.value,
                        DashboardActions({}, { route.value = route.value.copy(urlHandoff = null) },
                            { _, _ -> error("Unexpected real browser resolution") },
                            { notify, action -> permissions.hold(notify, action); permissions.show(DownloadPermissionPrompt.NOTIFICATION_DENIED) },
                            { _, _, _ -> }, {}))
                    val prompt by permissions.prompt.collectAsState()
                    DownloadPermissionDialogs(prompt, permissions::dismiss, {}, {}, {}, permissions::runPendingWithoutNotifications)
                }
            }
        } }
    }

    private fun nav(label: String) = compose.onNode((hasText(label) or hasContentDescription(label)) and hasClickAction()
        and hasAnyAncestor(hasTestTag("dashboard-navigation")))
}

private class FixtureQueue : DownloadQueueGateway {
    val items = MutableStateFlow<List<DownloadQueueItem>>(emptyList())
    override fun enqueue(media: ResolvedMedia, outputMedia: ResolvedMedia, options: YtDlpDownloadOptions, sourceUrl: String): UUID {
        val id = UUID.randomUUID()
        items.value += DownloadQueueItem(id.toString(), DownloadQueueState.QUEUED, title = media.title, sourceUrl = sourceUrl)
        return id
    }
    override fun enqueueDirect(media: ResolvedMedia, index: Int, sourceUrl: String, itemCount: Int) = enqueue(media, media, YtDlpDownloadOptions(), sourceUrl)
    override fun observe(ids: List<UUID>) = items.map { all -> all.filter { item -> ids.any { it.toString() == item.id } } }
    override fun observeQueue() = items.map(::DownloadQueueSnapshot)
    override fun cancel(id: UUID) { items.value = items.value.map { if (it.id == id.toString()) it.copy(state = DownloadQueueState.CANCELLED) else it } }
    override fun rename(id: UUID, name: String) = false
    fun update(state: DownloadQueueState, progress: Float) {
        items.value = items.value.map { it.copy(state = state, progress = progress, phase = DownloadPhase.TRANSFER, downloadedBytes = progress.toLong(), totalBytes = 100) }
    }
}
