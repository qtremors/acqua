package dev.qtremors.acqua.feature.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.R
import dev.qtremors.acqua.data.backup.PreferencesBackupManager
import dev.qtremors.acqua.data.backup.PreferencesBackupItemStatus
import dev.qtremors.acqua.data.backup.PreferencesBackupPreview
import dev.qtremors.acqua.ui.components.SettingsActionRow
import dev.qtremors.acqua.ui.components.SettingsSection
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupRestoreSection(
    backupManager: PreferencesBackupManager,
    modifier: Modifier = Modifier,
    onRestoreCompleted: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isBusy by remember { mutableStateOf(false) }
    var restorePreview by remember { mutableStateOf<PreferencesBackupPreview?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isBusy = true
        coroutineScope.launch {
            backupManager.exportTo(uri).fold(
                onSuccess = {
                    isBusy = false
                    Toast.makeText(context, R.string.backup_exported_success, Toast.LENGTH_SHORT).show()
                },
                onFailure = {
                    isBusy = false
                    Toast.makeText(context, R.string.backup_export_failed, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isBusy = true
        coroutineScope.launch {
            backupManager.preview(uri).fold(
                onSuccess = { preview ->
                    isBusy = false
                    pendingRestoreUri = uri
                    restorePreview = preview
                },
                onFailure = {
                    isBusy = false
                    Toast.makeText(context, R.string.backup_inspect_failed, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    SettingsSection(
        title = stringResource(R.string.backup_and_restore),
        modifier = modifier
    ) {
        SettingsActionRow(
            index = 0,
            count = 2,
            title = stringResource(R.string.backup_settings_title),
            description = stringResource(R.string.backup_settings_desc),
            leadingIcon = Icons.Filled.FileUpload,
            onClick = {
                val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                exportLauncher.launch("acqua_backup_$dateStr.json")
            }
        )

        SettingsActionRow(
            index = 1,
            count = 2,
            title = stringResource(R.string.restore_settings_title),
            description = stringResource(R.string.restore_settings_desc),
            leadingIcon = Icons.Filled.SettingsBackupRestore,
            onClick = {
                restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
            }
        )
    }

    if (isBusy) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.backup_and_restore)) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            },
            confirmButton = {}
        )
    }

    restorePreview?.let { preview ->
        AlertDialog(
            onDismissRequest = {
                restorePreview = null
                pendingRestoreUri = null
            },
            title = { Text(stringResource(R.string.restore_backup_confirmation_title)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.restore_backup_confirmation_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.backup_preview_items_header),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    preview.items.forEach { item ->
                        val status = when (item.status) {
                            PreferencesBackupItemStatus.WillRestore -> stringResource(R.string.backup_item_will_restore)
                            PreferencesBackupItemStatus.WillReset -> stringResource(R.string.backup_item_will_reset)
                            PreferencesBackupItemStatus.Unchanged -> stringResource(R.string.backup_item_unchanged)
                            else -> item.status.name
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.backup_preview_item, item.label, status),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingRestoreUri
                    restorePreview = null
                    pendingRestoreUri = null
                    if (uri != null) {
                        isBusy = true
                        coroutineScope.launch {
                            backupManager.restoreFrom(uri).fold(
                                onSuccess = {
                                    onRestoreCompleted()
                                    isBusy = false
                                    Toast.makeText(context, R.string.backup_restored_success, Toast.LENGTH_SHORT).show()
                                },
                                onFailure = {
                                    isBusy = false
                                    Toast.makeText(context, R.string.backup_restore_failed, Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    }
                }) {
                    Text(stringResource(R.string.restore_backup))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    restorePreview = null
                    pendingRestoreUri = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
