package dev.qtremors.acqua.feature.downloader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.downloader.DownloadContentType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloaderArtworkSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val state = mutableStateOf(audioState())

    @Test fun `unsupported media and direct transfers omit artwork controls and retain the other options`() {
        content()
        val video = audioState().copy(downloadContentType = DownloadContentType.VIDEO,
            media = listOf(ResolvedMedia("https://example.com/video", MediaKind.VIDEO, backend = MediaBackend.YT_DLP)))
        val photo = video.copy(selectedMediaKind = MediaKind.IMAGE,
            media = listOf(ResolvedMedia("https://example.com/photo", MediaKind.IMAGE)))
        val direct = audioState().copy(media = listOf(ResolvedMedia("https://example.com/audio", MediaKind.AUDIO)))
        for (unsupported in listOf(video, photo, direct.copy(downloadEngine = DownloadEngine.ACQUA), direct)) {
            compose.runOnIdle { state.value = unsupported }
            compose.onNodeWithText("Embed artwork").assertDoesNotExist()
            compose.onNodeWithText("Square album artwork").assertDoesNotExist()
            compose.onNodeWithText("Embed metadata").assertIsDisplayed()
            compose.onNodeWithText("Keep original dates").assertIsDisplayed()
            compose.onNodeWithText("Skip already saved media").assertIsDisplayed()
        }
    }

    @Test fun `processed audio keeps embedding and cropping choices when the controls are hidden and restored`() {
        content()
        compose.onNodeWithText("Embed artwork").assertIsOn().performClick()
        compose.onNodeWithText("Square album artwork").assertIsNotEnabled()
        assertFalse(state.value.embedThumbnail)
        assertTrue(state.value.cropArtwork)
        compose.onNodeWithText("Embed artwork").performClick()
        compose.onNodeWithText("Square album artwork").assertIsEnabled().assertIsOn().performClick()
        assertTrue(state.value.embedThumbnail)
        assertFalse(state.value.cropArtwork)
        compose.runOnIdle { state.value = state.value.copy(downloadEngine = DownloadEngine.ACQUA) }
        compose.onNodeWithText("Embed artwork").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(downloadEngine = DownloadEngine.YT_DLP) }
        compose.onNodeWithText("Embed artwork").assertIsOn()
        compose.onNodeWithText("Square album artwork").assertIsEnabled().assertIsOff()
    }

    private fun content() {
        compose.setContent {
            MaterialTheme {
                DownloaderExtraSettingsContent(state.value,
                    onEmbedMetadata = { state.value = state.value.copy(embedMetadata = it) },
                    onEmbedThumbnail = { state.value = state.value.copy(embedThumbnail = it) },
                    onCropArtwork = { state.value = state.value.copy(cropArtwork = it) },
                    onPreserveOriginalMetadata = { state.value = state.value.copy(preserveOriginalMetadata = it) },
                    onSkipArchived = { state.value = state.value.copy(advancedOptions = state.value.advancedOptions.copy(skipArchived = it)) })
            }
        }
    }

    private fun audioState() = DownloaderUiState(downloadEngine = DownloadEngine.YT_DLP,
        downloadContentType = DownloadContentType.AUDIO,
        media = listOf(ResolvedMedia("https://example.com/audio", MediaKind.AUDIO, backend = MediaBackend.YT_DLP)))
}
