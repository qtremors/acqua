package dev.qtremors.acqua.feature.downloader

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.downloader.CommandTemplate
import dev.qtremors.acqua.downloader.CommandTemplateStore
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions
import dev.qtremors.acqua.ui.components.CommandTemplateSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CommandTemplateSelectorTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val template = CommandTemplate(name = "Metadata off", command = "--no-embed-metadata")
    @Before fun reset() { context.getSharedPreferences("command_templates_v1", Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun `selection restores and only Done applies resolved options`() {
        CommandTemplateStore(context).put(template)
        var accepted: YtDlpDownloadOptions? = null
        val restore = StateRestorationTester(compose)
        restore.setContent { MaterialTheme { CommandTemplateSelector(YtDlpDownloadOptions()) { accepted = it } } }
        compose.onNodeWithText("Command template: None").performClick()
        compose.onNodeWithText(template.name).performClick()
        restore.emulateSavedInstanceStateRestore()
        assertNull(accepted)
        compose.onNodeWithText("Done").performClick()
        assertFalse(accepted!!.embedMetadata)
        assertEquals(template.id, accepted!!.commandTemplateId)
    }

    @Test fun `missing template can be cleared without changing resolved choices`() {
        var accepted: YtDlpDownloadOptions? = null
        val options = template.applyTo(YtDlpDownloadOptions())
        compose.setContent { MaterialTheme { CommandTemplateSelector(options) { accepted = it } } }
        compose.onNodeWithText("Command template: Missing or changed").performClick()
        compose.onNodeWithText("None").performClick()
        compose.onNodeWithText("Done").performClick()
        assertNull(accepted!!.commandTemplateId)
        assertFalse(accepted!!.embedMetadata)
    }
}
