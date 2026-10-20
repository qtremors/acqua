package dev.qtremors.acqua.feature.downloader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.qtremors.acqua.domain.CaptionTrack
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.CaptionFormat
import dev.qtremors.acqua.downloader.CaptionMode
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CaptionPickerUiTest {
    @get:Rule val compose = createComposeRule()
    private val media = ResolvedMedia("https://example.com/video", MediaKind.VIDEO, captions = listOf(
        CaptionTrack("en", "English", false), CaptionTrack("hi", "Hindi", true, translated = true)))

    @Test fun `audio LRC export and translated selection survive draft recreation`() {
        var accepted: YtDlpDownloadOptions? = null
        val state = DownloaderUiState(url = media.url, media = listOf(media), downloadEngine = DownloadEngine.YT_DLP,
            downloadContentType = DownloadContentType.AUDIO, audioFormat = AudioOutputFormat.MP3)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { CaptionPickerDialog(state, {}, {}, {}, { accepted = it }) } }
        compose.onNodeWithTag("caption-mode-EMBED").assertDoesNotExist()
        compose.onNodeWithText("Language").performClick()
        compose.onNodeWithTag("caption-picker-list").performScrollToNode(hasText("Automatic translation"))
        compose.onNodeWithText("Automatic translation").assertIsDisplayed()
        compose.onNodeWithTag("caption-language-hi").performClick()
        compose.onNodeWithTag("caption-language-en").performClick()
        compose.onNodeWithText("Format").performClick()
        compose.onNodeWithTag("caption-picker-list").performScrollToNode(hasTestTag("caption-format-LRC"))
        compose.onNodeWithTag("caption-format-LRC").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("download-captions-dialog-done").performClick()
        assertEquals(CaptionMode.SEPARATE, accepted?.captionMode)
        assertEquals(CaptionFormat.LRC, accepted?.captionFormat)
        assertEquals("hi", accepted?.captionLanguages)
        assertEquals(true, accepted?.automaticCaptions)
    }

    @Test fun `M4A offers embedding and restricts ASS and LRC to export mode`() {
        var accepted: YtDlpDownloadOptions? = null
        val state = DownloaderUiState(url = media.url, media = listOf(media), downloadContentType = DownloadContentType.AUDIO,
            audioFormat = AudioOutputFormat.M4A)
        compose.setContent { MaterialTheme { CaptionPickerDialog(state, {}, {}, {}, { accepted = it }) } }
        compose.onNodeWithTag("caption-picker-list").performScrollToNode(hasTestTag("caption-mode-BOTH"))
        compose.onNodeWithTag("caption-mode-BOTH").performClick()
        compose.onNodeWithText("Format").performClick()
        compose.onNodeWithTag("caption-format-LRC").assertDoesNotExist()
        compose.onNodeWithTag("caption-format-ASS").assertDoesNotExist()
        compose.onNodeWithTag("caption-picker-list").performScrollToNode(hasTestTag("caption-format-SRT"))
        compose.onNodeWithTag("caption-format-SRT").performClick()
        compose.onNodeWithTag("download-captions-dialog-done").performClick()
        assertEquals(CaptionMode.BOTH, accepted?.captionMode)
        assertEquals(CaptionFormat.SRT, accepted?.captionFormat)
    }
}
