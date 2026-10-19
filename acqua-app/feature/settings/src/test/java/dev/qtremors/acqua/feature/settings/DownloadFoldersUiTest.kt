package dev.qtremors.acqua.feature.settings

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.data.settings.DownloadDestinationStore
import dev.qtremors.acqua.data.settings.DownloadDestination
import dev.qtremors.acqua.data.settings.FolderOrganization
import dev.qtremors.acqua.domain.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadFoldersUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `folder policy drafts restore and only Done saves the chosen media type`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("download_destinations_v1", 0).edit().clear().commit()
        val store = DownloadDestinationStore(context)
        val restore = StateRestorationTester(compose)
        restore.setContent { MaterialTheme { DownloadFoldersSection(store) } }
        compose.onNodeWithText("Download folders").performClick()
        compose.onNodeWithText("Audio folder").performClick()
        compose.onNodeWithText("Hide from compatible galleries").assertDoesNotExist()
        compose.onNodeWithText("Group by website and collection").performScrollTo().performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Group by website and collection").assertIsSelected()
        assertEquals(FolderOrganization.NONE, store.get().audio.organization)
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Audio folder").performClick()
        compose.onNodeWithText("No extra subfolders").assertIsSelected()
        compose.onNodeWithText("Group by website").performScrollTo().performClick()
        compose.onNodeWithText("Done").performClick()
        compose.waitUntil { store.get().audio.organization == FolderOrganization.SOURCE }
        assertEquals(FolderOrganization.NONE, store.get().images.organization)
        assertEquals(FolderOrganization.NONE, store.get().video.organization)
    }

    @Test fun `custom folder gallery option is removed when using default and Cancel preserves the saved destination`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("download_destinations_v1", 0).edit().clear().commit()
        val store = DownloadDestinationStore(context)
        val custom = DownloadDestination("content://example.documents/tree/primary%3AMedia", hidden = true)
        store.put(MediaKind.AUDIO, custom)
        compose.setContent { MaterialTheme { DownloadFoldersSection(store) } }
        compose.onNodeWithText("Download folders").performClick()
        compose.onNodeWithText("Audio folder").performClick()
        compose.onNodeWithText("Hide from compatible galleries").performScrollTo().assertIsOn()
        compose.onNodeWithText("Use default Downloads").performScrollTo().performClick()
        compose.onNodeWithText("Hide from compatible galleries").assertDoesNotExist()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(custom, store.get().audio)
        compose.onNodeWithText("Audio folder").performClick()
        compose.onNodeWithText("Use default Downloads").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.waitUntil { store.get().audio == DownloadDestination() }
    }
}
