package dev.qtremors.acqua.feature.downloader.history

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.data.history.HistoryTransferFormat
import dev.qtremors.acqua.data.history.InaccessibleHistoryPolicy

@Composable
internal fun HistoryTransferChoices(format: HistoryTransferFormat, onFormat: (HistoryTransferFormat) -> Unit) {
    FlowRow {
        HistoryTransferFormat.entries.forEach { choice -> FilterChip(selected = format == choice,
            onClick = { onFormat(choice) }, label = { Text(stringResource(if (choice == HistoryTransferFormat.JSON) R.string.hist_format_json else R.string.hist_format_urls)) }) }
    }
}

@Composable
internal fun HistoryImportPolicy(policy: InaccessibleHistoryPolicy, onPolicy: (InaccessibleHistoryPolicy) -> Unit) {
    Column {
        Text(stringResource(R.string.hist_inaccessible_hint))
        FlowRow {
            InaccessibleHistoryPolicy.entries.forEach { choice -> FilterChip(selected = policy == choice, onClick = { onPolicy(choice) },
                label = { Text(stringResource(when (choice) {
                    InaccessibleHistoryPolicy.KEEP -> R.string.hist_import_keep
                    InaccessibleHistoryPolicy.LINKS_ONLY -> R.string.hist_import_links
                    InaccessibleHistoryPolicy.SKIP -> R.string.hist_import_skip
                })) }) }
        }
    }
}
