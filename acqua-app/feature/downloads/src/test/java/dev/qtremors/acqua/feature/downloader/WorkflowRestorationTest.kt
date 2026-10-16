package dev.qtremors.acqua.feature.downloader

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.data.history.HistoryEntry
import dev.qtremors.acqua.data.history.HistoryRepository
import dev.qtremors.acqua.data.settings.DownloadDestination
import dev.qtremors.acqua.data.settings.DownloadDestinations
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.downloader.VideoContainer
import dev.qtremors.acqua.feature.downloader.history.HistoryScreen
import dev.qtremors.acqua.feature.downloader.history.HistoryViewModel
import dev.qtremors.acqua.platform.FileActions
import dev.qtremors.acqua.platform.permissions.DownloadPermissionGate
import dev.qtremors.acqua.platform.permissions.DownloadPermissionPrompt
import dev.qtremors.acqua.platform.permissions.DownloadPermissionDialogs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WorkflowRestorationTest {
    @get:Rule val compose = createComposeRule()
    private val models = ViewModelStore()
    @After fun clear() { models.clear() }

    @Test fun `parent format controls restore the open dialog tab and unapplied draft`() {
        val state = mutableStateOf(DownloaderUiState(url = "https://example.com/video"))
        val restore = StateRestorationTester(compose)
        restore.setContent { MaterialTheme { Column { DownloadPickerControls(state.value) {
            state.value = state.value.copy(maximumVideoHeight = it.maximumVideoHeight, advancedOptions = it)
        } } } }
        compose.onNodeWithTag("download-summary-quality").performClick()
        compose.onNodeWithTag("download-picker-option-720").performScrollTo().performClick()
        compose.onNode(hasText("Container") and hasAnyAncestor(hasTestTag("download-picker-dialog"))).performClick()
        compose.onNodeWithTag("download-picker-option-MKV").performScrollTo().performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("download-picker-option-MKV").assertIsSelected()
        assertEquals(0, state.value.maximumVideoHeight)
        compose.onNodeWithTag("download-picker-done").performClick()
        assertEquals(720, state.value.maximumVideoHeight)
        assertEquals(VideoContainer.MKV, state.value.advancedOptions.videoContainer)
    }

    @Test fun `History selection survives recreation and record removal remains actionable`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = HistoryRepository(context).apply {
            clear()
            for (index in 1..2) add(HistoryEntry("fixture-$index", index.toLong(), "https://example.com/$index",
                "Fixture $index.mp4", "content://missing/$index", true, "video/mp4", 10, true))
        }
        val viewModel = HistoryViewModel(repository, SavedStateHandle())
        models.put("history", viewModel)
        val restore = StateRestorationTester(compose)
        restore.setContent { MaterialTheme { HistoryScreen(viewModel, FileActions(context), true, {}) } }
        compose.waitUntil(5000) { !viewModel.state.value.isLoading && viewModel.state.value.entries.size == 2 }
        compose.onNodeWithContentDescription(context.getString(R.string.hist_item_options, "Fixture 1.mp4")).performClick()
        compose.onNodeWithText("Select items").performClick()
        compose.onNodeWithText("1 selected").assertExists()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("1 selected").assertExists()
        compose.onNodeWithText("Select all results").performClick()
        compose.onNodeWithText("2 selected").assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.hist_remove)).performClick()
        compose.waitUntil(5000) { repository.load().isEmpty() }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(5000) { repository.load().size == 2 }
    }

    @Test fun `custom destinations avoid legacy permission for each selected media type`() {
        val destination = DownloadDestination("content://folders/tree/chosen")
        val destinations = DownloadDestinations(audio = destination)
        val state = DownloaderUiState(media = listOf(ResolvedMedia("https://example.com/video", MediaKind.VIDEO)))
        assertTrue(state.requiresLegacyStorage(destinations))
        assertFalse(state.copy(downloadContentType = DownloadContentType.AUDIO).requiresLegacyStorage(destinations))
        assertTrue(state.copy(media = null).requiresLegacyStorage(destinations))
        assertFalse(state.copy(media = null).requiresLegacyStorage(DownloadDestinations(destination, destination, destination)))
    }

    @Test fun `storage denial can be retried through settings without duplicating the pending action`() {
        val handle = SavedStateHandle()
        val gate = DownloadPermissionGate(handle)
        var actions = 0
        var requests = 0
        gate.hold(false) { actions++ }
        gate.show(DownloadPermissionPrompt.STORAGE_RATIONALE)
        compose.setContent { MaterialTheme {
            val prompt by gate.prompt.collectAsState()
            DownloadPermissionDialogs(prompt, gate::dismiss,
                { gate.markStorageRequested(); requests++; gate.show(DownloadPermissionPrompt.STORAGE_SETTINGS) },
                { gate.resumePending()?.action?.invoke() }, {}, gate::runPendingWithoutNotifications)
        } }
        compose.onNodeWithText("Try again").performClick()
        assertEquals(0, actions)
        assertEquals(1, requests)
        assertTrue(DownloadPermissionGate(handle).hasRequestedStorage)
        compose.onNodeWithText("Open settings").performClick()
        assertEquals(1, actions)
        gate.runPendingWithoutNotifications()
        assertEquals(1, actions)
    }

    @Test fun `single line link entry supports keyboard focus and clearing without starting work`() {
        val state = mutableStateOf(DownloaderUiState())
        var work = 0
        compose.setContent { MaterialTheme {
            BottomControlsSection(state.value, true, true, { state.value = state.value.copy(url = it) }, {}, { work++ }, { work++ })
        } }
        compose.onNode(hasSetTextAction()).performClick().assertIsFocused()
            .performTextReplacement("https://example.com/one\nhttps://example.com/two")
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Tab) }
        compose.onNodeWithContentDescription("Clear link").assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        assertEquals("", state.value.url)
        assertEquals(0, work)
    }
}
