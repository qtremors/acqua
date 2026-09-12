package dev.qtremors.acqua.feature.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import java.util.TimeZone
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.qtremors.acqua.R
import dev.qtremors.acqua.data.history.HistoryEntry
import dev.qtremors.acqua.platform.FileActions
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.ui.scrollbar.AcquaFastScrollbar
import dev.qtremors.acqua.ui.scrollbar.LazyListScrollbarState

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel, fileActions: FileActions, active: Boolean,
    onRefetch: (String) -> Unit, modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val visible = remember(state.entries, state.activeQuery, state.missingEntryIds) { state.filteredEntries }
    val listState = rememberLazyListState()
    val scrollbar = remember(listState) { LazyListScrollbarState(listState) }
    val snackbar = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }
    var statistics by remember { mutableStateOf(false) }
    var detailsId by rememberSaveable { mutableStateOf<String?>(null) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    val zone = TimeZone.getDefault()
    val displayItems = remember(visible, state.sort, state.groupBySource, expanded, currentTime, zone) {
        historyListItems(visible, state.sort, state.groupBySource, expanded, currentTime, zone)
    }
    val selectedEntries = visible.filter { it.id in selected }
    val canShare = selectedEntries.isNotEmpty() && selectedEntries.all { it.isDownloaded && it.id !in state.missingEntryIds }
    val currentActive by rememberUpdatedState(active)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && currentActive) {
                currentTime = System.currentTimeMillis()
                if (!viewModel.state.value.isLoading) viewModel.refresh()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(active) {
        if (active) {
            currentTime = System.currentTimeMillis()
            if (!state.isLoading) viewModel.refresh()
        }
    }
    LaunchedEffect(state.query, state.filter, state.sort, state.groupBySource) {
        listState.scrollToItem(0)
        selected = emptySet()
        selecting = false
    }
    LaunchedEffect(state.entries) { if (detailsId != null && state.entries.none { it.id == detailsId }) detailsId = null }
    LaunchedEffect(visible) {
        selected = selected.intersect(visible.mapTo(mutableSetOf(), HistoryEntry::id))
        if (visible.isEmpty()) selecting = false
    }
    LaunchedEffect(active, viewModel) {
        if (!active) return@LaunchedEffect
        viewModel.events.collect { event ->
            when (event) {
                is HistoryEvent.Removed -> {
                    val result = snackbar.showSnackbar(
                        context.resources.getQuantityString(R.plurals.hist_removed, event.entries.size, event.entries.size),
                        actionLabel = context.getString(R.string.hist_undo),
                        withDismissAction = true, duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.restore(event.entries)
                }
                HistoryEvent.Failed -> snackbar.showSnackbar(context.getString(R.string.hist_failed))
            }
        }
    }
    BackHandler(enabled = active && selecting) { selecting = false; selected = emptySet() }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text(stringResource(R.string.clear_history)) },
        text = { Text(stringResource(R.string.clear_history_confirmation)) },
        confirmButton = { TextButton(onClick = { confirmClear = false; selecting = false; selected = emptySet(); viewModel.clear() }) {
            Text(stringResource(R.string.hist_remove), color = MaterialTheme.colorScheme.error)
        } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } }
    )
    if (statistics) AlertDialog(
        onDismissRequest = { statistics = false }, title = { Text(stringResource(R.string.hist_statistics)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            HistoryStatistics(state.summary) {
                statistics = false
                viewModel.setQuery("")
                viewModel.selectFilter(HistoryFilter.UNAVAILABLE)
            }
        } },
        confirmButton = { TextButton(onClick = { statistics = false }) { Text(stringResource(R.string.close)) } }
    )
    state.entries.firstOrNull { it.id == detailsId }?.let { entry ->
        HistoryDetailsSheet(entry, state.fileStatuses[entry.id], onDismiss = { detailsId = null }, fileActions = fileActions, onRefetch = {
            detailsId = null; onRefetch(entry.url)
        }, onRetry = { viewModel.refresh() })
    }

    Box(modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())) {
        Column(Modifier.fillMaxSize()) {
            if (selecting) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selecting = false; selected = emptySet() }) { Icon(Icons.Default.Close, stringResource(R.string.hist_cancel_selection)) }
                    Text(stringResource(R.string.hist_selected, selected.size), Modifier.weight(1f))
                    IconButton(onClick = { fileActions.shareMany(selectedEntries) }, enabled = canShare) {
                        Icon(Icons.Default.Share, stringResource(R.string.hist_share_selected))
                    }
                    IconButton(onClick = { viewModel.remove(selected); selected = emptySet(); selecting = false }, enabled = selected.isNotEmpty()) {
                        Icon(Icons.Default.DeleteOutline, stringResource(R.string.hist_remove))
                    }
                }
                TextButton(onClick = { selected = visible.mapTo(mutableSetOf(), HistoryEntry::id) }) { Text(stringResource(R.string.hist_select_all)) }
                if (selectedEntries.isNotEmpty() && !canShare) Text(stringResource(R.string.hist_share_hint), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
            } else {
                HistorySearchAndFilters(state, viewModel::setQuery, viewModel::selectFilter, viewModel::selectSort,
                    viewModel::setGrouping, onStatistics = { statistics = true }, onSelect = { selecting = true },
                    onRefresh = { viewModel.refresh() }, onClear = { confirmClear = true })
            }
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.loadFailed) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.hist_load_failed), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.refresh() }) { Text(stringResource(R.string.hist_retry)) }
                }
            }
            Box(Modifier.weight(1f)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (visible.isEmpty() && !state.isLoading && !state.loadFailed) item("empty") {
                        val title = when {
                            state.entries.isEmpty() -> R.string.no_history
                            state.activeQuery.isSearching -> R.string.hist_no_matches
                            else -> when (state.filter) {
                                HistoryFilter.MEDIA -> R.string.hist_no_downloads
                                HistoryFilter.PHOTOS -> R.string.hist_no_photos
                                HistoryFilter.VIDEOS -> R.string.hist_no_videos
                                HistoryFilter.AUDIO -> R.string.hist_no_audio
                                HistoryFilter.LINKS -> R.string.hist_no_links
                                HistoryFilter.UNAVAILABLE -> R.string.hist_no_unavailable
                            }
                        }
                        Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(if (state.entries.isEmpty()) R.string.hist_empty_help else if (state.activeQuery.isSearching) R.string.hist_search_help else R.string.hist_category_help), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
                            if (state.entries.isNotEmpty()) {
                                TextButton(onClick = viewModel::resetFilters) { Text(stringResource(R.string.hist_reset)) }
                                if (state.summary.links > 0 && state.filter != HistoryFilter.LINKS) TextButton(onClick = { viewModel.setQuery(""); viewModel.selectFilter(HistoryFilter.LINKS) }) { Text(stringResource(R.string.hist_show_links)) }
                            }
                        }
                    }
                    items(displayItems, key = { it.key }) { item ->
                        when (item) {
                            is HistoryListItem.Day -> Text(stringResource(when (item.day) {
                                HistoryDay.TODAY -> R.string.hist_today
                                HistoryDay.YESTERDAY -> R.string.hist_yesterday
                                HistoryDay.EARLIER -> R.string.hist_earlier
                            }), Modifier.padding(top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall)
                            is HistoryListItem.Group -> {
                                val isExpanded = item.key in expanded
                                Surface(onClick = { expanded = if (isExpanded) expanded - item.key else expanded + item.key }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                        Text(stringResource(R.string.hist_group_count, item.entries.size, WebLink.host(item.entries.first().url).orEmpty()), style = MaterialTheme.typography.titleSmall)
                                        Text(stringResource(if (isExpanded) R.string.hist_collapse else R.string.hist_expand), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            is HistoryListItem.Entry -> {
                                val entry = item.entry
                                val toggle = { selected = if (entry.id in selected) selected - entry.id else selected + entry.id }
                                HistoryRow(entry, state.fileStatuses[entry.id], selecting, entry.id in selected,
                                    onClick = { if (selecting) toggle() else if (entry.isDownloaded && entry.id !in state.missingEntryIds) fileActions.open(entry.fileUri, entry.mimeType) else detailsId = entry.id },
                                    onLongClick = { selecting = true; toggle() }, onToggle = toggle,
                                    onShare = { fileActions.share(entry.fileUri, entry.mimeType) },
                                    onRemove = { viewModel.delete(entry.id) }, onDetails = { detailsId = entry.id },
                                    onSource = { fileActions.openSource(entry.url) }, onRefetch = { onRefetch(entry.url) },
                                    onRetry = { viewModel.refresh() })
                            }
                        }
                    }
                }
                if (displayItems.isNotEmpty()) AcquaFastScrollbar(
                    scrollbarState = scrollbar,
                    labelForIndex = { index -> when (val item = displayItems.getOrNull(index)) {
                        is HistoryListItem.Entry -> item.entry.fileName.ifBlank { WebLink.host(item.entry.url).orEmpty() }.take(20)
                        is HistoryListItem.Group -> WebLink.host(item.entries.first().url).orEmpty()
                        else -> ""
                    } }, modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(8.dp))
    }
}
