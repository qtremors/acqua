package dev.qtremors.acqua.feature.downloader.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.qtremors.acqua.data.history.HistoryEntry
import dev.qtremors.acqua.data.history.HistoryCategory
import dev.qtremors.acqua.data.history.HistoryDatabaseSummary
import dev.qtremors.acqua.data.history.HistoryFileStatus
import dev.qtremors.acqua.data.history.HistoryOrder
import dev.qtremors.acqua.data.history.HistoryPageRequest
import dev.qtremors.acqua.data.history.HistoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val loadFailed: Boolean = false,
    val queryAppliedByRepository: Boolean = false,
    val databaseSummary: HistorySummary? = null
) {
    val activeQuery get() = HistoryQuery(filter = filter, sort = sort, text = query)
    val filteredEntries by lazy {
        if (queryAppliedByRepository) entries else entries.applyHistoryQuery(activeQuery, missingEntryIds)
    }
    val summary by lazy { databaseSummary ?: entries.summarizeHistory(missingEntryIds) }
    val emptyReason get() = when {
        summary.totalEntries == 0 -> HistoryEmptyReason.NO_HISTORY
        activeQuery.isSearching -> HistoryEmptyReason.NO_SEARCH_RESULTS
        else -> HistoryEmptyReason.NO_FILTER_RESULTS
    }
}

sealed interface HistoryEvent {
    data class Removed(val entries: List<HistoryEntry>) : HistoryEvent
    data object Failed : HistoryEvent
}

