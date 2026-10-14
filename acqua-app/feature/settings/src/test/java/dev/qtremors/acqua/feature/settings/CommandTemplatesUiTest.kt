package dev.qtremors.acqua.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.downloader.CommandTemplateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CommandTemplatesUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = CommandTemplateStore(context)
    @Before fun reset() { context.getSharedPreferences("command_templates_v1", Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun `editor draft restores invalid options cannot save and valid templates persist`() {
        val restore = StateRestorationTester(compose)
        restore.setContent { MaterialTheme { CommandTemplatesSection() } }
        compose.onNodeWithText("Manage command templates").performClick()
        compose.onNodeWithText("New template").performClick()
        compose.onNodeWithText("Name").performTextReplacement("Audio export")
        compose.onNodeWithText("Options").performTextReplacement("--exec secret-value")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Options").performTextReplacement("-x --audio-format m4a --no-embed-metadata")
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Save").performClick()
        assertEquals("Audio export", store.all().single().name)
        assertTrue(store.all().single().command.contains("--audio-format m4a"))
    }

    @Test fun `clipboard import stays a preview until accepted and cancellation preserves original templates`() {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("fixture", "--no-embed-metadata"))
        compose.setContent { MaterialTheme { CommandTemplatesSection() } }
        compose.onNodeWithText("Manage command templates").performClick()
        compose.onNodeWithText("Paste templates").performClick()
        assertTrue(store.all().isEmpty())
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(store.all().isEmpty())
        compose.onNodeWithText("Paste templates").performClick()
        compose.onNodeWithText("Import these templates").performClick()
        assertEquals(1, store.all().size)
        compose.onNodeWithText("Copy templates").performScrollTo().performClick()
        assertTrue(clipboard.primaryClip!!.getItemAt(0).text.toString().contains("acqua.command-templates"))
    }
}
