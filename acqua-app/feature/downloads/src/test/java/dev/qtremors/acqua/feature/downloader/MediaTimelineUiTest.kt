package dev.qtremors.acqua.feature.downloader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import dev.qtremors.acqua.domain.CaptionTrack
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.downloader.CaptionFormat
import dev.qtremors.acqua.downloader.CaptionMode
import dev.qtremors.acqua.downloader.CaptionTiming
import dev.qtremors.acqua.downloader.ClipRange
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaTimelineUiTest {
    @get:Rule val compose = createComposeRule()
    private val media = ResolvedMedia("https://example.com/clip", MediaKind.VIDEO, durationSeconds = 10,
        captions = listOf(CaptionTrack("en", "English", false)))
    private val ranges = listOf(ClipRange(0.0, 3.0, "Opening"), ClipRange(5.0, 9.0, "Ending"))

    @Test fun `multiple ranges keep captions restore draft edits and reject overlaps until Done`() {
        var accepted: YtDlpDownloadOptions? = null
        val state = DownloaderUiState(url = media.url, media = listOf(media), advancedOptions = YtDlpDownloadOptions(clipRanges = ranges, captionMode = CaptionMode.SEPARATE))
        val restore = StateRestorationTester(compose)
        restore.setContent { MaterialTheme { ClipPickerDialog(state, {}, {}, {}, { options, _ -> accepted = options }) } }
        compose.onNodeWithTag("clip-picker-list").performScrollToNode(hasTestTag("clip-range-1"))
        compose.onNode(hasTestTag("clip-start") and hasAnyAncestor(hasTestTag("clip-range-1"))).performTextReplacement("0:02")
        compose.onNodeWithTag("download-clip-dialog-done").assertIsNotEnabled()
        assertNull(accepted)
        compose.onNode(hasTestTag("clip-start") and hasAnyAncestor(hasTestTag("clip-range-1"))).performTextReplacement("0:04.25")
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("download-clip-dialog-done").assertIsEnabled().performClick()
        assertEquals(ranges.first(), accepted!!.clipRanges.first())
        assertEquals(4.25, accepted!!.clipRanges.last().startSeconds, 0.0)
        assertEquals(CaptionMode.SEPARATE, accepted!!.captionMode)
    }

    @Test fun `caption offset and scale preserve clips and survive draft restoration`() {
        var accepted: YtDlpDownloadOptions? = null
        val state = DownloaderUiState(url = media.url, media = listOf(media), advancedOptions = YtDlpDownloadOptions(clipRanges = ranges, captionMode = CaptionMode.SEPARATE))
        val restore = StateRestorationTester(compose)
        restore.setContent { MaterialTheme { CaptionPickerDialog(state, {}, {}, {}, { accepted = it }) } }
        compose.onNodeWithText("Timing").performClick()
        compose.onNodeWithTag("caption-picker-list").performScrollToNode(hasTestTag("caption-offset"))
        compose.onNodeWithTag("caption-offset").performTextReplacement("-0.25")
        compose.onNodeWithTag("caption-picker-list").performScrollToNode(hasTestTag("caption-scale"))
        compose.onNodeWithTag("caption-scale").performTextReplacement("0")
        compose.onNodeWithTag("download-captions-dialog-done").assertIsNotEnabled()
        compose.onNodeWithTag("caption-scale").performTextReplacement("110")
        restore.emulateSavedInstanceStateRestore()
        assertNull(accepted)
        compose.onNodeWithTag("download-captions-dialog-done").performClick()
        assertEquals(CaptionTiming(-0.25, 1.1), accepted!!.captionTiming)
        assertEquals(ranges, accepted!!.clipRanges)
        assertEquals(CaptionFormat.SRT, accepted!!.captionFormat)
    }
}
