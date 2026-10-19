package dev.qtremors.acqua.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R

@Composable
internal fun SettingsAppUpdateDialog(
    state: SettingsAppUpdateState,
    onDismiss: () -> Unit,
    onRetryCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit
) {
    val info = state.info
    val canDownload = !info?.apkDownloadUrl.isNullOrBlank() && !info?.apkName.isNullOrBlank()
    val hasAction = state.failure == SettingsAppUpdateFailure.CHECK || state.downloadedApk != null ||
        (info?.isUpdateAvailable == true && canDownload && !state.isDownloading)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(when {
            state.failure == SettingsAppUpdateFailure.CHECK -> R.string.app_update_check_failed_title
            state.isDownloading -> R.string.download_running
            state.downloadedApk != null -> R.string.app_update_ready_title
            info?.isUpdateAvailable == true -> R.string.updates_available
            else -> R.string.up_to_date
        })) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(when {
                    state.failure == SettingsAppUpdateFailure.CHECK -> stringResource(R.string.app_update_check_failed_message)
                    state.failure == SettingsAppUpdateFailure.DOWNLOAD -> stringResource(R.string.app_update_download_failed_message)
                    state.failure == SettingsAppUpdateFailure.INSTALL -> stringResource(R.string.app_update_install_failed_message)
                    state.isDownloading -> stringResource(R.string.app_update_downloading_message, info?.latestVersionName.orEmpty())
                    state.downloadedApk != null -> stringResource(R.string.app_update_ready_message)
                    info?.isUpdateAvailable == true && !canDownload -> stringResource(R.string.app_update_no_apk_message)
                    info?.isUpdateAvailable == true -> stringResource(R.string.app_update_available_message, info.latestVersionName)
                    else -> stringResource(R.string.app_update_current_message)
                })
                if (state.isDownloading) {
                    val progress = state.progress
                    if (progress == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            when {
                state.failure == SettingsAppUpdateFailure.CHECK -> TextButton(onClick = onRetryCheck) {
                    Text(stringResource(R.string.try_again))
                }
                state.downloadedApk != null -> TextButton(onClick = onInstall) {
                    Text(stringResource(R.string.install))
                }
                info?.isUpdateAvailable == true && canDownload && !state.isDownloading -> TextButton(onClick = onDownload) {
                    Text(stringResource(if (state.failure == SettingsAppUpdateFailure.DOWNLOAD) R.string.try_again else R.string.download))
                }
                else -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        },
        dismissButton = {
            if (hasAction) TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}