class HistoryViewModel(
    private val repository: HistoryRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        HistoryUiState(
            filter = savedStateHandle.get<String>(KEY_FILTER)
                ?.let { runCatching { HistoryFilter.valueOf(it) }.getOrNull() }
                ?: HistoryFilter.MEDIA,
            sort = savedStateHandle.get<String>(KEY_SORT)
                ?.let { runCatching { HistorySort.valueOf(it) }.getOrNull() }
                ?: HistorySort.NEWEST,
            query = savedStateHandle[KEY_QUERY] ?: ""
        )
    )
    val state = mutableState.asStateFlow()
    private val messages = Channel<HistoryEvent>(Channel.UNLIMITED)
    val events = messages.receiveAsFlow()
    private val operations = Mutex()
    private var loadedRevision = -1L
    private var queryJob: Job? = null

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
            val result = withContext(Dispatchers.IO) {
                if (mutableState.value.filter == HistoryFilter.UNAVAILABLE) refreshUnknownStatuses()
                loadPage(offset = 0)
            }
            mutableState.value = mutableState.value.copy(
                entries = result.entries,
                fileStatuses = result.statuses,
                missingEntryIds = result.statuses.filterValues { it != HistoryFileStatus.AVAILABLE }.keys,
                isLoading = false,
                isLoadingMore = false,
                hasMore = result.hasMore,
                queryAppliedByRepository = true,
                databaseSummary = result.summary
            )
            loadedRevision = revision
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(isLoading = false, loadFailed = true)
        }
    }

    fun selectFilter(filter: HistoryFilter) {
        savedStateHandle[KEY_FILTER] = filter.name
        mutableState.value = mutableState.value.copy(filter = filter)
        reloadForQuery()
    }

    fun setQuery(query: String) {
        savedStateHandle[KEY_QUERY] = query
        mutableState.value = mutableState.value.copy(query = query)
        reloadForQuery(debounce = true)
    }

    fun selectSort(sort: HistorySort) {
        savedStateHandle[KEY_SORT] = sort.name
        mutableState.value = mutableState.value.copy(sort = sort)
        reloadForQuery()
    }

    fun resetFilters() {
        savedStateHandle[KEY_QUERY] = ""
        savedStateHandle[KEY_FILTER] = HistoryFilter.MEDIA.name
        mutableState.value = mutableState.value.copy(query = "", filter = HistoryFilter.MEDIA)
        reloadForQuery()
    }

    fun loadNextPage() {
        val snapshot = mutableState.value
        if (snapshot.isLoading || snapshot.isLoadingMore || !snapshot.hasMore) return
        viewModelScope.launch {
            operations.withLock {
                mutableState.value = mutableState.value.copy(isLoadingMore = true)
                try {
                    val result = withContext(Dispatchers.IO) {
                        if (mutableState.value.filter == HistoryFilter.UNAVAILABLE) refreshUnknownStatuses()
                        loadPage(offset = mutableState.value.entries.size)
                    }
                    mutableState.value = mutableState.value.copy(
                        entries = mutableState.value.entries + result.entries,
                        fileStatuses = mutableState.value.fileStatuses + result.statuses,
                        missingEntryIds = (mutableState.value.fileStatuses + result.statuses)
                            .filterValues { it != HistoryFileStatus.AVAILABLE }.keys,
                        isLoadingMore = false,
                        hasMore = result.hasMore,
                        databaseSummary = result.summary
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    mutableState.value = mutableState.value.copy(isLoadingMore = false, loadFailed = true)
                }
            }
        }
    }

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

    private fun reloadForQuery(debounce: Boolean = false) {
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            if (debounce) delay(250)
            operations.withLock { reload() }
        }
    }

    private fun refreshUnknownStatuses() {
        val unknown = repository.loadUnknownFileStatusEntries()
        repository.updateFileStatuses(unknown.associate { it.id to repository.fileStatus(it) })
    }

    private fun loadPage(offset: Int): LoadedHistoryPage {
        val snapshot = mutableState.value
        val page = repository.loadPage(
            HistoryPageRequest(
                category = snapshot.filter.toCategory(),
                order = snapshot.sort.toOrder(),
                terms = snapshot.activeQuery.normalizedTerms,
                limit = PAGE_SIZE,
                offset = offset
            )
        )
        val statuses = page.entries.filter(HistoryEntry::isDownloaded)
            .associate { it.id to repository.fileStatus(it) }
        repository.updateFileStatuses(statuses)
        val entries = if (snapshot.filter == HistoryFilter.UNAVAILABLE) {
            page.entries.filter { statuses[it.id] != HistoryFileStatus.AVAILABLE }
        } else page.entries
        return LoadedHistoryPage(
            entries = entries,
            statuses = statuses,
            hasMore = page.hasMore ||
                (snapshot.filter == HistoryFilter.UNAVAILABLE && repository.hasUnknownFileStatuses()),
            summary = repository.summary().toUiSummary()
        )
    }

    private data class LoadedHistoryPage(
        val entries: List<HistoryEntry>,
        val statuses: Map<String, HistoryFileStatus>,
        val hasMore: Boolean,
        val summary: HistorySummary
    )

    private companion object {
        const val KEY_FILTER = "history.filter"
        const val KEY_SORT = "history.sort"
        const val KEY_QUERY = "history.query"
        const val PAGE_SIZE = 100
    }
}

private fun HistoryFilter.toCategory(): HistoryCategory = when (this) {
    HistoryFilter.MEDIA -> HistoryCategory.MEDIA
    HistoryFilter.PHOTOS -> HistoryCategory.PHOTOS
    HistoryFilter.VIDEOS -> HistoryCategory.VIDEOS
    HistoryFilter.AUDIO -> HistoryCategory.AUDIO
    HistoryFilter.LINKS -> HistoryCategory.LINKS
    HistoryFilter.UNAVAILABLE -> HistoryCategory.UNAVAILABLE
}

private fun HistorySort.toOrder(): HistoryOrder = when (this) {
    HistorySort.NEWEST -> HistoryOrder.NEWEST
    HistorySort.OLDEST -> HistoryOrder.OLDEST
    HistorySort.LARGEST -> HistoryOrder.LARGEST
}

private fun HistoryDatabaseSummary.toUiSummary() = HistorySummary(
    downloads = downloads,
    links = links,
    photos = photos,
    videos = videos,
    audio = audio,
    missing = unavailable,
    storedBytes = storedBytes
)
