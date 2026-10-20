package dev.qtremors.acqua.feature.downloader

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaFormatOption
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.downloader.AudioOutputFormat
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
class DownloadConfigSheetTest {
    @get:Rule val compose = createComposeRule()
    private val state = mutableStateOf(DownloaderUiState(url = "https://example.com/video", validity = LinkValidity.VALID))
    private var fetches = 0
    private var downloads = 0
    private var dismissals = 0
    private val media = ResolvedMedia("https://example.com/video", MediaKind.VIDEO, backend = MediaBackend.YT_DLP,
        formats = listOf(MediaFormatOption("136", "mp4", "avc1", "none", 1280, 720, 30),
            MediaFormatOption("140", "m4a", "none", "mp4a.40.2", audioBitrateKbps = 128),
            MediaFormatOption("137", "mp4", "avc1", "none", 1920, 1080, 30, fileSize = 58_000_000),
            MediaFormatOption("141", "m4a", "none", "aac", audioBitrateKbps = 256),
            MediaFormatOption("18", "mp4", "avc1", "aac", 640, 360, 30)))

    @Test fun `paired summaries open grouped drafts and Cancel discards both tabs`() {
        content()
        val engine = compose.onNodeWithText("Download engine").fetchSemanticsNode().boundsInRoot
        val type = compose.onNodeWithText("Download type").fetchSemanticsNode().boundsInRoot
        val quality = compose.onNodeWithText("Quality").fetchSemanticsNode().boundsInRoot
        val container = compose.onNodeWithText("Container").fetchSemanticsNode().boundsInRoot
        assertTrue(engine.top < type.top && type.top < quality.top)
        assertEquals(quality.top, container.top, 0.5f)
        val video = summary("video-stream").fetchSemanticsNode().boundsInRoot
        val audio = summary("audio-stream").fetchSemanticsNode().boundsInRoot
        assertEquals(video.top, audio.top, 0.5f)
        assertTrue(video.left < audio.left)
        compose.onNodeWithText("yt-dlp").assertIsSelected()
        compose.onNodeWithText("2160p").assertDoesNotExist()
        assertNoExtraControls()
        assertActionInsideSheet()
        savePreview("download-config-sheet", "acqua-download-sheet.png")
        open("quality")
        assertVerticalOrder(listOf("0", "2160", "1440", "1080", "720", "480", "360"))
        choose("720")
        tab("Container")
        choose("MKV")
        assertEquals(0, state.value.maximumVideoHeight)
        assertEquals(VideoContainer.AUTO, state.value.advancedOptions.videoContainer)
        compose.onNodeWithTag("download-picker-cancel").performClick()
        compose.onNodeWithTag("download-picker-dialog").assertDoesNotExist()
        open("quality")
        compose.onNodeWithTag("download-picker-option-0").assertIsSelected()
        choose("720")
        tab("Container")
        choose("MKV")
        done()
        assertEquals(720, state.value.maximumVideoHeight)
        assertEquals(VideoContainer.MKV, state.value.advancedOptions.videoContainer)
        summary("quality").assertTextContains("720p")
        summary("container").assertTextContains("MKV")
    }

    @Test fun `stream tabs apply exact IDs together and quality restores Automatic video`() {
        state.value = state.value.copy(media = listOf(media))
        content()
        open("video-stream")
        assertVerticalOrder(listOf("automatic", "137", "136", "18"))
        choose("137")
        tab("Audio stream")
        assertVerticalOrder(listOf("automatic", "141", "140"))
        choose("140")
        assertNull(state.value.advancedOptions.videoFormatId)
        savePreview("download-picker-dialog", "acqua-download-stream-dialog.png")
        done()
        assertEquals("137", state.value.advancedOptions.videoFormatId)
        assertEquals("140", state.value.advancedOptions.audioFormatId)
        assertFalse(state.value.advancedOptions.videoFormatIncludesAudio)
        assertEquals(1080, state.value.maximumVideoHeight)
        summary("video-stream").assertTextContains("1080p", substring = true)
        summary("audio-stream").assertTextContains("AAC", substring = true)
        open("quality")
        choose("720")
        done()
        assertNull(state.value.advancedOptions.videoFormatId)
        assertEquals("140", state.value.advancedOptions.audioFormatId)
        summary("video-stream").assertTextContains("Automatic")
        compose.onNodeWithText("Download").assertIsDisplayed()
    }

    @Test fun `a video stream containing audio shows Included instead of another audio choice`() {
        state.value = state.value.copy(media = listOf(media))
        content()
        open("video-stream")
        choose("18")
        tab("Audio stream")
        compose.onNodeWithText("This video stream already includes audio.").assertIsDisplayed()
        compose.onNodeWithTag("download-picker-option-140").assertDoesNotExist()
        done()
        summary("audio-stream").assertIsEnabled().assertTextContains("Included")
        open("video-stream")
        choose("automatic")
        done()
        summary("audio-stream").assertIsEnabled().assertTextContains("Automatic")
    }

