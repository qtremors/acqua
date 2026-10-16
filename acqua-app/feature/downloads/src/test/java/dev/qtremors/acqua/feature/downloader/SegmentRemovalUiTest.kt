package dev.qtremors.acqua.feature.downloader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.qtremors.acqua.downloader.SegmentCategory
import dev.qtremors.acqua.ui.components.SegmentRemovalButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SegmentRemovalUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `custom draft survives recreation and Done forwards exactly the selected categories`() {
        var accepted: Set<SegmentCategory>? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { SegmentRemovalButton(emptySet()) { accepted = it } } }
        compose.onNodeWithText("Remove segments (0 categories)").performClick()
        compose.onNodeWithText("Select all").performClick()
        compose.onNodeWithText("Intros").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Done").performClick()
        assertEquals(SegmentCategory.entries.toSet() - SegmentCategory.INTRO, accepted)
    }

    @Test fun `cancel discards edits and Off is an explicit empty selection`() {
        var accepted: Set<SegmentCategory>? = null
        compose.setContent { MaterialTheme { SegmentRemovalButton(setOf(SegmentCategory.SPONSOR)) { accepted = it } } }
        compose.onNodeWithText("Remove segments (1 category)").performClick()
        compose.onNodeWithText("Off").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertNull(accepted)
        compose.onNodeWithText("Remove segments (1 category)").performClick()
        compose.onNodeWithText("Off").performClick()
        compose.onNodeWithText("Done").performClick()
        assertEquals(emptySet<SegmentCategory>(), accepted)
    }
}
