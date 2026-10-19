package dev.qtremors.acqua.feature.downloader.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.ui.components.EmptyState
import dev.qtremors.acqua.ui.components.EmptyStateVariant

@Composable
internal fun HistoryEmptyState(state: HistoryUiState, onResetFilters: () -> Unit, onShowLinks: () -> Unit) {
    val reason = state.emptyReason
    val title = when (reason) {
        HistoryEmptyReason.NO_HISTORY -> R.string.no_history
        HistoryEmptyReason.NO_SEARCH_RESULTS -> R.string.hist_no_matches
        HistoryEmptyReason.NO_FILTER_RESULTS -> when (state.filter) {
            HistoryFilter.MEDIA -> R.string.hist_no_downloads
            HistoryFilter.PHOTOS -> R.string.hist_no_photos
            HistoryFilter.VIDEOS -> R.string.hist_no_videos
            HistoryFilter.AUDIO -> R.string.hist_no_audio
            HistoryFilter.LINKS -> R.string.hist_no_links
            HistoryFilter.UNAVAILABLE -> R.string.hist_no_unavailable
        }
    }
    val help = when (reason) {
        HistoryEmptyReason.NO_HISTORY -> R.string.hist_empty_help
        HistoryEmptyReason.NO_SEARCH_RESULTS -> R.string.hist_search_help
        HistoryEmptyReason.NO_FILTER_RESULTS -> R.string.hist_category_help
    }
    EmptyState(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        variant = if (reason == HistoryEmptyReason.NO_SEARCH_RESULTS) EmptyStateVariant.Search else EmptyStateVariant.History,
        title = stringResource(title),
        description = stringResource(help),
        action = if (reason == HistoryEmptyReason.NO_HISTORY) null else {{
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(onClick = onResetFilters) { Text(stringResource(R.string.hist_reset)) }
                if (state.summary.links > 0 && state.filter != HistoryFilter.LINKS) {
                    TextButton(onClick = onShowLinks) { Text(stringResource(R.string.hist_show_links)) }
                }
            }
        }}
    )
}
