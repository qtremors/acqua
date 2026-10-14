package dev.qtremors.acqua.feature.settings

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.data.settings.DownloadDestinationStore
import dev.qtremors.acqua.data.settings.FolderOrganization
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
        compose.onNodeWithText("Audio folder").performClick()
        compose.onNodeWithText("Hide from compatible galleries").assertIsNotEnabled()
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
}
