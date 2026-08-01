package dev.qtremors.acqua.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.qtremors.acqua.data.history.HistoryEntry
import dev.qtremors.acqua.data.history.HistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HistoryUiState(
    val entries: List<HistoryEntry> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.MEDIA,
    val sort: HistorySort = HistorySort.NEWEST,
    val query: String = "",
    val missingEntryIds: Set<String> = emptySet()
) {
    val activeQuery: HistoryQuery
        get() = HistoryQuery(filter = filter, sort = sort, text = query)

    val filteredEntries: List<HistoryEntry>
        get() = entries.applyHistoryQuery(activeQuery)

    val summary: HistorySummary
        get() = entries.summarizeHistory(missingEntryIds)

    val emptyReason: HistoryEmptyReason
        get() = historyEmptyReason(entries, activeQuery)
}

class HistoryViewModel(private val repository: HistoryRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(HistoryUiState())
    val state = mutableState.asStateFlow()

    fun refresh() = viewModelScope.launch {
        val (entries, missing) = withContext(Dispatchers.IO) {
            val loaded = repository.load()
            loaded to loaded.asSequence()
                .filter(HistoryEntry::isDownloaded)
                .filterNot(repository::fileExists)
                .map(HistoryEntry::id)
                .toSet()
        }
        mutableState.value = mutableState.value.copy(entries = entries, missingEntryIds = missing)
    }

    fun selectFilter(filter: HistoryFilter) {
        mutableState.value = mutableState.value.copy(filter = filter)
    }

    fun setQuery(query: String) {
        mutableState.value = mutableState.value.copy(query = query)
    }

    fun selectSort(sort: HistorySort) {
        mutableState.value = mutableState.value.copy(sort = sort)
    }

    fun clear() = viewModelScope.launch {
        withContext(Dispatchers.IO) { repository.clear() }
        mutableState.value = mutableState.value.copy(entries = emptyList(), missingEntryIds = emptySet())
    }

    fun delete(id: String) = viewModelScope.launch {
        val entries = withContext(Dispatchers.IO) {
            repository.delete(id)
            repository.load()
        }
        mutableState.value = mutableState.value.copy(
            entries = entries,
            missingEntryIds = mutableState.value.missingEntryIds - id
        )
    }
}
