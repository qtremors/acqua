package dev.qtremors.acqua.feature.downloader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.qtremors.acqua.domain.MediaFormatOption
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SilentVideoPickerTest {
    @get:Rule val compose = createComposeRule()
    @Test fun silentVideoStillOffersQualityChoices() {
        compose.setContent { MaterialTheme { DownloadPickerDialog(
            DownloaderUiState(advancedOptions = YtDlpDownloadOptions(silentVideo = true)),
            DownloadPickerField.QUALITY, {}, {}) } }
        compose.onNodeWithTag("download-picker-option-720").assertExists()
        compose.onNodeWithText("Container").performClick()
        compose.onNodeWithTag("download-picker-option-MKV").assertExists()
    }

    @Test fun silentVideoStillOffersVideoStreams() {
        val media = ResolvedMedia("https://example.com/video", MediaKind.VIDEO,
            formats = listOf(MediaFormatOption("137", "mp4", "avc1", "none", 1920, 1080, 30)))
        compose.setContent { MaterialTheme { DownloadPickerDialog(
            DownloaderUiState(media = listOf(media), advancedOptions = YtDlpDownloadOptions(silentVideo = true)),
            DownloadPickerField.VIDEO_STREAM, {}, {}) } }
        compose.onNodeWithTag("download-picker-option-137").assertExists()
    }
}
