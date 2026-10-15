package dev.qtremors.acqua.feature.downloader.history

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.data.history.HistoryFileDeletionStatus

@Composable
internal fun HistoryDeleteFilesConfirmation(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.hist_delete_files)) },
        text = { Text(stringResource(R.string.hist_delete_files_confirmation, minOf(count, HistoryDeletionController.MAX_FILES))) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.hist_delete_files)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
internal fun HistoryDeletionResults(controller: HistoryDeletionController) {
    val state by controller.state.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        controller.consentResult(it.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(state.consent) {
        state.consent?.takeUnless { it.launched }?.let { consent ->
            controller.markConsentLaunched()
            try { launcher.launch(IntentSenderRequest.Builder(consent.sender).build()) }
            catch (_: RuntimeException) { controller.consentResult(false) }
        }
    }
    if (!state.visible) return
    AlertDialog(onDismissRequest = { if (state.pending.isEmpty()) controller.dismiss() else controller.cancel() },
        title = { Text(stringResource(R.string.hist_delete_files)) }, text = {
            Column {
                Text(stringResource(R.string.hist_delete_files_retained))
                if (state.running) LinearProgressIndicator()
                if (state.consent != null) Text(stringResource(R.string.hist_delete_files_permission))
                else if (!state.running && state.pending.isNotEmpty()) Text(stringResource(R.string.hist_delete_files_paused))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(state.results, key = { it.id }) { result ->
                        Text(stringResource(R.string.hist_delete_file_result, result.name.ifBlank { result.id }, stringResource(result.status.label())))
                    }
                }
            }
        }, confirmButton = {
            when {
                state.pending.isEmpty() -> TextButton(onClick = controller::dismiss) { Text(stringResource(R.string.close)) }
                !state.running && state.consent == null -> TextButton(onClick = controller::resume) { Text(stringResource(R.string.continue_label)) }
            }
        }, dismissButton = {
            if (state.pending.isNotEmpty()) TextButton(onClick = controller::cancel) { Text(stringResource(R.string.cancel)) }
        })
}

private fun HistoryFileDeletionStatus.label(): Int = when (this) {
    HistoryFileDeletionStatus.DELETED -> R.string.hist_delete_file_deleted
    HistoryFileDeletionStatus.MISSING -> R.string.hist_delete_file_missing
    HistoryFileDeletionStatus.DENIED -> R.string.hist_delete_file_denied
    HistoryFileDeletionStatus.FAILED -> R.string.hist_delete_file_failed
    HistoryFileDeletionStatus.SKIPPED -> R.string.hist_delete_file_skipped
    HistoryFileDeletionStatus.CANCELLED -> R.string.hist_delete_file_cancelled
}
