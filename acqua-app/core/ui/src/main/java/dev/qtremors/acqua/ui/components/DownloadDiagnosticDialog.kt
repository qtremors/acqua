package dev.qtremors.acqua.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.downloader.DownloadDiagnostic
import kotlinx.coroutines.launch

@Composable
fun DownloadDiagnosticDialog(record: DownloadDiagnostic, onDismiss: () -> Unit, onRetry: (suspend () -> Boolean)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var retrying by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.download_diagnostics)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectionContainer { Text(record.copyText()) }
                TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                        ClipData.newPlainText(context.getString(R.string.download_diagnostics), record.copyText()))
                }) { Text(stringResource(R.string.copy_diagnostics)) }
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/qtremors/acqua/issues"))) }
                }) { Text(stringResource(R.string.diagnostic_get_help)) }
                if (onRetry != null && record.id != "app-crash") TextButton(enabled = !retrying, onClick = {
                    retrying = true
                    scope.launch {
                        val accepted = runCatching { onRetry() }.getOrDefault(false)
                        retrying = false
                        if (accepted) onDismiss()
                        Toast.makeText(context, if (accepted) R.string.diagnostic_retry_queued else R.string.diagnostic_retry_unavailable, Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.hist_retry)) }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}
