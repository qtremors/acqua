package dev.qtremors.acqua.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.downloader.YtDlpUpdateChannel
import dev.qtremors.acqua.downloader.YtDlpUpdateInterval

@Composable
internal fun YtDlpUpdatePreferences(state: SettingsUiState, viewModel: SettingsViewModel) {
    Column {
        Text(stringResource(R.string.ytdlp_update_channel), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            YtDlpUpdateChannel.entries.forEach { channel ->
                FilterChip(selected = state.ytDlpUpdateChannel == channel, enabled = !state.isUpdatingYtDlp,
                    onClick = { viewModel.setYtDlpUpdateChannel(channel) }, label = { Text(stringResource(when (channel) {
                        YtDlpUpdateChannel.STABLE -> R.string.ytdlp_channel_stable
                        YtDlpUpdateChannel.NIGHTLY -> R.string.ytdlp_channel_nightly
                        YtDlpUpdateChannel.MASTER -> R.string.ytdlp_channel_master
                    })) })
            }
        }
        Text(stringResource(R.string.ytdlp_update_interval), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            YtDlpUpdateInterval.entries.forEach { interval ->
                FilterChip(selected = state.ytDlpUpdateInterval == interval, enabled = state.autoUpdateYtDlp && !state.isUpdatingYtDlp,
                    onClick = { viewModel.setYtDlpUpdateInterval(interval) }, label = { Text(stringResource(when (interval) {
                        YtDlpUpdateInterval.DAILY -> R.string.ytdlp_interval_daily
                        YtDlpUpdateInterval.WEEKLY -> R.string.ytdlp_interval_weekly
                        YtDlpUpdateInterval.MONTHLY -> R.string.ytdlp_interval_monthly
                    })) })
            }
        }
        Text(stringResource(R.string.ytdlp_maintenance_hint), style = MaterialTheme.typography.bodySmall)
    }
}
