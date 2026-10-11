package dev.qtremors.acqua.feature.downloader

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.data.history.HistoryWriter
import dev.qtremors.acqua.data.network.HttpMediaClient
import dev.qtremors.acqua.data.network.MediaPreviewCache
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.MediaResolutionGateway
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.downloader.DownloadFailure
import dev.qtremors.acqua.downloader.DownloadQueueGateway
import dev.qtremors.acqua.downloader.DownloadQueueItem
import dev.qtremors.acqua.downloader.DownloadQueueSnapshot
import dev.qtremors.acqua.downloader.DownloadQueueState
import dev.qtremors.acqua.downloader.YtDlpCancellation
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions
import java.util.UUID
import java.io.File
import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloaderLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `unknown download errors do not report an updater failure`() {
        assertEquals(dev.qtremors.acqua.core.data.R.string.download_error_unknown, DownloadFailure.UNKNOWN.messageResource())
    }

    @Test
    fun `mixed image carousel fills the viewport with small gaps and loops in both directions`() {
        val items = listOf(
            ResolvedMedia("https://preview.test/portrait", MediaKind.IMAGE, width = 600, height = 1200),
            ResolvedMedia("https://preview.test/landscape", MediaKind.IMAGE, width = 1200, height = 600)
        )
        content(DownloaderUiState(media = items))
        waitForPreview(0)
        waitForPreview(1)
        val square = compose.onNodeWithTag("media-preview-container").fetchSemanticsNode().boundsInRoot
        assertEquals(360f, square.width, 1f)
        assertEquals(300f, square.height, 1f)
        val portrait = cardBounds(0)
        assertEquals(150f, portrait.width, 1f)
        assertEquals(300f, portrait.height, 1f)
        assertSmallHorizontalGaps()
        savePreview("acqua-image-carousel-layout.png")
        moveCarousel(forward = true)
        waitForPreview(1)
        val landscape = compose.onAllNodesWithTag("media-preview-1").fetchSemanticsNodes()
            .minBy { abs(it.boundsInRoot.center.x - square.center.x) }.layoutInfo.coordinates.size
        assertEquals(600, landscape.width)
        assertEquals(300, landscape.height)
        assertSmallHorizontalGaps()
        assertEquals(square, compose.onNodeWithTag("media-preview-container").fetchSemanticsNode().boundsInRoot)
        repeat(4) { turn ->
            moveCarousel(forward = true)
            compose.onNodeWithText("${turn % 2 + 1} / 2").assertIsDisplayed()
            assertSmallHorizontalGaps()
        }
        repeat(5) { turn ->
            moveCarousel(forward = false)
            compose.onNodeWithText("${turn % 2 + 1} / 2").assertIsDisplayed()
            assertSmallHorizontalGaps()
        }
    }

    @Test
    fun `loading image dimensions keeps the centered item and its download action aligned`() {
        val items = listOf(ResolvedMedia("https://preview.test/portrait", MediaKind.IMAGE),
            ResolvedMedia("https://preview.test/landscape", MediaKind.IMAGE))
        val downloaded = mutableListOf<String>()
        content(DownloaderUiState(media = items), onDownloadOne = { item, _, _, _ -> downloaded += item.url })
        waitForPreview(0)
        waitForPreview(1)
        val viewport = compose.onNodeWithTag("media-preview-container").fetchSemanticsNode().boundsInRoot
        assertEquals(viewport.center.x, cardBounds(0).center.x, 1f)
        compose.onNodeWithText("1 / 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Download item").performClick()
        moveCarousel(forward = true)
        compose.onNodeWithText("2 / 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Download item").performClick()
        assertEquals(items.map { it.url }, downloaded)
    }

    @Test
    fun `audio crop previews retain their aspect ratio when disabled and keep a stable viewport`() {
        val item = ResolvedMedia("https://example.com/audio", MediaKind.AUDIO, backend = MediaBackend.YT_DLP,
            width = 1280, height = 720, thumbnailUrl = "https://preview.test/audio-cover")
        val state = mutableStateOf(DownloaderUiState(media = listOf(item), downloadContentType = DownloadContentType.AUDIO))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cache = MediaPreviewCache(context, previewClient())
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.width(360.dp).height(600.dp).background(MaterialTheme.colorScheme.background)) {
                    MediaContentSection(state.value, cache, { _, _, _ -> }, { _, _, _, _ -> }, {}, {}, {}, false,
                        previewMaxHeight = 300.dp)
                }
            }
        }
        val square = compose.onNodeWithTag("media-preview-container").fetchSemanticsNode().boundsInRoot
        waitForPreview(0)
        val metadata = compose.onNodeWithTag("media-metadata").fetchSemanticsNode().boundsInRoot
        assertEquals(8f, metadata.top - square.bottom, 1f)
        compose.runOnIdle { state.value = state.value.copy(cropArtwork = false) }
        val card = cardBounds(0)
        assertEquals(16f / 9f, card.width / card.height, 0.02f)
        assertEquals(square, compose.onNodeWithTag("media-preview-container").fetchSemanticsNode().boundsInRoot)
        waitForPreview(0)
        savePreview("acqua-artwork-metadata-layout.png")
    }

    @Test
    fun `downloader ignores the parent top inset and progress stays below the audio preview`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val queue = LayoutQueue()
        lateinit var viewModel: DownloaderViewModel
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.width(360.dp).height(640.dp).background(MaterialTheme.colorScheme.background)) {
                    viewModel = androidx.compose.runtime.remember {
                        DownloaderViewModel(HistoryWriter { 0L }, NoResolution(), AppSettingsRepository(context),
                            YtDlpCancellation {}, queue, SavedStateHandle()).apply {
                            seedResolvedMedia("https://example.com/audio", listOf(ResolvedMedia(
                                "https://example.com/audio", MediaKind.AUDIO, backend = MediaBackend.YT_DLP,
                                thumbnailUrl = "https://preview.test/audio-cover")))
                        }
                    }
                    val client = androidx.compose.runtime.remember { previewClient() }
                    DownloaderScreen(viewModel, client, false, true, 0, { _, _ -> emptyList() },
                        { _, action -> action() }, {}, showHeaderCard = false,
                        contentPadding = PaddingValues(top = 32.dp))
                }
            }
        }
        val viewport = compose.onNodeWithTag("downloader-content").fetchSemanticsNode().boundsInRoot
        assertEquals(0f, viewport.top, 1f)
        compose.runOnIdle {
            queue.state.value = DownloadQueueSnapshot(listOf(DownloadQueueItem(UUID.randomUUID().toString(), DownloadQueueState.RUNNING)))
        }
        compose.onNodeWithTag("download-status").assertIsDisplayed()
        val preview = compose.onNodeWithTag("media-preview-container").fetchSemanticsNode().boundsInRoot
        val progress = compose.onNodeWithTag("download-status").fetchSemanticsNode().boundsInRoot
        val card = cardBounds(0)
        assertEquals(card.width, card.height, 1f)
        assertEquals(preview.height, card.height, 1f)
        assertTrue(preview.bottom <= progress.top)
        assertTrue(compose.onNodeWithTag("media-metadata").fetchSemanticsNode().boundsInRoot.bottom <= progress.top)
        waitForPreview(0)
        savePreview("acqua-audio-progress-layout.png")
    }

    private fun savePreview(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(System.getProperty("java.io.tmpdir"), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun waitForPreview(index: Int) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("loaded-media-preview-$index").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun cardBounds(index: Int): Rect {
        val viewport = compose.onNodeWithTag("media-preview-container").fetchSemanticsNode().boundsInRoot
        return compose.onAllNodesWithTag("media-preview-$index").fetchSemanticsNodes()
            .map { it.boundsInRoot }.minBy { abs(it.center.x - viewport.center.x) }
    }

    private fun assertSmallHorizontalGaps() {
        val viewport = compose.onNodeWithTag("media-preview-container").fetchSemanticsNode().boundsInRoot
        val bounds = (0..1).flatMap { index ->
            compose.onAllNodesWithTag("media-preview-$index").fetchSemanticsNodes().map { it.boundsInRoot }
        }.filter { it.width > 0f }.sortedBy { it.left }
        assertTrue(bounds.first().left - viewport.left <= 8f)
        assertTrue(viewport.right - bounds.last().right <= 8f)
        bounds.zipWithNext().forEach { (left, right) -> assertEquals(8f, right.left - left.right, 1f) }
    }

    private fun moveCarousel(forward: Boolean) {
        compose.onNodeWithTag("media-preview-container").performTouchInput {
            down(Offset(if (forward) width * 0.8f else width * 0.2f, centerY))
            moveBy(Offset(if (forward) -300f else 300f, 0f), delayMillis = 800)
            advanceEventTime(200)
            up()
        }
    }

    private fun content(state: DownloaderUiState,
        onDownloadOne: (ResolvedMedia, Int, Int, Int) -> Unit = { _, _, _, _ -> }) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cache = MediaPreviewCache(context, previewClient())
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.width(360.dp).height(600.dp).background(MaterialTheme.colorScheme.background)) {
                    MediaContentSection(state, cache, { _, _, _ -> }, onDownloadOne, {}, {}, {}, false,
                        previewMaxHeight = 300.dp)
                }
            }
        }
    }

    private fun previewClient(): HttpMediaClient = HttpMediaClient(OkHttpClient.Builder().addInterceptor { chain ->
        val (width, height) = when (chain.request().url.encodedPath) {
            "/portrait" -> 600 to 1200
            "/audio-cover" -> 1280 to 720
            else -> 1200 to 600
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) for (x in 0 until width) {
            bitmap.setPixel(x, y, Color.rgb(x * 255 / width, y * 255 / height, if ((x / 40 + y / 40) % 2 == 0) 80 else 180))
        }
        val bytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
        bitmap.recycle()
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(bytes.toResponseBody("image/png".toMediaType())).build()
    }.build())

    private class NoResolution : MediaResolutionGateway {
        override suspend fun resolve(input: String, browserSessionsEnabled: Boolean, forceBrowserResolution: Boolean,
            engine: DownloadEngine, allowBrowserFallback: Boolean,
            browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>): List<ResolvedMedia> = error("Already resolved")
    }

    private class LayoutQueue : DownloadQueueGateway {
        val state = MutableStateFlow(DownloadQueueSnapshot())
        override fun observeQueue(): Flow<DownloadQueueSnapshot> = state
        override fun observe(ids: List<UUID>): Flow<List<DownloadQueueItem>> = flowOf(emptyList())
        override fun enqueue(media: ResolvedMedia, outputMedia: ResolvedMedia, options: YtDlpDownloadOptions, sourceUrl: String): UUID = error("No download expected")
        override fun enqueueDirect(media: ResolvedMedia, index: Int, sourceUrl: String, itemCount: Int): UUID = error("No download expected")
        override fun cancel(id: UUID) = Unit
        override fun rename(id: UUID, name: String): Boolean = false
    }
}
