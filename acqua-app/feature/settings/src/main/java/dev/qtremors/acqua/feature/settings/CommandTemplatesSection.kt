package dev.qtremors.acqua.feature.settings

import dev.qtremors.acqua.ui.components.ExpressiveTextField
import dev.qtremors.acqua.ui.components.EmptyState
import dev.qtremors.acqua.ui.components.EmptyStateVariant

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.downloader.CommandTemplate
import dev.qtremors.acqua.downloader.CommandTemplatePreview
import dev.qtremors.acqua.downloader.CommandTemplateStore
import dev.qtremors.acqua.ui.components.SettingsActionRow
import dev.qtremors.acqua.ui.components.SettingsSection
import dev.qtremors.acqua.ui.components.SettingsEditorDialog
import dev.qtremors.acqua.ui.components.SettingsEditorInfo
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
internal fun CommandTemplatesSection() {
    val context = LocalContext.current
    val store = remember(context) { CommandTemplateStore(context) }
    val clipboard = remember(context) { context.getSystemService(ClipboardManager::class.java) }
    var mode by rememberSaveable { mutableStateOf<String?>(null) }
    var id by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var command by rememberSaveable { mutableStateOf("") }
    var importState by rememberSaveable { mutableStateOf<String?>(null) }
    var issue by rememberSaveable { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    val templates by produceState(emptyList<CommandTemplate>(), revision, mode) {
        if (mode == "list") {
            loading = true
            val result = withContext(Dispatchers.IO) { runCatching { store.all() } }
            value = result.getOrDefault(emptyList())
            if (result.isFailure) issue = true
            loading = false
        }
    }
    val importedName = stringResource(R.string.command_template_imported)
    val example = stringResource(R.string.command_template_example)
    val preview = importState?.let { runCatching { store.previewImport(it, importedName) }.getOrNull() }
    val showPreview: (String) -> Unit = { raw ->
        runCatching { store.previewState(store.previewImport(raw, importedName)) }.onSuccess { importState = it; issue = false; mode = "preview" }
            .onFailure { issue = true }
    }
    val documents = rememberCommandTemplateDocuments(store, showPreview) { issue = true }
    SettingsSection(title = stringResource(R.string.command_templates)) {
        SettingsActionRow(title = stringResource(R.string.command_templates_manage), description = stringResource(R.string.settings_templates_summary),
            leadingIcon = Icons.Filled.Terminal, trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            onClick = { loading = true; revision++; issue = false; mode = "list" })
    }
    when (mode) {
        "list" -> CommandTemplateListDialog(templates, issue, loading, { mode = null }, onNew = {
            id = UUID.randomUUID().toString(); name = ""; command = example; issue = false; mode = "edit"
        }, onEdit = { template -> id = template.id; name = template.name; command = template.command; issue = false; mode = "edit" },
            onRemove = { template -> runCatching { store.remove(template.id) }.onSuccess { revision++ }.onFailure { issue = true } },
            onCopy = { runCatching { clipboard.setPrimaryClip(ClipData.newPlainText("Acqua", store.export())) }.onFailure { issue = true } },
            onPaste = { runCatching { clipboard.primaryClip?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.text?.toString().orEmpty() }
                .onSuccess(showPreview).onFailure { issue = true } }, documents = documents)
        "edit" -> CommandTemplateEditorDialog(id, name, command, issue, { name = it.take(80) }, { command = it.take(4097) }, { mode = "list" }) {
            runCatching { store.put(it) }.onSuccess { revision++; issue = false; mode = "list" }.onFailure { issue = true }
        }
        "preview" -> CommandTemplateImportDialog(preview, issue, { mode = "list"; importState = null }) {
            runCatching { store.applyImport(it) }.onSuccess { revision++; issue = false; importState = null; mode = "list" }.onFailure { issue = true }
        }
    }
}

@Composable
private fun CommandTemplateListDialog(templates: List<CommandTemplate>, issue: Boolean, loading: Boolean, onDismiss: () -> Unit, onNew: () -> Unit,
    onEdit: (CommandTemplate) -> Unit, onRemove: (CommandTemplate) -> Unit, onCopy: () -> Unit, onPaste: () -> Unit,
    documents: CommandTemplateDocuments) {
    SettingsEditorDialog(stringResource(R.string.command_templates), onDismiss, stringResource(R.string.close), onDismiss,
        showCancel = false, tag = "command-templates-dialog") {
            FilledTonalButton(onClick = onNew, enabled = templates.size < CommandTemplateStore.MAX_TEMPLATES,
                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.command_template_new)) }
            FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCopy, enabled = templates.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.command_template_copy)) }
                TextButton(onClick = onPaste, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.command_template_paste)) }
                TextButton(onClick = documents.exportFile, enabled = templates.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.command_template_export)) }
                TextButton(onClick = documents.importFile, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.command_template_import)) }
            }
            if (loading) CircularProgressIndicator()
            if (issue) Text(stringResource(R.string.command_template_store_error), color = MaterialTheme.colorScheme.error)
            if (templates.isEmpty() && !issue && !loading) EmptyState(variant = EmptyStateVariant.Templates, compact = true)
            templates.forEach { template ->
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(template.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { onEdit(template) }) { Icon(Icons.Filled.Edit, stringResource(R.string.command_template_edit, template.name)) }
                            IconButton(onClick = { onRemove(template) }) { Icon(Icons.Filled.DeleteOutline, stringResource(R.string.command_template_remove, template.name)) }
                        }
                        Text(template.command, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            SettingsEditorInfo(stringResource(R.string.command_templates_hint))
    }
}

@Composable
private fun CommandTemplateEditorDialog(id: String, name: String, command: String, issue: Boolean, onName: (String) -> Unit,
    onCommand: (String) -> Unit, onDismiss: () -> Unit, onSave: (CommandTemplate) -> Unit) {
    val valid = runCatching { CommandTemplate(id, name, command).validated() }.getOrNull()
    SettingsEditorDialog(stringResource(R.string.command_template_edit_title), onDismiss, stringResource(R.string.save),
        { valid?.let(onSave) }, confirmEnabled = valid != null, tag = "command-template-editor") {
            Text(stringResource(R.string.settings_template_brief), style = MaterialTheme.typography.bodyMedium)
            ExpressiveTextField(name, onName, label = { Text(stringResource(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            ExpressiveTextField(command, onCommand, label = { Text(stringResource(R.string.command_template_options)) },
                minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth(), isError = name.isNotBlank() && valid == null)
            if (issue || (name.isNotBlank() && valid == null)) Text(stringResource(R.string.command_template_store_error), color = MaterialTheme.colorScheme.error)
            SettingsEditorInfo(stringResource(R.string.command_templates_hint))
    }
}

@Composable
private fun CommandTemplateImportDialog(preview: CommandTemplatePreview?, issue: Boolean, onDismiss: () -> Unit,
    onApply: (CommandTemplatePreview) -> Unit) {
    SettingsEditorDialog(stringResource(R.string.command_template_import), onDismiss, stringResource(R.string.command_template_apply_import),
        { preview?.let(onApply) }, confirmEnabled = preview != null, tag = "command-template-import") {
            if (preview != null) {
                Text(stringResource(R.string.command_template_import_preview, preview.added, preview.updated))
                preview.templates.forEach { template ->
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(template.name, style = MaterialTheme.typography.titleMedium)
                            Text(template.command, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (issue || preview == null) Text(stringResource(R.string.command_template_store_error), color = MaterialTheme.colorScheme.error)
    }
}
