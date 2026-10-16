package dev.qtremors.acqua.feature.downloader

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import dev.qtremors.acqua.domain.CaptionTrack
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaChapter
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.downloader.CaptionFormat
import dev.qtremors.acqua.downloader.CaptionMode
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.downloader.VideoContainer
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloadExtraControlsTest {
    @get:Rule val compose = createComposeRule()
    private val url = "https://www.youtube.com/watch?v=abcdefghijk&list=PL123"
    private val video = ResolvedMedia("https://www.youtube.com/watch?v=abcdefghijk", MediaKind.VIDEO,
        backend = MediaBackend.YT_DLP, title = "A walk through the mountains", durationSeconds = 300,
        captions = listOf(CaptionTrack("en", "English", false), CaptionTrack("hi", "Hindi", true)),
        chapters = listOf(MediaChapter("Introduction", 0.0, 60.0), MediaChapter("The trail", 60.0, 300.0)))
    private val entries = List(4) { video.copy(url = "https://example.com/video/$it", title = "Mountain trail ${it + 1}",
        collectionUrl = url, collectionTitle = "Weekend walks", collectionIndex = it + 1, collectionSize = 4,
        captions = emptyList(), chapters = emptyList(), thumbnailUrl = "file:///missing-thumbnail.png") }
    private val state = mutableStateOf(DownloaderUiState(url = url, validity = LinkValidity.VALID, media = entries))

    @Test fun `thumbnail chip toggles separately while preserving media choices and playlist selection`() {
        RuntimeEnvironment.setFontScale(1f)
        val options = state.value.advancedOptions.copy(videoFormatId = "137", audioFormatId = "140",
            videoContainer = VideoContainer.MKV, captionMode = CaptionMode.BOTH, embedThumbnail = false, cropArtwork = false)
        state.value = state.value.copy(advancedOptions = options, selectedCollectionItems = setOf(1, 3))
        content()
        open("thumbnail")
        compose.onNodeWithTag("download-extra-thumbnail").assertIsOn()
        assertEquals(options.copy(downloadThumbnail = true), state.value.advancedOptions)
        assertEquals(setOf(1, 3), state.value.selectedCollectionItems)
        preview("download-config-sheet", "acqua-thumbnail-chip.png")
        open("thumbnail")
        compose.onNodeWithTag("download-extra-thumbnail").assertIsOff()
        assertEquals(options, state.value.advancedOptions)
    }

    @Test fun `direct audio offers the artwork chip and image downloads do not offer an extra thumbnail`() {
        RuntimeEnvironment.setFontScale(1f)
        state.value = state.value.copy(downloadEngine = DownloadEngine.ACQUA, downloadContentType = DownloadContentType.AUDIO,
            media = listOf(video.copy(kind = MediaKind.AUDIO, backend = MediaBackend.DIRECT)))
        content()
        open("thumbnail")
        compose.onNodeWithText("Artwork").assertIsDisplayed()
        assertTrue(state.value.advancedOptions.downloadThumbnail)
        compose.onNodeWithTag("download-extra-clip").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(selectedMediaKind = MediaKind.IMAGE,
            media = listOf(video.copy(kind = MediaKind.IMAGE, backend = MediaBackend.DIRECT))) }
        compose.onNodeWithTag("download-extra-thumbnail").assertDoesNotExist()
    }

    @Test fun `playlist thumbnails ranges and empty selection stay draft until Done`() {
        content()
        open("playlist")
        compose.onNodeWithTag("playlist-thumbnail-0", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("playlist-entry-0").assertIsOn()
        compose.onNodeWithText("None").performScrollTo().performClick()
        compose.onNodeWithTag("download-playlist-dialog-done").assertIsNotEnabled()
        compose.onNodeWithTag("playlist-range").performScrollTo().performTextReplacement("2,4")
        compose.onNodeWithText("Apply range").performScrollTo().performClick()
        compose.onNodeWithTag("playlist-entry-1").performScrollTo().assertIsOn()
        compose.onNodeWithTag("playlist-entry-0").performScrollTo().assertIsOff()
        assertNull(state.value.selectedCollectionItems)
        preview("download-playlist-dialog", "acqua-playlist-picker.png")
        compose.onNodeWithTag("download-playlist-dialog-cancel").performClick()
        assertNull(state.value.selectedCollectionItems)
        open("playlist")
        compose.onNodeWithTag("playlist-range").performScrollTo().performTextReplacement("2,4")
        compose.onNodeWithText("Apply range").performScrollTo().performClick()
        done("playlist")
        assertEquals(setOf(1, 3), state.value.selectedCollectionItems)
        compose.onNodeWithText("Download · 2").assertIsDisplayed()
    }

    @Test fun `switching to this video requires Fetch and pure playlist hides that scope`() {
        content()
        open("playlist")
        compose.onNodeWithText("This video").performClick()
        compose.onNodeWithTag("download-playlist-dialog-cancel").performClick()
        assertTrue(state.value.downloadPlaylist)
        open("playlist")
        compose.onNodeWithText("This video").performClick()
        done("playlist")
        assertFalse(state.value.downloadPlaylist)
        compose.onNodeWithText("Fetch").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(url = "https://www.youtube.com/playlist?list=PL123", downloadPlaylist = true) }
        open("playlist")
        compose.onNodeWithText("This video").assertDoesNotExist()
    }

    @Test fun `caption popup selects actual languages and conversion while Cancel keeps clips`() {
        state.value = state.value.copy(media = listOf(video), advancedOptions = state.value.advancedOptions.copy(
            clipStartSeconds = 10.0, clipEndSeconds = 30.0))
        content()
        open("captions")
        compose.onNodeWithTag("caption-mode-BOTH").performScrollTo().performClick()
        compose.onNodeWithTag("download-captions-dialog-cancel").performClick()
        assertEquals(10.0, state.value.advancedOptions.clipStartSeconds!!, 0.0)
        assertEquals(CaptionMode.NONE, state.value.advancedOptions.captionMode)
        open("captions")
        compose.onNodeWithTag("caption-mode-BOTH").performScrollTo().performClick()
        compose.onNodeWithText("Language").performScrollTo().performClick()
        compose.onNodeWithTag("caption-language-hi").performScrollTo().performClick()
        preview("download-captions-dialog", "acqua-caption-picker.png")
        compose.onNodeWithText("Format").performScrollTo().performClick()
        compose.onNodeWithTag("caption-format-SRT").performScrollTo().performClick()
        done("captions")
        assertEquals(CaptionMode.BOTH, state.value.advancedOptions.captionMode)
        assertEquals(CaptionFormat.SRT, state.value.advancedOptions.captionFormat)
        assertEquals(setOf("en", "hi"), state.value.advancedOptions.captionLanguages.split(',').toSet())
        assertTrue(state.value.advancedOptions.automaticCaptions)
        assertNull(state.value.advancedOptions.clipStartSeconds)
        compose.runOnIdle { state.value = state.value.copy(advancedOptions = state.value.advancedOptions.copy(videoContainer = VideoContainer.WEBM)) }
        open("captions")
        compose.onNodeWithText("Format").performScrollTo().performClick()
        compose.onNodeWithTag("caption-format-SRT").assertDoesNotExist()
        done("captions")
        assertEquals(CaptionFormat.VTT, state.value.advancedOptions.captionFormat)
        open("captions")
        compose.onNodeWithText("Language").performScrollTo().performClick()
        compose.onNodeWithTag("caption-language-all").performScrollTo().performClick()
        done("captions")
        assertEquals("all", state.value.advancedOptions.captionLanguages)
        assertTrue(state.value.advancedOptions.automaticCaptions)
    }

    @Test fun `playlist clip fetches one entry validates bounds and applies chapters`() {
        state.value = state.value.copy(advancedOptions = state.value.advancedOptions.copy(captionMode = CaptionMode.BOTH))
        content()
        open("clip")
        compose.onNodeWithText("Range").performScrollTo().performClick()
        compose.onNodeWithTag("playlist-entry-1").performScrollTo().performClick()
        compose.onNodeWithTag("download-clip-dialog-done").assertIsNotEnabled()
        compose.onNodeWithText("Fetch").performScrollTo().performClick()
        compose.onNodeWithTag("clip-start").performScrollTo().performTextReplacement("1:00")
        compose.onNodeWithTag("clip-end").performTextReplacement("0:30")
        compose.onNodeWithTag("download-clip-dialog-done").assertIsNotEnabled()
        compose.onNodeWithTag("clip-end").performTextReplacement("2:00")
        compose.onNodeWithTag("clip-range-slider").performScrollTo().assertIsDisplayed()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))[0]
            .performSemanticsAction(SemanticsActions.SetProgress) { it(90f) }
        compose.onNodeWithTag("clip-start").assertTextContains("1:30")
        preview("download-clip-dialog", "acqua-clip-picker.png")
        assertNull(state.value.selectedCollectionItems)
        done("clip")
        assertEquals(setOf(1), state.value.selectedCollectionItems)
        assertEquals(90.0, state.value.advancedOptions.clipStartSeconds!!, 0.0)
        assertEquals(CaptionMode.NONE, state.value.advancedOptions.captionMode)
        open("clip")
        compose.onNodeWithText("Chapters").performScrollTo().performClick()
        preview("download-clip-dialog", "acqua-chapter-picker.png")
        done("clip")
        assertTrue(state.value.advancedOptions.splitChapters)
        assertNull(state.value.advancedOptions.clipStartSeconds)
    }

    @Test @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun `all modal actions remain reachable at large text sizes`() {
        RuntimeEnvironment.setFontScale(2f)
        content()
        for (kind in listOf("playlist", "captions", "clip")) {
            open(kind)
            preview("download-$kind-dialog", "acqua-$kind-picker-large-text.png")
            compose.onNodeWithTag("download-$kind-dialog-cancel").assertIsDisplayed()
            compose.onNodeWithTag("download-$kind-dialog-done").assertIsDisplayed()
            if (kind == "captions") compose.onNodeWithTag("caption-mode-NONE").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("download-$kind-dialog-cancel").performClick()
        }
        preview("download-config-sheet", "acqua-extra-row-large-text.png")
        compose.onNodeWithTag("download-config-action").assertIsDisplayed()
    }

    private fun content() {
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme()) {
            DownloadConfigBottomSheet(state.value, true, {}, {}, {}, {}, {}, {},
                onPlaylistSelection = { scope, selected -> state.value = state.value.copy(downloadPlaylist = scope,
                    selectedCollectionItems = selected, media = if (scope == state.value.downloadPlaylist) state.value.media else null) },
                onContentSelection = { options, selected -> state.value = state.value.copy(advancedOptions = options, selectedCollectionItems = selected) },
                onInspectEntry = { index ->
                    val items = state.value.media.orEmpty().toMutableList()
                    items[index] = items[index].copy(durationSeconds = video.durationSeconds, chapters = video.chapters, captions = video.captions)
                    state.value = state.value.copy(media = items, inspectedCollectionItems = state.value.inspectedCollectionItems + index)
                })
        } }
    }
    private fun open(kind: String) {
        compose.onNodeWithTag("download-config-controls").performTouchInput { swipeUp() }
        compose.onNodeWithTag("download-extra-$kind").performScrollTo().assertIsDisplayed().performClick()
    }
    private fun done(kind: String) { compose.onNodeWithTag("download-$kind-dialog-done").performClick() }
    private fun preview(tag: String, name: String) {
        File(System.getProperty("java.io.tmpdir"), name).outputStream().use {
            compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
