package dev.qtremors.acqua.feature.history

import android.text.format.Formatter
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.R

internal fun HistoryFilter.labelRes(): Int = when (this) {
    HistoryFilter.MEDIA -> R.string.hist_downloads
    HistoryFilter.PHOTOS -> R.string.photos
    HistoryFilter.VIDEOS -> R.string.videos
    HistoryFilter.AUDIO -> R.string.audio
    HistoryFilter.LINKS -> R.string.links
    HistoryFilter.UNAVAILABLE -> R.string.hist_unavailable
}

internal fun HistorySort.labelRes(): Int = when (this) {
    HistorySort.NEWEST -> R.string.newest
    HistorySort.OLDEST -> R.string.oldest
    HistorySort.LARGEST -> R.string.largest
}

@Composable
internal fun HistorySearchAndFilters(
    state: HistoryUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (HistoryFilter) -> Unit,
    onSortChange: (HistorySort) -> Unit,
    onGroupingChange: (Boolean) -> Unit,
    onStatistics: () -> Unit,
    onSelect: () -> Unit,
    onRefresh: () -> Unit,
    onClear: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val summary = state.summary
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.query, onValueChange = onQueryChange,
                label = { Text(stringResource(R.string.hist_search)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (state.query.isNotEmpty()) {{
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, stringResource(R.string.clear_search))
                    }
                }} else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.hist_options)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Column {
                            Text(stringResource(R.string.hist_group))
                            Text(stringResource(R.string.hist_group_help), style = MaterialTheme.typography.bodySmall)
                        } },
                        leadingIcon = { Checkbox(state.groupBySource, onCheckedChange = null) },
                        onClick = { menu = false; onGroupingChange(!state.groupBySource) }
                    )
                    DropdownMenuItem(text = { Text(stringResource(R.string.hist_select)) }, enabled = state.filteredEntries.isNotEmpty(), onClick = { menu = false; onSelect(); focus.clearFocus() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.refresh)) }, onClick = { menu = false; onRefresh() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.clear_history)) }, enabled = state.entries.isNotEmpty(), onClick = { menu = false; onClear() })
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { onFilterChange(filter); focus.clearFocus() },
                    label = { Text(stringResource(R.string.history_filter_count, stringResource(filter.labelRes()), summary.countFor(filter))) }
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(pluralStringResource(R.plurals.history_results, state.filteredEntries.size, state.filteredEntries.size), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Box {
                val sortDescription = stringResource(R.string.hist_sort, stringResource(state.sort.labelRes()))
                TextButton(onClick = { sortMenu = true }, modifier = Modifier.semantics { contentDescription = sortDescription }) {
                    Text(stringResource(state.sort.labelRes()))
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    HistorySort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(stringResource(sort.labelRes())) },
                            leadingIcon = { RadioButton(selected = state.sort == sort, onClick = null) },
                            onClick = { sortMenu = false; onSortChange(sort); focus.clearFocus() }
                        )
                    }
                }
            }
            IconButton(onClick = onStatistics) { Icon(Icons.Default.Info, stringResource(R.string.hist_statistics)) }
        }
    }
}

@Composable
internal fun HistoryStatistics(summary: HistorySummary, onUnavailable: () -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(pluralStringResource(R.plurals.history_download_count, summary.downloads, summary.downloads))
        Text(pluralStringResource(R.plurals.history_link_count, summary.links, summary.links))
        Text(stringResource(R.string.history_storage_used, Formatter.formatShortFileSize(context, summary.storedBytes)))
        if (summary.missing > 0) {
            TextButton(onClick = onUnavailable) {
                Text(pluralStringResource(R.plurals.hist_unavailable_count, summary.missing, summary.missing), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
