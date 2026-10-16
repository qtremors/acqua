package dev.qtremors.acqua.feature.downloader

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.data.history.HistoryWriter
import dev.qtremors.acqua.data.network.HttpMediaClient
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.MediaResolutionGateway
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.downloader.DownloadQueueGateway
import dev.qtremors.acqua.downloader.DownloadQueueItem
import dev.qtremors.acqua.downloader.DownloadQueueSnapshot
import dev.qtremors.acqua.downloader.DownloadQueueState
import dev.qtremors.acqua.downloader.YtDlpCancellation
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions
import dev.qtremors.acqua.platform.permissions.DownloadPermissionGate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class QuickConfigurationTest {
    @get:Rule val compose = createComposeRule()
    private val models = ViewModelStore()
    private val queue = QuickQueue()
    private var inspections = 0
    private var permissionAction: (() -> Unit)? = null
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = AppSettingsRepository(context).apply { setLastDownloadEngine(DownloadEngine.ACQUA) }
    private val gate = DownloadPermissionGate(SavedStateHandle())
    private val model by lazy { DownloaderViewModel(HistoryWriter { 1 }, object : MediaResolutionGateway {
        override suspend fun resolve(input: String, browserSessionsEnabled: Boolean, forceBrowserResolution: Boolean,
            engine: DownloadEngine, allowBrowserFallback: Boolean, browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>): List<ResolvedMedia> {
            inspections++
            return listOf(ResolvedMedia(input, MediaKind.AUDIO))
        }
    }, settings, YtDlpCancellation {}, queue, SavedStateHandle(), Dispatchers.Main.immediate).also { models.put("quick", it) } }
    private val http = HttpMediaClient(okhttp3.OkHttpClient.Builder().addInterceptor { error("Unexpected HTTP request") }.build())
    private val source = mutableStateOf("https://example.com/audio")

    @After fun cleanup() { models.clear() }

    @Test fun `quick intake opens configuration without inspection or download and stays dismissed after recreation`() {
        model.updateUrl(source.value)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { screen() } }
        compose.onNodeWithTag("download-config-sheet").assertIsDisplayed()
        compose.onNodeWithText(source.value).assertIsDisplayed()
        assertEquals(0, inspections)
        assertEquals(0, queue.accepted)
        compose.onNodeWithContentDescription("Close").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("download-config-sheet").assertDoesNotExist()
        assertEquals(0, inspections)
        source.value = "https://example.com/next"
        compose.onNodeWithTag("download-config-sheet").assertIsDisplayed()
        assertEquals(0, queue.accepted)
    }

    @Test fun `inspection and download remain explicit and denied permission never queues a request`() {
        model.updateUrl(source.value)
        compose.setContent { MaterialTheme { screen() } }
        compose.onNodeWithTag("download-config-action").performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertNotNull("Inspection count=$inspections; state=${model.state.value}", model.state.value.media)
        assertEquals(1, inspections)
        assertEquals(0, queue.accepted)
        compose.onNodeWithText(context.getString(dev.qtremors.acqua.core.data.R.string.download_audio)).performClick()
        assertNotNull(permissionAction)
        assertEquals(0, queue.accepted)
        val allowed = requireNotNull(permissionAction)
        compose.runOnIdle { allowed(); allowed() }
        compose.waitUntil(3_000) { queue.accepted == 1 }
        compose.waitForIdle()
        assertEquals(1, queue.accepted)
        assertEquals(1, inspections)
    }

    @androidx.compose.runtime.Composable
    private fun screen() {
        androidx.compose.runtime.LaunchedEffect(source.value) {
            if (model.state.value.url != source.value) model.updateUrl(source.value)
        }
        DownloaderScreen(model, http, false, true, 0, { _, _ -> error("No browser fallback") },
            { needsNotification, action ->
                gate.hold(needsNotification, action)
                permissionAction = gate::runPendingWithoutNotifications
            }, {}, showLinkEditor = false, initialConfigurationKey = source.value)
    }

    private class QuickQueue : DownloadQueueGateway {
        var accepted = 0
        override fun enqueue(media: ResolvedMedia, outputMedia: ResolvedMedia, options: YtDlpDownloadOptions, sourceUrl: String): UUID = error("Expected direct media")
        override fun enqueueDirect(media: ResolvedMedia, index: Int, sourceUrl: String, itemCount: Int) = UUID.randomUUID().also { accepted++ }
        override fun observe(ids: List<UUID>) = flowOf(ids.map { DownloadQueueItem(it.toString(), DownloadQueueState.SUCCEEDED) })
        override fun observeQueue() = flowOf(DownloadQueueSnapshot())
        override fun cancel(id: UUID) {}
        override fun rename(id: UUID, name: String) = false
    }
}
