package dev.qtremors.acqua.feature.downloader.history

import android.text.format.Formatter
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.ui.theme.bounceClickable
import dev.qtremors.acqua.ui.theme.expressiveSegmentedShapes

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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HistorySearchAndFilters(
    state: HistoryUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (HistoryFilter) -> Unit,
    onSortChange: (HistorySort) -> Unit,
    onStatistics: () -> Unit,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
    onOpenSearch: () -> Unit = {}
) {
    var sortMenu by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val summary = state.summary
    val colors = MaterialTheme.colorScheme

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
        // Filter chips row with active search query chip if present
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.query.isNotEmpty()) {
                FilterChip(
                    selected = true,
                    onClick = onOpenSearch,
                    leadingIcon = {
                        Icon(Icons.Default.Search, null, Modifier.size(16.dp))
                    },
                    label = { Text("\"${state.query}\"") },
                    trailingIcon = {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.clear_search),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                )
            }
            HistoryFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { onFilterChange(filter); focus.clearFocus() },
                    label = { Text(stringResource(R.string.history_filter_count, stringResource(filter.labelRes()), summary.countFor(filter))) }
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Status bar & actions row
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                pluralStringResource(R.plurals.history_results, state.filteredEntries.size, state.filteredEntries.size),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // 1. Sort dropdown (separate button)
            Box {
                val sortDescription = stringResource(R.string.hist_sort, stringResource(state.sort.labelRes()))
                Surface(
                    shape = CircleShape,
                    color = colors.surfaceContainerHigh,
                    modifier = Modifier
                        .height(36.dp)
                        .clip(CircleShape)
                        .bounceClickable(
                            role = Role.Button,
                            onClick = { sortMenu = true }
                        )
                        .semantics { contentDescription = sortDescription }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(state.sort.labelRes()),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface
                        )
                    }
                }
                DropdownMenu(
                    expanded = sortMenu,
                    onDismissRequest = { sortMenu = false },
                    shape = RoundedCornerShape(24.dp),
                    containerColor = colors.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            .padding(6.dp)
                            .selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        HistorySort.entries.forEachIndexed { index, sort ->
                            val isSelected = state.sort == sort
                            SegmentedListItem(
                                selected = isSelected,
                                onClick = {
                                    sortMenu = false
                                    onSortChange(sort)
                                    focus.clearFocus()
                                },
                                shapes = expressiveSegmentedShapes(index = index, count = HistorySort.entries.size),
                                leadingContent = {
                                    RadioButton(selected = isSelected, onClick = null)
                                },
                                content = {
                                    Text(
                                        stringResource(sort.labelRes()),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                colors = ListItemDefaults.segmentedColors(
                                    containerColor = if (isSelected) colors.surfaceContainerHighest else colors.surfaceContainer
                                ),
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                            )
                        }
                    }
                }
            }

            // 2. Grouped: Info & Refresh (split button style)
            val statsDescription = stringResource(R.string.hist_statistics)
            val refreshDescription = stringResource(R.string.refresh)
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val infoShape = splitButtonShape(0, 2)
                Surface(
                    shape = infoShape,
                    color = colors.surfaceContainerHigh,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(infoShape)
                        .bounceClickable(
                            role = Role.Button,
                            onClick = onStatistics
                        )
                        .semantics { contentDescription = statsDescription }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = statsDescription,
                            modifier = Modifier.size(18.dp),
                            tint = colors.onSurface
                        )
                    }
                }

                val refreshShape = splitButtonShape(1, 2)
                Surface(
                    shape = refreshShape,
                    color = colors.surfaceContainerHigh,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(refreshShape)
                        .bounceClickable(
                            role = Role.Button,
                            onClick = onRefresh
                        )
                        .semantics { contentDescription = refreshDescription }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = refreshDescription,
                            modifier = Modifier.size(18.dp),
                            tint = colors.onSurface
                        )
                    }
                }
            }

            // 3. Clear history (separate button)
            val canClear = state.entries.isNotEmpty()
            val clearDescription = stringResource(R.string.clear_history)
            Surface(
                shape = CircleShape,
                color = if (canClear) colors.surfaceContainerHigh else colors.surfaceContainerHigh.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .bounceClickable(
                        enabled = canClear,
                        role = Role.Button,
                        onClick = onClear
                    )
                    .semantics { contentDescription = clearDescription }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = clearDescription,
                        modifier = Modifier.size(18.dp),
                        tint = if (canClear) colors.error else colors.onSurface.copy(alpha = 0.38f)
                    )
                }
            }
        }
    }
}

private fun splitButtonShape(index: Int, count: Int): Shape {
    return when {
        count <= 1 -> CircleShape
        index == 0 -> RoundedCornerShape(50, 15, 15, 50)
        index == count - 1 -> RoundedCornerShape(15, 50, 50, 15)
        else -> RoundedCornerShape(15)
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
