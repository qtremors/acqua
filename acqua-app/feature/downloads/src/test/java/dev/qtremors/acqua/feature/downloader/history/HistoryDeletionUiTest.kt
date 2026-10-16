package dev.qtremors.acqua.feature.downloader.history

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.data.history.HistoryEntry
import dev.qtremors.acqua.data.history.HistoryFileStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryDeletionUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val entry = HistoryEntry("fixture", 1, "https://example.com/post", "fixture.mp4", "content://fixture/1", true, "video/mp4", 10, true)

    @Test fun `record removal remains separate from file deletion`() {
        var recordsRemoved = 0
        var filesRequested = 0
        compose.setContent {
            MaterialTheme {
                HistoryRow(entry, HistoryFileStatus.AVAILABLE, false, false,
                    onClick = {}, onLongClick = {}, onToggle = {}, onShare = {}, onRemove = { recordsRemoved++ },
                    onDetails = {}, onSource = {}, onRefetch = {}, onRetry = {}, onDeleteFile = { filesRequested++ })
            }
        }
        compose.onNodeWithContentDescription(context.getString(dev.qtremors.acqua.core.data.R.string.hist_item_options, entry.fileName)).performClick()
        compose.onNodeWithText("Remove from history").performClick()
        assertEquals(1, recordsRemoved)
        assertEquals(0, filesRequested)
        compose.onNodeWithContentDescription(context.getString(dev.qtremors.acqua.core.data.R.string.hist_item_options, entry.fileName)).performClick()
        compose.onNodeWithText("Delete files").performClick()
        assertEquals(1, filesRequested)
        assertEquals(1, recordsRemoved)
    }

    @Test fun `file deletion confirmation explains permanence and cancel never confirms`() {
        val visible = mutableStateOf(true)
        var confirmed = false
        compose.setContent {
            MaterialTheme { if (visible.value) HistoryDeleteFilesConfirmation(2, { visible.value = false }, { confirmed = true }) }
        }
        compose.onNodeWithText("Deleted files cannot be restored with Undo.", substring = true).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertFalse(confirmed)
        assertFalse(visible.value)
    }
}
