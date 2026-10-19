package dev.qtremors.acqua.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.downloader.YtDlpUpdateChannel
import dev.qtremors.acqua.downloader.YtDlpUpdateInterval
import dev.qtremors.acqua.ui.components.SettingsChoice
import dev.qtremors.acqua.ui.components.SettingsChoicePair

@Composable
internal fun YtDlpUpdatePreferences(state: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsChoicePair(
        SettingsChoice(stringResource(R.string.ytdlp_update_channel), YtDlpUpdateChannel.entries.map {
            stringResource(when (it) {
                YtDlpUpdateChannel.STABLE -> R.string.ytdlp_channel_stable
                YtDlpUpdateChannel.NIGHTLY -> R.string.ytdlp_channel_nightly
                YtDlpUpdateChannel.MASTER -> R.string.ytdlp_channel_master
            })
        }, YtDlpUpdateChannel.entries.indexOf(state.ytDlpUpdateChannel), { viewModel.setYtDlpUpdateChannel(YtDlpUpdateChannel.entries[it]) },
            enabled = !state.isUpdatingYtDlp, hint = stringResource(R.string.ytdlp_maintenance_hint)),
        SettingsChoice(stringResource(R.string.ytdlp_update_interval), YtDlpUpdateInterval.entries.map {
            stringResource(when (it) {
                YtDlpUpdateInterval.DAILY -> R.string.ytdlp_interval_daily
                YtDlpUpdateInterval.WEEKLY -> R.string.ytdlp_interval_weekly
                YtDlpUpdateInterval.MONTHLY -> R.string.ytdlp_interval_monthly
            })
        }, YtDlpUpdateInterval.entries.indexOf(state.ytDlpUpdateInterval), { viewModel.setYtDlpUpdateInterval(YtDlpUpdateInterval.entries[it]) },
            enabled = state.autoUpdateYtDlp && !state.isUpdatingYtDlp)
    )
}
