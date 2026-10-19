package dev.qtremors.acqua.feature.downloader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.ui.components.SettingsSection
import dev.qtremors.acqua.ui.components.SettingsSwitchRow
import dev.qtremors.acqua.ui.components.TransferNetworkOptionsButton
import dev.qtremors.acqua.ui.components.SegmentRemovalButton
import dev.qtremors.acqua.ui.components.FormatPolicyButton

@Composable
fun DownloaderExtraSettings(viewModel: DownloaderViewModel) {
    val defaults by viewModel.downloadDefaults.collectAsState()
    DownloaderExtraSettingsContent(defaults,
        onRecordHistory = { enabled -> viewModel.updateDownloadDefaults { it.copy(recordHistory = enabled) } },
        onSkipArchived = { enabled -> viewModel.updateDownloadDefaults { it.copy(skipArchived = enabled) } },
        onNetworkChange = { network -> viewModel.updateDownloadDefaults { it.copy(network = network) } },
        onSegmentsChange = { categories -> viewModel.updateDownloadDefaults { it.copy(removeSegments = categories) } },
        onFormatPolicyChange = { policy -> viewModel.updateDownloadDefaults { it.copy(formatPolicy = policy) } })
}

@Composable
internal fun DownloaderExtraSettingsContent(defaults: dev.qtremors.acqua.downloader.YtDlpDownloadOptions,
    onRecordHistory: (Boolean) -> Unit = {}, onSkipArchived: (Boolean) -> Unit = {},
    onNetworkChange: (dev.qtremors.acqua.domain.TransferNetworkOptions) -> Unit = {},
    onSegmentsChange: (Set<dev.qtremors.acqua.downloader.SegmentCategory>) -> Unit = {},
    onFormatPolicyChange: (dev.qtremors.acqua.downloader.DownloadFormatPolicy) -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SettingsSection(stringResource(R.string.settings_download_behavior)) {
            SettingsSwitchRow(stringResource(R.string.record_download_history), defaults.recordHistory, onRecordHistory,
                index = 0, count = 2, description = stringResource(R.string.settings_history_summary), leadingIcon = Icons.Filled.History)
            SettingsSwitchRow(stringResource(R.string.skip_archived_downloads), defaults.skipArchived, onSkipArchived,
                index = 1, count = 2)
        }
        SettingsSection(stringResource(R.string.settings_advanced_downloads)) {
            Surface(Modifier.fillMaxWidth().testTag("advanced-downloads-card"), shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer) {
                Column {
                    TransferNetworkOptionsButton(defaults.network, onNetworkChange, settingsRow = true, trigger = { open, summary ->
                        AdvancedDownloadRow(stringResource(R.string.transfer_network_options), summary, Icons.Filled.Wifi, open)
                    })
                    AdvancedDownloadDivider()
                    FormatPolicyButton(defaults.formatPolicy, onFormatPolicyChange, settingsRow = true, trigger = { open, summary ->
                        AdvancedDownloadRow(stringResource(R.string.format_policy), summary, Icons.Filled.Tune, open)
                    })
                    AdvancedDownloadDivider()
                    SegmentRemovalButton(defaults.removeSegments, onSegmentsChange, settingsRow = true, trigger = { open, summary ->
                        AdvancedDownloadRow(stringResource(R.string.segment_removal), summary, Icons.Filled.ContentCut, open)
                    })
                }
            }
        }
    }
}

@Composable
private fun AdvancedDownloadRow(title: String, summary: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).clickable(role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(summary, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AdvancedDownloadDivider() {
    HorizontalDivider(Modifier.padding(start = 72.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}
