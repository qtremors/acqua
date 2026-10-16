package dev.qtremors.acqua.feature.downloader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import dev.qtremors.acqua.downloader.DownloadFormatPolicy
import dev.qtremors.acqua.downloader.FormatPolicyMode
import dev.qtremors.acqua.ui.components.FormatPolicyButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FormatPolicyUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `invalid custom sorting prevents acceptance and valid draft restores`() {
        var accepted: DownloadFormatPolicy? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { FormatPolicyButton(DownloadFormatPolicy()) { accepted = it } } }
        compose.onNodeWithText("Format: Automatic · AUTO · AUTO").performClick()
        compose.onNodeWithText("Custom sorting").performClick()
        compose.onNodeWithText("res,fps,br").performTextReplacement("--exec")
        compose.onNodeWithText("Done").assertIsNotEnabled()
        compose.onNodeWithText("--exec").performTextReplacement("+size,res")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Done").performClick()
        assertEquals(DownloadFormatPolicy(FormatPolicyMode.CUSTOM, customSort = "+size,res"), accepted)
    }

    @Test fun `cancel preserves accepted policy`() {
        var accepted: DownloadFormatPolicy? = null
        compose.setContent { MaterialTheme { FormatPolicyButton(DownloadFormatPolicy()) { accepted = it } } }
        compose.onNodeWithText("Format: Automatic · AUTO · AUTO").performClick()
        compose.onNodeWithText("Lowest quality").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertNull(accepted)
    }
}
