package dev.qtremors.acqua.feature.downloader

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.downloader.DownloadDiagnostic
import dev.qtremors.acqua.downloader.DownloadDiagnosticsStore
import dev.qtremors.acqua.downloader.DownloadFailure
import dev.qtremors.acqua.downloader.DownloadPhase
import dev.qtremors.acqua.ui.components.DownloadDiagnosticDialog
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadDiagnosticDialogTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `copy uses the redacted stored report and help opens the project without sending report data`() {
        val record = record()
        compose.setContent { MaterialTheme { DownloadDiagnosticDialog(record, {}) } }
        compose.onNodeWithText("Copy diagnostics").performClick()
        val copied = context.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
        assertEquals(record.copyText(), copied)
        assertFalse(copied.contains("privateToken"))
        compose.onNodeWithText("Get help").performClick()
        val opened = Shadows.shadowOf(context.applicationContext as Application).nextStartedActivity
        assertEquals("https://github.com/qtremors/acqua/issues", opened.dataString)
        assertFalse(opened.toUri(0).contains(record.id))
    }

    @Test fun `retry accepts one action and closes details after the request is queued`() {
        val pending = CompletableDeferred<Boolean>()
        val visible = mutableStateOf(true)
        var calls = 0
        val record = record()
        compose.setContent {
            MaterialTheme { if (visible.value) DownloadDiagnosticDialog(record, { visible.value = false }) { calls++; pending.await() } }
        }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Retry").assertIsNotEnabled()
        assertEquals(1, calls)
        pending.complete(true)
        compose.waitForIdle()
        assertFalse(visible.value)
        assertTrue(pending.isCompleted)
    }

    private fun record(): DownloadDiagnostic {
        val store = DownloadDiagnosticsStore(context)
        val id = UUID.randomUUID().toString()
        store.record(id, IllegalStateException("token=privateToken"), DownloadFailure.UNKNOWN, DownloadPhase.TRANSFER, "worker:DIRECT")
        return requireNotNull(store.get(id))
    }
}
