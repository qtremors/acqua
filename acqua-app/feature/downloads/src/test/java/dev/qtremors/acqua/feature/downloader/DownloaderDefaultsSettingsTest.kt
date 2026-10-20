package dev.qtremors.acqua.feature.downloader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.TransferNetworkOptions
import dev.qtremors.acqua.downloader.AudioCodecPreference
import dev.qtremors.acqua.downloader.DownloadFormatPolicy
import dev.qtremors.acqua.downloader.SegmentCategory
import dev.qtremors.acqua.downloader.VideoCodecPreference
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions
import android.graphics.Bitmap
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloaderDefaultsSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `advanced card opens its editors and summaries follow confirmed settings`() {
        val defaults = mutableStateOf(YtDlpDownloadOptions())
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DownloaderExtraSettingsContent(defaults.value,
                        onNetworkChange = { defaults.value = defaults.value.copy(network = it) },
                        onSegmentsChange = { defaults.value = defaults.value.copy(removeSegments = it) },
                        onFormatPolicyChange = { defaults.value = defaults.value.copy(formatPolicy = it) })
                }
            }
        }
        compose.onNodeWithText("Network options").performScrollTo().performClick()
        compose.onNodeWithText("Speed limit").performTextReplacement("512")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("System defaults").assertIsDisplayed()
        compose.onNodeWithText("Network options").performClick()
        compose.onNodeWithText("Speed limit").performTextReplacement("512")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("512 KiB/s").assertIsDisplayed()
        compose.onNodeWithText("Format and codecs").performScrollTo().performClick()
        compose.onNodeWithText("Lowest quality").performScrollTo().performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Lowest quality").assertIsDisplayed()
        compose.onNodeWithText("Remove segments").performScrollTo().performClick()
        compose.onAllNodesWithText("Sponsors").onLast().performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("1 category enabled").assertIsDisplayed()
    }

    @Test fun `advanced card summaries fit narrow widths large fonts and RTL`() {
        val font = mutableStateOf(1f)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val defaults = YtDlpDownloadOptions(
            network = TransferNetworkOptions(512, "http://localhost:8080", true, 4),
            formatPolicy = DownloadFormatPolicy(videoCodec = VideoCodecPreference.H264, audioCodec = AudioCodecPreference.AAC),
            removeSegments = setOf(SegmentCategory.SPONSOR, SegmentCategory.INTRO))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font.value), LocalLayoutDirection provides direction.value) {
                MaterialTheme {
                    Box(Modifier.width(320.dp)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) { DownloaderExtraSettingsContent(defaults) }
                    }
                }
            }
        }
        for (scale in listOf(1f, 2f)) {
            compose.runOnIdle { font.value = scale; direction.value = if (scale == 1f) LayoutDirection.Ltr else LayoutDirection.Rtl }
            for (text in listOf("Network options", "512 KiB/s · Proxy enabled · IPv4 only · 4 parallel fragments",
                "Format and codecs", "Automatic · H.264 · AAC", "Remove segments", "2 categories enabled")) {
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(text, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout = layouts.single()
                assertFalse("Clipped at font scale $scale: $text; size ${layout.size}, constraints ${layout.layoutInput.constraints}, paragraph ${layout.multiParagraph.height}, width overflow ${layout.didOverflowWidth}", layout.hasVisualOverflow)
            }
            compose.onNodeWithTag("advanced-downloads-card").performScrollTo()
            File("build/reports/advanced-downloads-${if (scale == 1f) "normal" else "large-rtl"}.png").also { file ->
                file.parentFile?.mkdirs()
                file.outputStream().use {
                    compose.onNodeWithTag("advanced-downloads-card").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
    }

    @Test fun `defaults stay accessible on either engine and changes update their rows`() {
        val state = mutableStateOf(DownloaderUiState(downloadEngine = DownloadEngine.ACQUA))
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DownloaderExtraSettingsContent(state.value.advancedOptions,
                        onRecordHistory = { state.value = state.value.copy(advancedOptions = state.value.advancedOptions.copy(recordHistory = it)) },
                        onSkipArchived = { state.value = state.value.copy(advancedOptions = state.value.advancedOptions.copy(skipArchived = it)) })
                }
            }
        }
        compose.onNodeWithText("Save to History").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithText("Skip already saved media").performClick().assertIsOn()
        for (engine in DownloadEngine.entries) {
            compose.runOnIdle { state.value = state.value.copy(downloadEngine = engine) }
            compose.onNodeWithText("Network options").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Format and codecs").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Remove segments").performScrollTo().assertIsDisplayed()
        }
    }
}
