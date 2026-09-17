package dev.qtremors.acqua.platform.permissions

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.qtremors.acqua.core.data.R

@Composable
fun DownloadPermissionDialogs(
    prompt: DownloadPermissionPrompt,
    onDismiss: () -> Unit,
    onRequestStorage: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestNotifications: () -> Unit,
    onContinueWithoutNotifications: () -> Unit
) {
    when (prompt) {
        DownloadPermissionPrompt.NONE -> Unit
        DownloadPermissionPrompt.STORAGE_RATIONALE -> PermissionDialog(
            title = stringResource(R.string.storage_permission_required),
            message = stringResource(R.string.storage_permission_rationale),
            confirmLabel = stringResource(R.string.try_again),
            onConfirm = onRequestStorage,
            onDismiss = onDismiss
        )
        DownloadPermissionPrompt.STORAGE_SETTINGS -> PermissionDialog(
            title = stringResource(R.string.storage_permission_required),
            message = stringResource(R.string.storage_permission_settings),
            confirmLabel = stringResource(R.string.open_settings),
            onConfirm = onOpenSettings,
            onDismiss = onDismiss
        )
        DownloadPermissionPrompt.NOTIFICATION_EXPLANATION -> PermissionDialog(
            title = stringResource(R.string.notification_permission_title),
            message = stringResource(R.string.notification_permission_download_explanation),
            confirmLabel = stringResource(R.string.allow_notifications),
            onConfirm = onRequestNotifications,
            dismissLabel = stringResource(R.string.continue_without_notifications),
            onDismiss = onContinueWithoutNotifications
        )
        DownloadPermissionPrompt.NOTIFICATION_DENIED -> PermissionDialog(
            title = stringResource(R.string.notifications_disabled),
            message = stringResource(R.string.notification_permission_denied_explanation),
            confirmLabel = stringResource(R.string.continue_without_notifications),
            onConfirm = onContinueWithoutNotifications,
            dismissLabel = stringResource(R.string.open_settings),
            onDismiss = onOpenSettings
        )
    }
}

@Composable
private fun PermissionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = stringResource(R.string.cancel)
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } }
    )
}
