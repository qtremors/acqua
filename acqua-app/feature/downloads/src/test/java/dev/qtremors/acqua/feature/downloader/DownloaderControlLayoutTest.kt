package dev.qtremors.acqua.feature.downloader

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.ui.components.ExpressiveSegmentedButtonRow
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloaderControlLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `pasted batches offer a batch action and disable it while queueing`() {
        val state = mutableStateOf(DownloaderUiState(url = "https://example.com/one\nhttps://example.com/two"))
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp)) {
                    BottomControlsSection(state.value, true, true, {}, {},
                        { error("Unexpected single link inspection") }, { clicks++ })
                }
            }
        }
        compose.onNodeWithText("Download links").assertIsDisplayed().performClick()
        assertEquals(1, clicks)
        compose.runOnIdle { state.value = state.value.copy(isBatchRunning = true) }
        compose.onNodeWithText("Queueing links…").assertIsDisplayed()
        compose.onNodeWithText("Queueing links…").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `tabs fit their labels and changing badges across selection and font sizes`() {
        val selected = mutableStateOf(0)
        val count = mutableStateOf(1)
        val fontScale = mutableStateOf(1f)
        val labels = listOf("Downloader", "Progress & History")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale.value)) {
                MaterialTheme {
                    Box(Modifier.width(360.dp)) {
                        ExpressiveSegmentedButtonRow(
                            items = labels,
                            selectedIndex = selected.value,
                            onSelect = { selected.value = it },
                            label = { it },
                            leadingIcon = { label, _ ->
                                Icon(if (label == labels[0]) Icons.Filled.Download else Icons.Filled.History,
                                    null, Modifier.size(18.dp))
                            },
                            trailingBadge = { label, _ ->
                                if (label == labels[1]) Badge { Text(count.value.toString()) }
                            }
                        )
                    }
                }
            }
        }
        savePreview("acqua-downloader-tab-sizing.png")
        labels.forEach(::assertFullText)
        val initialHeight = compose.onNodeWithText(labels[0]).fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { count.value = 1000 }
        labels.forEach(::assertFullText)
        assertFullText("1000")
        compose.onNodeWithText(labels[1]).performClick()
        labels.forEach(::assertFullText)
        compose.runOnIdle { fontScale.value = 2f }
        savePreview("acqua-downloader-tab-sizing.png")
        labels.forEach(::assertFullText)
        assertFullText("1000")
        assertTrue(compose.onNodeWithText(labels[0]).fetchSemanticsNode().boundsInRoot.height > initialHeight)
        compose.onNodeWithText(labels[0]).performClick()
        labels.forEach(::assertFullText)
        savePreview("acqua-downloader-tab-sizing.png")
    }

    @Test
    fun `engine and action controls resize for resolving downloading and large text`() {
        val state = mutableStateOf(DownloaderUiState(validity = LinkValidity.VALID,
            downloadEngine = DownloadEngine.ACQUA))
        val fontScale = mutableStateOf(1f)
        val width = mutableStateOf(360.dp)
        var fetches = 0
        var downloads = 0
        var configOpens = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale.value)) {
                MaterialTheme {
                    Box(Modifier.width(width.value).height(600.dp)) {
                        BottomControlsSection(state.value, false, true, {}, { configOpens++ },
                            { fetches++ }, { downloads++ })
                    }
                }
            }
        }
        savePreview("acqua-downloader-control-sizing.png")
        assertFullText("Acqua")
        assertFullText("Fetch")
        val fetchWidth = compose.onNodeWithText("Fetch").fetchSemanticsNode().boundsInRoot.width
        compose.onNodeWithText("Fetch").performClick()
        compose.onNodeWithText("Acqua").performClick()
        assertEquals(1, fetches)
        assertEquals(1, configOpens)
        compose.runOnIdle { state.value = state.value.copy(isResolving = true) }
        assertFullText("Resolving media")
        assertTrue(compose.onNodeWithText("Resolving media").fetchSemanticsNode().boundsInRoot.width > fetchWidth)
        compose.runOnIdle {
            state.value = state.value.copy(isResolving = false, downloadEngine = DownloadEngine.YT_DLP,
                downloadContentType = DownloadContentType.AUDIO,
                media = List(2) { ResolvedMedia("https://example.com/$it", MediaKind.IMAGE) })
        }
        assertFullText("yt-dlp · Audio (Original)")
        assertFullText("Download all (2)")
        compose.onNodeWithText("Download all (2)").performClick()
        assertEquals(1, downloads)
        compose.runOnIdle { fontScale.value = 2f; width.value = 320.dp }
        savePreview("acqua-downloader-control-sizing.png")
        assertFullText("yt-dlp · Audio (Original)")
        assertFullText("Download all (2)")
        val engine = compose.onNodeWithText("yt-dlp · Audio (Original)").fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithText("Download all (2)").fetchSemanticsNode().boundsInRoot
        assertEquals(engine.top, action.top, 1f)
        assertTrue(action.left > engine.right)
        savePreview("acqua-downloader-control-sizing.png")
        compose.runOnIdle { state.value = state.value.copy(isSaving = true, savingIndex = 1) }
        assertFullText("1/2 downloaded")
        compose.runOnIdle {
            state.value = state.value.copy(isSaving = false, saved = true, lastDownloadedKind = MediaKind.IMAGE)
        }
        assertFullText("Image downloaded")
    }

    private fun assertFullText(text: String) {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertTrue("Missing text layout for $text", results.isNotEmpty())
        results.forEach {
            assertFalse("Clipped text height: $text", it.didOverflowHeight)
            assertEquals(text.length, it.getLineEnd(it.lineCount - 1))
            repeat(it.lineCount) { line ->
                assertFalse("Ellipsized text: $text", it.isLineEllipsized(line))
                // Native font metrics can exceed the rounded pixel width by a fraction of a pixel.
                assertTrue("Clipped text width: $text", it.getLineRight(line) <= it.size.width + 1f)
            }
        }
    }

    private fun savePreview(name: String) {
        File(System.getProperty("java.io.tmpdir"), name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
