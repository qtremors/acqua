package dev.qtremors.acqua.feature.downloader.history

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.MainDispatcherRule
import dev.qtremors.acqua.data.history.HistoryEntry
import dev.qtremors.acqua.data.history.HistoryFileDeletionAttempt
import dev.qtremors.acqua.data.history.HistoryFileDeletionGateway
import dev.qtremors.acqua.data.history.HistoryFileDeletionStatus
import dev.qtremors.acqua.data.history.HistoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryDeletionControllerTest {
    @get:Rule val main = MainDispatcherRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = HistoryRepository(context)
    private val gateway = FakeDeletion()

    @Before fun setup() { repository.clear() }

    @Test fun `default record removal and undo leave providers untouched`() = runTest {
        val entry = entries(1).single()
        val model = HistoryViewModel(repository, SavedStateHandle(), main.dispatcher, gateway)
        val models = ViewModelStore().apply { put("history", model) }
        try {
            advanceUntilIdle()
            model.delete(entry.id)
            advanceUntilIdle()
            assertTrue(repository.load().isEmpty())
            assertTrue(gateway.calls.isEmpty())
            model.restore(listOf(entry))
            advanceUntilIdle()
            assertEquals(entry.fileUri, repository.find(entry.id)!!.fileUri)
            assertTrue(gateway.calls.isEmpty())
        } finally { models.clear() }
    }

    @Test fun `mixed multiselect keeps every record and reports independent outcomes`() = runTest {
        val entries = entries(5)
        gateway.attempt = { entry, _ ->
            if (entry.id == "4") error("Provider failed")
            HistoryFileDeletionAttempt.Finished(listOf(HistoryFileDeletionStatus.DELETED, HistoryFileDeletionStatus.MISSING,
                HistoryFileDeletionStatus.DENIED, HistoryFileDeletionStatus.FAILED)[entry.id.toInt()])
        }
        var changes = 0
        val controller = HistoryDeletionController(repository, gateway, SavedStateHandle(), this, main.dispatcher) { changes++ }
        controller.start(entries)
        controller.start(entries)
        advanceUntilIdle()
        assertEquals(5, gateway.calls.size)
        assertEquals(5, repository.load().size)
        assertEquals(listOf(HistoryFileDeletionStatus.DELETED, HistoryFileDeletionStatus.MISSING, HistoryFileDeletionStatus.DENIED,
            HistoryFileDeletionStatus.FAILED, HistoryFileDeletionStatus.FAILED), controller.state.value.results.map { it.status })
        assertFalse(controller.state.value.running)
        assertEquals(1, changes)
    }

    @Test fun `denied consent continues remaining items and cancellation never calls their providers`() = runTest {
        val entries = entries(3)
        gateway.attempt = { _, _ -> consent(autoDelete = true) }
        val controller = HistoryDeletionController(repository, gateway, SavedStateHandle(), this, main.dispatcher) {}
        controller.start(entries)
        advanceUntilIdle()
        controller.consentResult(false)
        controller.consentResult(false)
        advanceUntilIdle()
        assertEquals(HistoryFileDeletionStatus.DENIED, controller.state.value.results.single().status)
        assertNotNull(controller.state.value.consent)
        controller.cancel()
        advanceUntilIdle()
        assertEquals(listOf(HistoryFileDeletionStatus.DENIED, HistoryFileDeletionStatus.CANCELLED, HistoryFileDeletionStatus.CANCELLED),
            controller.state.value.results.map { it.status })
        assertEquals(listOf("0", "1"), gateway.calls.map { it.first })
        assertEquals(3, repository.load().size)
    }

    @Test fun `media approval verifies the deletion and legacy approval retries only the same file`() = runTest {
        val entries = entries(2)
        gateway.attempt = { entry, allow -> if (allow) consent(autoDelete = entry.id == "0") else HistoryFileDeletionAttempt.Finished(HistoryFileDeletionStatus.DELETED) }
        val controller = HistoryDeletionController(repository, gateway, SavedStateHandle(), this, main.dispatcher) {}
        controller.start(entries)
        advanceUntilIdle()
        controller.consentResult(true)
        advanceUntilIdle()
        assertEquals(listOf("0"), gateway.verified)
        controller.consentResult(true)
        advanceUntilIdle()
        assertEquals(listOf("0" to true, "1" to true, "1" to false), gateway.calls)
        assertTrue(controller.state.value.results.all { it.status == HistoryFileDeletionStatus.DELETED })
        assertTrue(controller.state.value.pending.isEmpty())
    }

    @Test fun `recreated operation pauses and preserves completed results and accepted file identity`() = runTest {
        val entries = entries(3)
        gateway.attempt = { entry, _ -> if (entry.id == "0") HistoryFileDeletionAttempt.Finished(HistoryFileDeletionStatus.DELETED) else consent(true) }
        val saved = SavedStateHandle()
        val original = HistoryDeletionController(repository, gateway, saved, this, main.dispatcher) {}
        original.start(entries)
        advanceUntilIdle()
        val restored = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        val recreated = HistoryDeletionController(repository, gateway, restored, this, main.dispatcher) {}
        advanceUntilIdle()
        assertFalse(recreated.state.value.running)
        assertEquals(2, gateway.calls.size)
        assertEquals("0", recreated.state.value.results.single().id)
        repository.add(entries[1].copy(fileUri = "content://other/new-file"))
        gateway.attempt = { _, _ -> HistoryFileDeletionAttempt.Finished(HistoryFileDeletionStatus.DELETED) }
        recreated.resume()
        advanceUntilIdle()
        assertEquals(listOf("0", "1", "2"), gateway.calls.map { it.first })
        assertEquals(listOf(HistoryFileDeletionStatus.DELETED, HistoryFileDeletionStatus.SKIPPED, HistoryFileDeletionStatus.DELETED),
            recreated.state.value.results.map { it.status })
    }

    @Test fun `changing a record while consent is open never deletes the replacement`() = runTest {
        val entry = entries(1).single()
        gateway.attempt = { _, _ -> consent(false) }
        val controller = HistoryDeletionController(repository, gateway, SavedStateHandle(), this, main.dispatcher) {}
        controller.start(listOf(entry))
        advanceUntilIdle()
        repository.add(entry.copy(fileUri = "content://other/new-file"))
        controller.consentResult(true)
        advanceUntilIdle()
        assertEquals(HistoryFileDeletionStatus.SKIPPED, controller.state.value.results.single().status)
        assertEquals(1, gateway.calls.size)
        assertTrue(gateway.verified.isEmpty())
    }

    private fun entries(count: Int) = List(count) { index ->
        HistoryEntry(index.toString(), index.toLong(), "https://example.com/$index", "file-$index.mp4", "content://deletion.test/$index", true, "video/mp4", 10, true)
            .also(repository::add)
    }

    private fun consent(autoDelete: Boolean) = HistoryFileDeletionAttempt.Consent(
        PendingIntent.getActivity(context, 0, Intent("deletion.permission"), PendingIntent.FLAG_IMMUTABLE).intentSender, autoDelete)

    private class FakeDeletion : HistoryFileDeletionGateway {
        val calls = mutableListOf<Pair<String, Boolean>>()
        val verified = mutableListOf<String>()
        var attempt: (HistoryEntry, Boolean) -> HistoryFileDeletionAttempt = { _, _ -> HistoryFileDeletionAttempt.Finished(HistoryFileDeletionStatus.DELETED) }
        override fun delete(entry: HistoryEntry, allowConsent: Boolean): HistoryFileDeletionAttempt {
            calls += entry.id to allowConsent
            return attempt(entry, allowConsent)
        }
        override fun verifyApprovedDeletion(entry: HistoryEntry): HistoryFileDeletionStatus {
            verified += entry.id
            return HistoryFileDeletionStatus.DELETED
        }
    }
}
