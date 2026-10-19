package dev.qtremors.acqua.feature.settings

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.domain.TransferNetworkOptions
import dev.qtremors.acqua.data.session.BrowserIdentityStore
import dev.qtremors.acqua.data.session.CookieProfile
import dev.qtremors.acqua.data.session.CookieProfileCipher
import dev.qtremors.acqua.data.session.CookieProfileStore
import dev.qtremors.acqua.data.session.SessionCookie
import dev.qtremors.acqua.downloader.CommandTemplate
import dev.qtremors.acqua.downloader.CommandTemplateStore
import dev.qtremors.acqua.downloader.DownloadFormatPolicy
import dev.qtremors.acqua.ui.components.FormatPolicyButton
import dev.qtremors.acqua.ui.components.SegmentRemovalButton
import dev.qtremors.acqua.ui.components.TransferNetworkOptionsButton
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsModalLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `advanced editors show grouped controls with fixed actions and accessible codecs`() {
        compose.setContent {
            MaterialTheme {
                Column {
                    TransferNetworkOptionsButton(TransferNetworkOptions()) {}
                    FormatPolicyButton(DownloadFormatPolicy()) {}
                    SegmentRemovalButton(emptySet()) {}
                }
            }
        }
        compose.onNodeWithText("Network options").performClick()
        compose.onNodeWithText("Save").assertIsDisplayed()
        compose.onNodeWithText("Proxy address").performTextReplacement("invalid-proxy")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Use HTTP or SOCKS5", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Proxy address").performTextReplacement("")
        capture("network-options-dialog", "network")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Format: Automatic · AUTO · AUTO").performClick()
        compose.onNodeWithContentDescription("Source video codec: Automatic").performScrollTo().performClick()
        compose.onNodeWithText("H.264").performClick()
        compose.onNodeWithText("Done").assertIsDisplayed()
        capture("format-policy-dialog", "formats")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Remove segments (0 categories)").performClick()
        compose.onNodeWithText("Done").assertIsDisplayed()
        assertTrue(compose.onNodeWithText("Intros").fetchSemanticsNode().boundsInRoot.height >= 48f)
        capture("segment-removal-dialog", "segments")
    }

    @Test fun `populated template and saved session dialogs keep content grouped and actions visible`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CommandTemplateStore(context).put(CommandTemplate(java.util.UUID.randomUUID().toString(), "Audio export", "-x --audio-format m4a"))
        val store = CookieProfileStore(context, object : CookieProfileCipher {
            override fun encrypt(value: String) = value.reversed()
            override fun decrypt(value: String) = value.reversed()
            override fun erase() = Unit
        })
        store.put(CookieProfile(java.util.UUID.randomUUID().toString(), "Example account", "https://example.com/private", "Observed Browser UA", 1_800_000_000_000L,
            listOf(SessionCookie("sid", "fixture", "example.com", true, "/", 1_900_000_000_000L, true, true))).validated())
        BrowserIdentityStore(context).record("https://example.com/private", "Observed Browser UA")
        compose.setContent { MaterialTheme { Column { CommandTemplatesSection(); CookieProfilesSection(store) } } }
        compose.onNodeWithText("Manage command templates").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Audio export").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Close").assertIsDisplayed()
        capture("command-templates-dialog", "templates")
        compose.onNodeWithContentDescription("Edit Audio export").performScrollTo().performClick()
        compose.onNodeWithText("Save").assertIsDisplayed()
        capture("command-template-editor", "template-editor")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Manage saved sessions").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Example account").fetchSemanticsNodes().isNotEmpty() }
        capture("saved-sessions-dialog", "sessions")
        compose.onNodeWithText("Refresh from browser").performScrollTo().performClick()
        compose.onNodeWithText("Save authorized session").assertIsDisplayed().assertIsNotEnabled()
        capture("saved-session-editor", "session-editor")
    }

    @Suppress("DEPRECATION")
    @Test
    @Config(qualifiers = "w600dp-h360dp-mdpi")
    fun `network editor keeps actions reachable in landscape with large fonts`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(context.resources.configuration).apply { fontScale = 2f }
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
        compose.setContent { MaterialTheme { TransferNetworkOptionsButton(TransferNetworkOptions()) {} } }
        compose.onNodeWithText("Network options").performClick()
        compose.onNodeWithText("Save").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("Use IPv4 only").performScrollTo().assertIsDisplayed()
        capture("network-options-dialog", "network-landscape-large")
    }

    private fun capture(tag: String, name: String) {
        File("build/reports/settings-modals/$name.png").also { file ->
            file.parentFile?.mkdirs()
            file.outputStream().use { compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
