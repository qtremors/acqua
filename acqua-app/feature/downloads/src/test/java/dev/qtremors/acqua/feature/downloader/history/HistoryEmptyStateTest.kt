package dev.qtremors.acqua.feature.downloader.history

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HistoryEmptyStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `a fresh database shows first use help`() {
        content(HistoryUiState(databaseSummary = HistorySummary()))
        compose.onNodeWithText("No history records found").assertIsDisplayed()
        compose.onNodeWithText("Reset filters").assertDoesNotExist()
    }

    @Test fun `an empty repository search keeps search messaging and recovery actions`() {
        var resets = 0
        var showLinks = 0
        compose.setContent {
            MaterialTheme {
                HistoryEmptyState(HistoryUiState(query = "no match", databaseSummary = HistorySummary(downloads = 1, links = 1)),
                    onResetFilters = { resets++ }, onShowLinks = { showLinks++ })
            }
        }
        compose.onNodeWithText("No matching results in this category").assertIsDisplayed()
        compose.onNodeWithText("No history records found").assertDoesNotExist()
        compose.onNodeWithText("Reset filters").performClick()
        compose.onNodeWithText("Show links").performClick()
        assertEquals(1, resets)
        assertEquals(1, showLinks)
    }

    @Test fun `an empty category uses global history and keeps reset available`() {
        content(HistoryUiState(filter = HistoryFilter.AUDIO, databaseSummary = HistorySummary(downloads = 1)))
        compose.onNodeWithText("No audio yet").assertIsDisplayed()
        compose.onNodeWithText("Reset filters").assertIsDisplayed()
        compose.onNodeWithText("No history records found").assertDoesNotExist()
    }

    private fun content(state: HistoryUiState) {
        compose.setContent { MaterialTheme { HistoryEmptyState(state, {}, {}) } }
    }
}
