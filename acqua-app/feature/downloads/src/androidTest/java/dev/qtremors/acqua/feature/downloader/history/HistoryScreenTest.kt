package dev.qtremors.acqua.feature.downloader.history

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.qtremors.acqua.data.history.HistoryEntry
import dev.qtremors.acqua.data.history.HistoryFileStatus
import dev.qtremors.acqua.data.history.HistoryRepository
import dev.qtremors.acqua.platform.FileActions
import dev.qtremors.acqua.ui.theme.AcquaTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = ViewModelStore()
    private val repository = HistoryRepository(context)
    private val entry = HistoryEntry("screen-test", 1, "https://example.com/photo", "Beach.jpg", "content://missing/photo", false, "image/jpeg", 100, true)

    @Before fun prepare() { repository.clear() }
    @After fun cleanUp() { compose.runOnIdle { store.clear() }; repository.clear() }

    @Test fun tappingTheFileTitleInvokesThePrimaryAction() {
        var opened = 0
        compose.setContent {
            AcquaTheme {
                HistoryRow(entry, HistoryFileStatus.AVAILABLE, false, false,
                    onClick = { opened++ }, onLongClick = {}, onToggle = {}, onShare = {},
                    onRemove = {}, onDetails = {}, onSource = {}, onRefetch = {}, onRetry = {})
            }
        }
        compose.onNodeWithText("Beach.jpg").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun emptyAudioCategoryDoesNotClaimSearchFailed() {
        repository.add(entry)
        lateinit var model: HistoryViewModel
        compose.runOnIdle { model = HistoryViewModel(repository, SavedStateHandle()); store.put("history", model); model.selectFilter(HistoryFilter.AUDIO) }
        compose.setContent { AcquaTheme { HistoryScreen(model, FileActions(context), true, {}) } }
        compose.waitUntil(5_000) { !model.state.value.isLoading }
        compose.onNodeWithText("No audio yet").assertIsDisplayed()
        compose.onNodeWithText("Reset filters").performClick()
        compose.onNodeWithText("Beach.jpg").assertIsDisplayed()
    }

    @Test fun newDownloadsAppearAndRemovalCanBeUndone() {
        lateinit var model: HistoryViewModel
        compose.runOnIdle { model = HistoryViewModel(repository, SavedStateHandle()); store.put("history", model) }
        compose.setContent { AcquaTheme { HistoryScreen(model, FileActions(context), true, {}) } }
        compose.waitUntil(5_000) { !model.state.value.isLoading }
        HistoryRepository(context).add(entry)
        compose.waitUntil(5_000) { model.state.value.entries.any { it.id == entry.id } }
        compose.onNodeWithText("Beach.jpg").assertIsDisplayed()
        compose.runOnIdle { model.delete(entry.id) }
        compose.waitUntil(5_000) { model.state.value.entries.isEmpty() && !model.state.value.isLoading }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(5_000) { model.state.value.entries.any { it.id == entry.id } }
        assertEquals(listOf(entry), repository.load())
    }
}
