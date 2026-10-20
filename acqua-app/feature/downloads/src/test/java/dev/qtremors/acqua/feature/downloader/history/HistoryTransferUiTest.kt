package dev.qtremors.acqua.feature.downloader.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.data.history.HistoryEntry
import dev.qtremors.acqua.data.history.HistoryRepository
import dev.qtremors.acqua.data.history.HistoryTransfer
import dev.qtremors.acqua.data.history.HistoryTransferFormat
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryTransferUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = HistoryRepository(context)
    private val transfer = HistoryTransfer(context, repository)
    private val clipboard = context.getSystemService(ClipboardManager::class.java)
    @Before fun reset() { repository.clear() }

    @Test fun `clipboard preview restores and only explicit apply changes original History`() {
        val original = entry("one", false)
        repository.add(original)
        clipboard.setPrimaryClip(ClipData.newPlainText("fixture", "https://example.com/one\nhttps://example.com/two"))
        val restore = StateRestorationTester(compose)
        restore.setContent { MaterialTheme { HistoryTransferSection(transfer, null) } }
        compose.onNode(hasText("Import or export History") and hasClickAction()).performClick()
        compose.onNodeWithText("Paste import").performScrollTo().performClick()
        awaitApply()
        assertEquals(listOf(original), repository.load())
        compose.onNodeWithText("1 new records · 1 duplicates skipped · 0 files unavailable").assertExists()
        restore.emulateSavedInstanceStateRestore()
        awaitApply()
        assertEquals(listOf(original), repository.load())
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf(original), repository.load())
        compose.onNode(hasText("Import or export History") and hasClickAction()).performClick()
        compose.onNodeWithText("Paste import").performScrollTo().performClick()
        awaitApply()
        compose.onNodeWithText("Apply import").performScrollTo().performClick()
        compose.waitUntil(5000) { repository.load().size == 2 }
        assertEquals(original, repository.find(original.id))
    }

    @Test fun `unavailable import choice survives recreation and selected clipboard export is exact`() {
        val imported = entry("external", true)
        repository.add(imported)
        val document = transfer.export(HistoryTransferFormat.JSON)
        repository.clear()
        val original = entry("original", false)
        repository.add(original)
        clipboard.setPrimaryClip(ClipData.newPlainText("fixture", document))
        val restore = StateRestorationTester(compose)
        restore.setContent { MaterialTheme { HistoryTransferSection(transfer, setOf(original.id)) } }
        compose.onNodeWithText("Export or relink selected").performClick()
        compose.onNodeWithText("Copy export").performClick()
        compose.waitUntil(5000) { clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.startsWith('{') == true &&
            JSONObject(clipboard.primaryClip!!.getItemAt(0).text.toString()).getJSONArray("entries").getJSONObject(0).getString("id") == original.id }
        assertEquals(1, JSONObject(clipboard.primaryClip!!.getItemAt(0).text.toString()).getJSONArray("entries").length())
        clipboard.setPrimaryClip(ClipData.newPlainText("fixture", document))
        compose.onNodeWithText("Paste import").performScrollTo().performClick()
        awaitApply()
        compose.onNodeWithText("Import as links").performScrollTo().performClick()
        restore.emulateSavedInstanceStateRestore()
        awaitApply()
        compose.onNodeWithText("Apply import").performScrollTo().performClick()
        compose.waitUntil(5000) { repository.find(imported.id) != null }
        val result = repository.find(imported.id)!!
        assertFalse(result.isDownloaded)
        assertEquals("", result.fileUri)
        assertEquals(imported.timestamp, result.timestamp)
        assertEquals(imported.title, result.title)
    }

    private fun awaitApply() = compose.waitUntil(5000) { compose.onAllNodesWithText("Apply import").fetchSemanticsNodes().isNotEmpty() }
    private fun entry(id: String, downloaded: Boolean) = HistoryEntry(id, 1234567890123L, "https://example.com/$id", "$id.mp4",
        if (downloaded) "content://ungranted.test/$id" else "", true, "video/mp4", 123, downloaded,
        title = "Title $id", author = "Author", sourceHost = "example.com")
}
