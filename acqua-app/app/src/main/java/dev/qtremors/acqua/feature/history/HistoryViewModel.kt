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

enum class HistoryFilter { MEDIA, PHOTOS, VIDEOS, AUDIO, LINKS }

data class HistoryUiState(
    val entries: List<HistoryEntry> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.MEDIA
) {
    val filteredEntries: List<HistoryEntry>
        get() = when (filter) {
            HistoryFilter.MEDIA -> entries.filter(HistoryEntry::isDownloaded)
            HistoryFilter.PHOTOS -> entries.filter { it.isDownloaded && !it.isVideo && !it.isAudio }
            HistoryFilter.VIDEOS -> entries.filter { it.isDownloaded && it.isVideo }
            HistoryFilter.AUDIO -> entries.filter(HistoryEntry::isAudio)
            HistoryFilter.LINKS -> entries.filterNot(HistoryEntry::isDownloaded)
        }
}

class HistoryViewModel(private val repository: HistoryRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(HistoryUiState())
    val state = mutableState.asStateFlow()

    fun refresh() = viewModelScope.launch {
        val entries = withContext(Dispatchers.IO) { repository.load() }
        mutableState.value = mutableState.value.copy(entries = entries)
    }

    fun selectFilter(filter: HistoryFilter) {
        mutableState.value = mutableState.value.copy(filter = filter)
    }

    fun clear() = viewModelScope.launch {
        withContext(Dispatchers.IO) { repository.clear() }
        mutableState.value = mutableState.value.copy(entries = emptyList())
    }

    fun delete(id: String) = viewModelScope.launch {
        val entries = withContext(Dispatchers.IO) {
            repository.delete(id)
            repository.load()
        }
        mutableState.value = mutableState.value.copy(entries = entries)
    }
}
