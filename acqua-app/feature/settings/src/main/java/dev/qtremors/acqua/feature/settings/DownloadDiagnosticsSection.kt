package dev.qtremors.acqua.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.downloader.DownloadDiagnostic
import dev.qtremors.acqua.downloader.DownloadDiagnosticsStore
import dev.qtremors.acqua.ui.components.DownloadDiagnosticDialog
import dev.qtremors.acqua.ui.components.SettingsCardContainer
import dev.qtremors.acqua.ui.components.SettingsSection
import dev.qtremors.acqua.ui.components.SettingsSwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
internal fun DownloadDiagnosticsSection(verbose: Boolean, onVerboseChange: (Boolean) -> Unit,
    initialTaskId: String?, onTaskConsumed: () -> Unit, onRetry: ((String) -> Boolean)?) {
    val context = LocalContext.current
    val store = remember(context) { DownloadDiagnosticsStore(context) }
    val scope = rememberCoroutineScope()
    var recent by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var records by remember { mutableStateOf(emptyList<DownloadDiagnostic>()) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(recent, initialTaskId, selected) {
        if (recent || initialTaskId != null || selected != null) {
            loading = true
            records = withContext(Dispatchers.IO) { runCatching { store.load() }.getOrDefault(emptyList()) }
            loading = false
            if (initialTaskId != null) {
                selected = initialTaskId.takeIf { id -> records.any { it.id == id } }
                if (selected == null) recent = true
                onTaskConsumed()
            }
        }
    }
    SettingsSection(title = stringResource(R.string.download_diagnostics)) {
        SettingsSwitchRow(index = 0, count = 2, title = stringResource(R.string.verbose_diagnostics),
            description = stringResource(R.string.diagnostic_retention_hint), checked = verbose, onCheckedChange = onVerboseChange)
        SettingsCardContainer(index = 1, count = 2) {
            TextButton(onClick = { recent = true }) { Text(stringResource(R.string.recent_failures)) }
        }
    }
    if (recent && selected == null) AlertDialog(onDismissRequest = { recent = false },
        title = { Text(stringResource(R.string.recent_failures)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loading) Text(stringResource(R.string.diagnostics_loading))
                else if (records.isEmpty()) Text(stringResource(R.string.diagnostics_empty))
                records.forEach { record ->
                    TextButton(onClick = { selected = record.id }) {
                        Column {
                            Text(stringResource(if (record.id == "app-crash") R.string.diagnostic_app_crash else R.string.download_failed_title))
                            Text(DateFormat.getDateTimeInstance().format(Date(record.timestamp)))
                        }
                    }
                }
                if (records.isNotEmpty()) TextButton(onClick = {
                    scope.launch {
                        val cleared = withContext(Dispatchers.IO) { runCatching { store.clear() }.isSuccess }
                        if (cleared) records = emptyList()
                    }
                }) { Text(stringResource(R.string.clear_diagnostics)) }
            }
        }, confirmButton = { TextButton(onClick = { recent = false }) { Text(stringResource(R.string.close)) } })
    records.firstOrNull { it.id == selected }?.let { record ->
        DownloadDiagnosticDialog(record, onDismiss = { selected = null },
            onRetry = onRetry?.let { retry -> { withContext(Dispatchers.IO) { retry(record.id) } } })
    }
}
