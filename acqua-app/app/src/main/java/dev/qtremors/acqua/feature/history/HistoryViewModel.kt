package dev.qtremors.acqua.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.qtremors.acqua.data.history.HistoryEntry
import dev.qtremors.acqua.data.history.HistoryFileStatus
import dev.qtremors.acqua.data.history.HistoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class HistoryUiState(
    val entries: List<HistoryEntry> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.MEDIA,
    val sort: HistorySort = HistorySort.NEWEST,
    val query: String = "",
    val missingEntryIds: Set<String> = emptySet(),
    val fileStatuses: Map<String, HistoryFileStatus> = emptyMap(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val groupBySource: Boolean = false
) {
    val activeQuery get() = HistoryQuery(filter = filter, sort = sort, text = query)
    val filteredEntries by lazy { entries.applyHistoryQuery(activeQuery, missingEntryIds) }
    val summary by lazy { entries.summarizeHistory(missingEntryIds) }
    val emptyReason get() = historyEmptyReason(entries, activeQuery)
}

sealed interface HistoryEvent {
    data class Removed(val entries: List<HistoryEntry>) : HistoryEvent
    data object Failed : HistoryEvent
}

class HistoryViewModel(private val repository: HistoryRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(HistoryUiState())
    val state = mutableState.asStateFlow()
    private val messages = Channel<HistoryEvent>(Channel.UNLIMITED)
    val events = messages.receiveAsFlow()
    private val operations = Mutex()
    private var loadedRevision = -1L

    init {
        viewModelScope.launch {
            repository.changes.collectLatest { revision ->
                operations.withLock { if (loadedRevision != revision) reload() }
            }
        }
    }

    fun refresh() = viewModelScope.launch { operations.withLock { reload() } }

    private suspend fun reload() {
        val revision = repository.changes.value
        mutableState.value = mutableState.value.copy(isLoading = true, loadFailed = false)
        try {
            val (entries, statuses) = withContext(Dispatchers.IO) {
                val loaded = repository.load()
                loaded to loaded.filter(HistoryEntry::isDownloaded).associate { it.id to repository.fileStatus(it) }
            }
            mutableState.value = mutableState.value.copy(
                entries = entries, fileStatuses = statuses,
                missingEntryIds = statuses.filterValues { it != HistoryFileStatus.AVAILABLE }.keys,
                isLoading = false
            )
            loadedRevision = revision
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(isLoading = false, loadFailed = true)
        }
    }

    fun selectFilter(filter: HistoryFilter) { mutableState.value = mutableState.value.copy(filter = filter) }
    fun setQuery(query: String) { mutableState.value = mutableState.value.copy(query = query) }
    fun selectSort(sort: HistorySort) { mutableState.value = mutableState.value.copy(sort = sort) }
    fun setGrouping(enabled: Boolean) { mutableState.value = mutableState.value.copy(groupBySource = enabled) }
    fun resetFilters() { mutableState.value = mutableState.value.copy(query = "", filter = HistoryFilter.MEDIA) }

    fun clear() = remove(null)
    fun delete(id: String) = remove(setOf(id))
    fun remove(ids: Set<String>?) = viewModelScope.launch {
        operations.withLock {
            try {
                val removed = withContext(Dispatchers.IO) { repository.remove(ids) }
                reload()
                if (removed.isNotEmpty()) messages.send(HistoryEvent.Removed(removed))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                messages.send(HistoryEvent.Failed)
            }
        }
    }

    fun restore(entries: List<HistoryEntry>) = viewModelScope.launch {
        operations.withLock {
            try {
                withContext(Dispatchers.IO) { repository.restore(entries) }
                reload()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                messages.send(HistoryEvent.Failed)
            }
        }
    }
}
