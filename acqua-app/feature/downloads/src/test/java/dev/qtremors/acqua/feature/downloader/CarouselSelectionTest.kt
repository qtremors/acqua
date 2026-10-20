package dev.qtremors.acqua.feature.downloader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.flow.flowOf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h840dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CarouselSelectionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var model: DownloaderViewModel
    private val queue = SelectionQueue()

    @Test fun `holding begins selection taps toggle and removing the last item restores download all`() {
        content()
        val originalBounds = centeredCard(0).fetchSemanticsNode().boundsInRoot
        centeredCard(0).performTouchInput { longClick() }
        assertEquals(setOf(0), model.state.value.selectedCollectionItems)
        compose.onNodeWithText("Download · 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Selection actions").assertDoesNotExist()
        centeredCard(0).assertIsSelected()
        assertEquals(originalBounds, centeredCard(0).fetchSemanticsNode().boundsInRoot)
        val indicator = compose.onAllNodesWithTag("preview-selection-indicator-0", useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.boundsInRoot }.minBy { abs(it.center.x - originalBounds.center.x) }
        assertEquals(32f, indicator.width, 1f)
        assertEquals(10f, originalBounds.right - indicator.right, 1f)
        assertEquals(10f, indicator.top - originalBounds.top, 1f)
        assertTrue(queue.requests.isEmpty())
        savePreview("acqua-carousel-selection-checkbox.png")
        compose.onNodeWithTag("media-preview-container").performTouchInput {
            down(Offset(width * 0.8f, centerY))
            moveBy(Offset(-300f, 0f), delayMillis = 100)
            advanceEventTime(200)
            up()
        }
        compose.onNodeWithText("2 / 10").assertIsDisplayed()
        centeredCard(1).performClick()
        assertEquals(setOf(0, 1), model.state.value.selectedCollectionItems)
        compose.onNodeWithText("Download · 2").assertIsDisplayed()
        centeredCard(1).performClick()
        assertEquals(setOf(0), model.state.value.selectedCollectionItems)
        compose.runOnIdle { model.selectPreviewItems(setOf(1)) }
        centeredCard(1).performClick()
        assertNull(model.state.value.selectedCollectionItems)
        compose.onNodeWithText("Download all (10)").assertIsDisplayed()
        compose.onNodeWithContentDescription("Selection actions").assertDoesNotExist()
        centeredCard(1).assertIsNotSelected()
        compose.onAllNodesWithTag("preview-selection-indicator-1", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText("All").assertDoesNotExist()
        compose.onNodeWithText("Cancel selection").assertDoesNotExist()
    }

    @Test fun `holding download opens the expressive menu and inversion selects only the other eight entries`() {
        content()
        compose.runOnIdle { model.selectPreviewItems(setOf(0, 2)) }
        compose.onNodeWithTag("preview-download-action").performTouchInput { longClick() }
        compose.onNodeWithText("Invert selection").assertIsDisplayed()
        assertTrue(queue.requests.isEmpty())
        saveMenu("acqua-carousel-selection-menu.png")
        compose.onNodeWithText("Invert selection").performClick()
        assertEquals(setOf(1, 3, 4, 5, 6, 7, 8, 9), model.state.value.selectedCollectionItems)
        compose.onNodeWithText("Download · 8").assertIsDisplayed()
        centeredCard(0).assertIsNotSelected()
        compose.onNodeWithContentDescription("Download item").performClick()
        compose.waitUntil { queue.requests.size == 1 }
        assertEquals("https://preview.test/landscape/0", queue.requests.single().url)
        compose.onNodeWithTag("preview-download-action").performTouchInput { longClick() }
        compose.onNodeWithText("Select all").performClick()
        compose.onNodeWithText("Download · 10").assertIsDisplayed()
        compose.onNodeWithTag("preview-download-action").performTouchInput { longClick() }
        compose.onNodeWithText("Invert selection").performClick()
        assertNull(model.state.value.selectedCollectionItems)
        compose.onNodeWithText("Download all (10)").assertIsDisplayed()
        centeredCard(0).performTouchInput { longClick() }
        compose.onNodeWithTag("preview-download-action").performTouchInput { longClick() }
        compose.onNodeWithText("Cancel selection").performClick()
        assertNull(model.state.value.selectedCollectionItems)
    }

    private fun centeredCard(index: Int): SemanticsNodeInteraction {
        val viewport = compose.onNodeWithTag("media-preview-container").fetchSemanticsNode().boundsInRoot
        val cards = compose.onAllNodesWithTag("media-preview-$index")
        val closest = cards.fetchSemanticsNodes().withIndex()
            .minBy { abs(it.value.boundsInRoot.center.x - viewport.center.x) }.index
        return cards[closest]
    }

    private fun content() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = previewClient()
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.width(360.dp).height(800.dp).background(MaterialTheme.colorScheme.background)) {
                    model = remember {
                        DownloaderViewModel(HistoryWriter { 0L }, NoResolution(), AppSettingsRepository(context),
                            YtDlpCancellation {}, queue, SavedStateHandle()).apply {
                            seedResolvedMedia("https://example.com/post", List(10) { index ->
                                ResolvedMedia("https://preview.test/${if (index % 2 == 0) "landscape" else "portrait"}/$index",
                                    MediaKind.IMAGE, width = if (index % 2 == 0) 360 else 240,
                                    height = if (index % 2 == 0) 240 else 360, title = "Item ${index + 1}")
                            })
                        }
                    }
                    DownloaderScreen(model, client, false, true, 0, { _, _ -> emptyList() },
                        { _, action -> action() }, {}, showHeaderCard = false)
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("loaded-media-preview-0", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun savePreview(name: String) = save(compose.onRoot(), name)
    private fun saveMenu(name: String) = save(compose.onNodeWithTag("preview-selection-menu"), name)
    private fun save(node: SemanticsNodeInteraction, name: String) {
        val bitmap = node.captureToImage().asAndroidBitmap()
        File(System.getProperty("java.io.tmpdir"), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun previewClient() = HttpMediaClient(OkHttpClient.Builder().addInterceptor { chain ->
        val portrait = chain.request().url.encodedPath.contains("portrait")
        val bitmap = Bitmap.createBitmap(if (portrait) 240 else 360, if (portrait) 360 else 240, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { shader = LinearGradient(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(),
            intArrayOf(0xff486b81.toInt(), 0xfff0c5a2.toInt(), 0xff274d46.toInt()), null, Shader.TileMode.CLAMP) }
        Canvas(bitmap).drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
        val bytes = ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
        bitmap.recycle()
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(bytes.toResponseBody("image/png".toMediaType())).build()
    }.build())

    private class NoResolution : MediaResolutionGateway {
        override suspend fun resolve(input: String, browserSessionsEnabled: Boolean, forceBrowserResolution: Boolean,
            engine: DownloadEngine, allowBrowserFallback: Boolean,
            browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>): List<ResolvedMedia> = error("Already fetched")
    }

    private class SelectionQueue : DownloadQueueGateway {
        val requests = mutableListOf<ResolvedMedia>()
        override fun observeQueue() = flowOf(DownloadQueueSnapshot())
        override fun observe(ids: List<UUID>) = flowOf(ids.map { DownloadQueueItem(it.toString(), DownloadQueueState.SUCCEEDED) })
        override fun enqueue(media: ResolvedMedia, outputMedia: ResolvedMedia, options: YtDlpDownloadOptions, sourceUrl: String): UUID =
            UUID.randomUUID().also { requests += media }
        override fun enqueueDirect(media: ResolvedMedia, index: Int, sourceUrl: String, itemCount: Int): UUID =
            UUID.randomUUID().also { requests += media }
        override fun cancel(id: UUID) = Unit
        override fun rename(id: UUID, name: String) = false
    }
}
