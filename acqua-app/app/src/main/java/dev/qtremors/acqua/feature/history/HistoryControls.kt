package dev.qtremors.acqua.feature.history

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.R

private data class HistoryFilterOption(
    val filter: HistoryFilter,
    @StringRes val label: Int
)

private data class HistorySortOption(
    val sort: HistorySort,
    @StringRes val label: Int
)

private val filterOptions = listOf(
    HistoryFilterOption(HistoryFilter.MEDIA, R.string.all_media),
    HistoryFilterOption(HistoryFilter.PHOTOS, R.string.photos),
    HistoryFilterOption(HistoryFilter.VIDEOS, R.string.videos),
    HistoryFilterOption(HistoryFilter.AUDIO, R.string.audio),
    HistoryFilterOption(HistoryFilter.LINKS, R.string.links)
)

private val sortOptions = listOf(
    HistorySortOption(HistorySort.NEWEST, R.string.newest),
    HistorySortOption(HistorySort.OLDEST, R.string.oldest),
    HistorySortOption(HistorySort.LARGEST, R.string.largest)
)

@Composable
internal fun HistoryHeader(
    hasEntries: Boolean,
    onOpenDownloads: () -> Unit,
    onClear: () -> Unit
) {
    if (!hasEntries) return
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onOpenDownloads) {
            Icon(
                Icons.Filled.FolderOpen,
                stringResource(R.string.open_downloads_folder),
                tint = colors.primary
            )
        }
        TextButton(onClick = onClear) {
            Text(stringResource(R.string.clear_all), color = colors.error)
        }
    }
}

@Composable
internal fun HistorySearchAndFilters(
    query: String,
    selectedFilter: HistoryFilter,
    selectedSort: HistorySort,
    summary: HistorySummary,
    resultCount: Int,
    onQueryChange: (String) -> Unit,
    onFilterChange: (HistoryFilter) -> Unit,
    onSortChange: (HistorySort) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, stringResource(R.string.clear_search))
                }
            }
        } else {
            null
        },
        label = { Text(stringResource(R.string.search_history)) },
        supportingText = {
            Text(
                pluralStringResource(R.plurals.history_results, resultCount, resultCount),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(16.dp)
    )

    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(bottom = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filterOptions.forEach { option ->
            val selected = selectedFilter == option.filter
            Surface(
                onClick = { onFilterChange(option.filter) },
                shape = RoundedCornerShape(20.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.height(40.dp)
            ) {
                Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(
                            R.string.history_filter_count,
                            stringResource(option.label),
                            summary.countFor(option.filter)
                        ),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(bottom = 12.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        sortOptions.forEach { option ->
            FilterChip(
                selected = selectedSort == option.sort,
                onClick = { onSortChange(option.sort) },
                label = { Text(stringResource(option.label)) }
            )
        }
    }
}

@Composable
internal fun HistorySummaryCard(summary: HistorySummary) {
    if (summary.totalEntries == 0) return
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val storedSize = Formatter.formatShortFileSize(context, summary.storedBytes)

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                stringResource(R.string.history_summary_title),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryPill(
                    pluralStringResource(
                        R.plurals.history_download_count,
                        summary.downloads,
                        summary.downloads
                    )
                )
                SummaryPill(
                    pluralStringResource(
                        R.plurals.history_link_count,
                        summary.links,
                        summary.links
                    )
                )
                SummaryPill(stringResource(R.string.history_storage_used, storedSize))
                if (summary.missing > 0) {
                    SummaryPill(
                        pluralStringResource(
                            R.plurals.history_missing_count,
                            summary.missing,
                            summary.missing
                        ),
                        isWarning = true
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryPill(label: String, isWarning: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isWarning) colors.errorContainer else colors.secondaryContainer,
        contentColor = if (isWarning) colors.onErrorContainer else colors.onSecondaryContainer
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
internal fun HistoryEmptyState(reason: HistoryEmptyReason) {
    val message = when (reason) {
        HistoryEmptyReason.NO_HISTORY -> R.string.no_history
        HistoryEmptyReason.NO_SEARCH_RESULTS -> R.string.no_history_search_results
        HistoryEmptyReason.NO_FILTER_RESULTS -> R.string.no_history_filter_results
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 48.dp)
        )
    }
}
