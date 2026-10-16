package dev.qtremors.acqua.feature.downloader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import dev.qtremors.acqua.data.network.TransferNetworkOptions
import dev.qtremors.acqua.ui.components.TransferNetworkOptionsButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransferNetworkOptionsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `invalid or credential-bearing proxies cannot be saved and cancellation keeps options`() {
        var accepted: TransferNetworkOptions? = null
        compose.setContent { MaterialTheme { TransferNetworkOptionsButton(TransferNetworkOptions()) { accepted = it } } }
        compose.onNodeWithText("Network options").performClick()
        compose.onNodeWithText("Proxy address (blank for system default)").performTextReplacement("http://user:password@host:8080")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
        assertNull(accepted)
    }

    @Test fun `edited options survive dialog recreation and save together`() {
        var accepted: TransferNetworkOptions? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { TransferNetworkOptionsButton(TransferNetworkOptions()) { accepted = it } } }
        compose.onNodeWithText("Network options").performClick()
        compose.onNodeWithText("Speed per transfer in KiB/s (0 for unlimited)").performTextReplacement("512")
        compose.onNodeWithText("Proxy address (blank for system default)").performTextReplacement("http://localhost:8080")
        compose.onNodeWithText("yt-dlp fragments at once (1–8)").performTextReplacement("4")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Save").performClick()
        assertEquals(TransferNetworkOptions(512, "http://localhost:8080", false, 4), accepted)
    }
}
