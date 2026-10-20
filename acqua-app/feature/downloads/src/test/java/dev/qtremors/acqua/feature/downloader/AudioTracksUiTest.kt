package dev.qtremors.acqua.feature.downloader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsOn
import dev.qtremors.acqua.domain.MediaFormatOption
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.downloader.VideoContainer
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioTracksUiTest {
    @get:Rule val compose = createComposeRule()
    private val media = ResolvedMedia("https://example.com/video", MediaKind.VIDEO, formats = listOf(
        MediaFormatOption("137", "mp4", "avc1", "none", 1920, 1080, 30),
        MediaFormatOption("140", "m4a", "none", "mp4a", language = "en", audioChannels = 2),
        MediaFormatOption("141", "m4a", "none", "mp4a", language = "hi", audioChannels = 2)))

    @Test fun `multiple audio selections and their expanded panel survive dialog restoration`() {
        var accepted: YtDlpDownloadOptions? = null
        val restore = StateRestorationTester(compose)
        restore.setContent { MaterialTheme { DownloadPickerDialog(DownloaderUiState(media = listOf(media)), DownloadPickerField.AUDIO_STREAM, {}, { accepted = it }) } }
        compose.onNodeWithText("Audio tracks (0 selected)").performClick()
        compose.onNodeWithTag("audio-track-140").performScrollTo().performClick()
        compose.onNodeWithTag("audio-track-141").performScrollTo().performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("audio-track-140").performScrollTo().assertIsOn()
        compose.onNodeWithTag("audio-track-141").performScrollTo().assertIsOn()
        compose.onNodeWithTag("download-picker-done").performClick()
        assertEquals("140", accepted?.audioFormatId)
        assertEquals(setOf("141"), accepted?.additionalAudioFormatIds)
        assertEquals(VideoContainer.MKV, accepted?.videoContainer)
    }

    @Test fun `silent video clears combined and audio choices before acceptance`() {
        var accepted: YtDlpDownloadOptions? = null
        val original = YtDlpDownloadOptions(videoFormatId = "18", audioFormatId = "140", additionalAudioFormatIds = setOf("141"))
        compose.setContent { MaterialTheme { DownloadPickerDialog(DownloaderUiState(media = listOf(media), advancedOptions = original),
            DownloadPickerField.AUDIO_STREAM, {}, { accepted = it }) } }
        compose.onNodeWithTag("silent-video").performClick()
        compose.onNodeWithTag("download-picker-done").performClick()
        assertTrue(accepted!!.silentVideo)
        assertNull(accepted!!.videoFormatId)
        assertNull(accepted!!.audioFormatId)
        assertTrue(accepted!!.additionalAudioFormatIds.isEmpty())
    }
}