    @Test fun `audio Format and Quality tabs apply conversion choices on Done`() {
        content()
        compose.onNodeWithText("Audio").performScrollTo().performClick()
        compose.onNodeWithText("Format").assertIsDisplayed()
        summary("video-stream").assertDoesNotExist()
        open("container")
        choose("MP3")
        tab("Quality")
        assertVerticalOrder(listOf("0", "320", "256", "192", "128", "96", "64"))
        choose("192")
        assertEquals(AudioOutputFormat.ORIGINAL, state.value.audioFormat)
        done()
        assertEquals(AudioOutputFormat.MP3, state.value.audioFormat)
        assertEquals(192, state.value.advancedOptions.audioBitrateKbps)
        summary("quality").assertTextContains("192 kbps")
        open("quality")
        choose("0")
        done()
        assertEquals(0, state.value.advancedOptions.audioBitrateKbps)
        summary("quality").assertTextContains("Best")
        savePreview("download-config-sheet", "acqua-download-sheet-audio.png")
    }

    @Test fun `Fetch becomes a download action and changing engines requires Fetch again`() {
        content()
        compose.onNodeWithText("Fetch").assertIsDisplayed()
        compose.onNodeWithTag("download-config-action").performClick()
        assertEquals(1, fetches)
        assertEquals(0, downloads)
        compose.runOnIdle { state.value = state.value.copy(media = listOf(media)) }
        compose.onNodeWithText("Download").assertIsDisplayed()
        compose.onNodeWithTag("download-config-action").performClick()
        assertEquals(1, fetches)
        assertEquals(1, downloads)
        assertEquals(2, dismissals)
        compose.runOnIdle { state.value = state.value.copy(selectedCollectionItems = emptySet()) }
        compose.onNodeWithTag("download-config-action").assertIsNotEnabled()
        compose.onNodeWithText("Acqua").performScrollTo().performClick()
        compose.onNodeWithText("Fetch").assertIsDisplayed()
        summary("quality").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(isResolving = true) }
        compose.onNodeWithTag("download-config-action").assertIsNotEnabled()
    }

    @Test @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun `grouped dialogs and their actions remain reachable at large text sizes`() {
        content(fontScale = 2f)
        assertNoExtraControls()
        assertActionInsideSheet()
        savePreview("download-config-sheet", "acqua-download-sheet-large-text.png")
        open("quality")
        choose("720")
        tab("Container")
        compose.onNodeWithTag("download-picker-done").assertIsDisplayed()
        savePreview("download-picker-dialog", "acqua-download-picker-large-text.png")
        choose("MKV")
        done()
        assertEquals(720, state.value.maximumVideoHeight)
        assertEquals(VideoContainer.MKV, state.value.advancedOptions.videoContainer)
        assertActionInsideSheet()
    }

    private fun summary(field: String) = compose.onNodeWithTag("download-summary-$field")
    private fun open(field: String) { summary(field).performScrollTo().performClick() }
    private fun choose(value: String) { compose.onNodeWithTag("download-picker-option-$value").performScrollTo().performClick() }
    private fun done() { compose.onNodeWithTag("download-picker-done").performClick() }
    private fun tab(label: String) {
        compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("download-picker-dialog"))).performScrollTo().performClick()
    }
    private fun assertVerticalOrder(values: List<String>) {
        val positions = values.map { compose.onNodeWithTag("download-picker-option-$it").fetchSemanticsNode().positionInRoot }
        positions.zipWithNext().forEach { (higher, lower) -> assertTrue(higher.y < lower.y) }
    }
    private fun assertNoExtraControls() {
        listOf("More options", "Saved presets", "Start in seconds", "Keep original dates",
            "Skip already saved media", "Embed metadata", "Embed artwork").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
    }
    private fun content(fontScale: Float = 1f) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                DownloadConfigBottomSheet(state.value, true, { dismissals++ }, { fetches++ },
                    { state.value = state.value.copy(downloadEngine = it, media = null) },
                    { state.value = state.value.copy(downloadContentType = it) },
                    { state.value = state.value.copy(maximumVideoHeight = it.maximumVideoHeight, audioFormat = it.audioFormat, advancedOptions = it) },
                    { downloads++ })
            }
        }
    }
    private fun assertActionInsideSheet() {
        val sheet = compose.onNodeWithTag("download-config-sheet").fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithTag("download-config-action").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(action.top >= sheet.top && action.bottom <= sheet.bottom)
    }
    private fun savePreview(tag: String, name: String) {
        File(System.getProperty("java.io.tmpdir"), name).outputStream().use {
            compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
