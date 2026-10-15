package dev.qtremors.acqua.feature.downloader.history

import android.content.IntentSender
import androidx.lifecycle.SavedStateHandle
import dev.qtremors.acqua.data.history.HistoryEntry
import dev.qtremors.acqua.data.history.HistoryFileDeletionAttempt
import dev.qtremors.acqua.data.history.HistoryFileDeletionGateway
import dev.qtremors.acqua.data.history.HistoryFileDeletionStatus
import dev.qtremors.acqua.data.history.HistoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HistoryDeletionResult(val id: String, val name: String, val status: HistoryFileDeletionStatus)
data class HistoryDeletionConsent(val sender: IntentSender, val deletesOnApproval: Boolean, val launched: Boolean = false)
data class HistoryDeletionState(
    val pending: List<String> = emptyList(),
    val results: List<HistoryDeletionResult> = emptyList(),
    val running: Boolean = false,
    val consent: HistoryDeletionConsent? = null
) { val visible get() = pending.isNotEmpty() || results.isNotEmpty() }

class HistoryDeletionController(
    private val repository: HistoryRepository,
    private val gateway: HistoryFileDeletionGateway,
    private val saved: SavedStateHandle,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val onChanged: () -> Unit
) {
    private val mutable = MutableStateFlow(HistoryDeletionState(
        pending = saved.get<ArrayList<String>>("history.delete.pending").orEmpty().take(MAX_FILES),
        results = restoredResults()
    ))
    val state = mutable.asStateFlow()
    private var cancellationRequested = false
    private val acceptedUris = saved.get<ArrayList<String>>("history.delete.uris").orEmpty()
        .chunked(2).filter { it.size == 2 }.associate { it[0] to it[1] }.toMutableMap()

    fun start(entries: List<HistoryEntry>) {
        if (mutable.value.visible) return
        val ids = entries.filter { it.isDownloaded && it.fileUri.isNotBlank() }.map { it.id }.distinct().take(MAX_FILES)
        if (ids.isEmpty()) return
        cancellationRequested = false
        acceptedUris.clear()
        entries.filter { it.id in ids }.forEach { acceptedUris[it.id] = it.fileUri }
        update(HistoryDeletionState(pending = ids))
        resume()
    }

    fun resume() {
        if (mutable.value.running || mutable.value.consent != null || mutable.value.pending.isEmpty()) return
        update(mutable.value.copy(running = true))
        scope.launch {
            while (mutable.value.pending.isNotEmpty()) {
                val id = mutable.value.pending.first()
                var entry: HistoryEntry? = null
                val attempt = safely {
                    entry = withContext(io) { repository.find(id) }
                    val accepted = entry
                    if (accepted == null || accepted.fileUri != acceptedUris[id]) HistoryFileDeletionAttempt.Finished(HistoryFileDeletionStatus.SKIPPED)
                    else if (cancellationRequested) HistoryFileDeletionAttempt.Finished(HistoryFileDeletionStatus.CANCELLED)
                    else withContext(io) { gateway.delete(accepted) }
                }
                when (attempt) {
                    is HistoryFileDeletionAttempt.Consent -> {
                        update(mutable.value.copy(running = false, consent = HistoryDeletionConsent(attempt.sender, attempt.deletesOnApproval)))
                        if (cancellationRequested) cancel()
                        return@launch
                    }
                    is HistoryFileDeletionAttempt.Finished -> finish(id, entry?.fileName.orEmpty(), attempt.status)
                }
            }
            update(mutable.value.copy(running = false))
            onChanged()
        }
    }

    fun markConsentLaunched() { mutable.value = mutable.value.copy(consent = mutable.value.consent?.copy(launched = true)) }

    fun consentResult(approved: Boolean) {
        val consent = mutable.value.consent ?: return
        val id = mutable.value.pending.firstOrNull() ?: return
        update(mutable.value.copy(consent = null, running = true))
        scope.launch {
            var entry: HistoryEntry? = null
            val attempt = safely {
                entry = withContext(io) { repository.find(id) }
                val accepted = entry
                val status = when {
                    !approved -> HistoryFileDeletionStatus.DENIED
                    accepted == null || accepted.fileUri != acceptedUris[id] -> HistoryFileDeletionStatus.SKIPPED
                    consent.deletesOnApproval -> withContext(io) { gateway.verifyApprovedDeletion(accepted) }
                    else -> (withContext(io) { gateway.delete(accepted, allowConsent = false) } as? HistoryFileDeletionAttempt.Finished)
                        ?.status ?: HistoryFileDeletionStatus.DENIED
                }
                HistoryFileDeletionAttempt.Finished(status)
            }
            finish(id, entry?.fileName.orEmpty(), (attempt as HistoryFileDeletionAttempt.Finished).status)
            update(mutable.value.copy(running = false))
            resume()
            if (mutable.value.pending.isEmpty()) onChanged()
        }
    }

    fun cancel() {
        cancellationRequested = true
        if (mutable.value.running) return
        update(mutable.value.copy(consent = null))
        resume()
    }

    fun dismiss() { if (!mutable.value.running && mutable.value.pending.isEmpty()) update(HistoryDeletionState()) }

    private fun finish(id: String, name: String, status: HistoryFileDeletionStatus) {
        acceptedUris.remove(id)
        update(mutable.value.copy(pending = mutable.value.pending.drop(1),
            results = mutable.value.results + HistoryDeletionResult(id, name, status)))
    }

    private fun update(state: HistoryDeletionState) {
        saved["history.delete.pending"] = ArrayList(state.pending)
        saved["history.delete.results"] = ArrayList(state.results.flatMap { listOf(it.id, it.name, it.status.name) })
        saved["history.delete.uris"] = ArrayList(acceptedUris.flatMap { listOf(it.key, it.value) })
        mutable.value = state
    }

    private fun restoredResults(): List<HistoryDeletionResult> = saved.get<ArrayList<String>>("history.delete.results").orEmpty()
        .take(MAX_FILES * 3).chunked(3).mapNotNull { row ->
            if (row.size != 3) null else runCatching { HistoryDeletionResult(row[0], row[1], HistoryFileDeletionStatus.valueOf(row[2])) }.getOrNull()
        }

    private suspend fun safely(action: suspend () -> HistoryFileDeletionAttempt): HistoryFileDeletionAttempt = try { action() }
        catch (error: CancellationException) { throw error }
        catch (_: RuntimeException) { HistoryFileDeletionAttempt.Finished(HistoryFileDeletionStatus.FAILED) }

    companion object { const val MAX_FILES = 500 }
}
