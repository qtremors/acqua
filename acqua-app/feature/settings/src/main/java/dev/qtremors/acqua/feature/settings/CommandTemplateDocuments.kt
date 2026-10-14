package dev.qtremors.acqua.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.downloader.CommandTemplateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class CommandTemplateDocuments(val importFile: () -> Unit, val exportFile: () -> Unit)

@Composable
internal fun rememberCommandTemplateDocuments(store: CommandTemplateStore, onPreview: (String) -> Unit,
    onError: () -> Unit): CommandTemplateDocuments {
    val scope = rememberCoroutineScope()
    val importedName = stringResource(R.string.command_template_imported)
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { store.previewState(store.readImport(uri, importedName)) } }
                .onSuccess(onPreview).onFailure { onError() }
        }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { store.writeExport(uri) } }.onFailure { onError() }
        }
    }
    return CommandTemplateDocuments({ open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        { save.launch("Acqua-command-templates.json") })
}
