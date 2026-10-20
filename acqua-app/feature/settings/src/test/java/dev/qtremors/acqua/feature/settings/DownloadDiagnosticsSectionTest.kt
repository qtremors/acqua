package dev.qtremors.acqua.feature.settings

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.downloader.DownloadDiagnosticsStore
import dev.qtremors.acqua.downloader.DownloadFailure
import dev.qtremors.acqua.downloader.DownloadPhase
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadDiagnosticsSectionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `notification report is consumed once and open details survive recreation`() {
        val store = DownloadDiagnosticsStore(ApplicationProvider.getApplicationContext<Context>())
        store.clear()
        val id = UUID.randomUUID().toString()
        store.record(id, IllegalStateException("Temporary processing failure"), DownloadFailure.RUNTIME, DownloadPhase.PROCESSING, "job:PROCESSED")
        val initial = mutableStateOf<String?>(id)
        var consumed = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme { DownloadDiagnosticsSection(false, {}, initial.value, { initial.value = null; consumed++ }, null) }
        }
        awaitReport(id)
        assertEquals(1, consumed)
        restoration.emulateSavedInstanceStateRestore()
        awaitReport(id)
        assertEquals(1, consumed)
        compose.onNodeWithText("Close").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Copy diagnostics").assertDoesNotExist()
    }

    @Test fun `expired notification shows the empty list instead of another task report`() {
        DownloadDiagnosticsStore(ApplicationProvider.getApplicationContext<Context>()).clear()
        val initial = mutableStateOf<String?>(UUID.randomUUID().toString())
        compose.setContent {
            MaterialTheme { DownloadDiagnosticsSection(false, {}, initial.value, { initial.value = null }, null) }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("No recent failure reports.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Copy diagnostics").assertDoesNotExist()
    }

    private fun awaitReport(id: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Task: $id", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }
}
