package dev.qtremors.acqua.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.downloader.CommandTemplate
import dev.qtremors.acqua.downloader.CommandTemplatePreview
import dev.qtremors.acqua.downloader.CommandTemplateStore
import dev.qtremors.acqua.ui.components.SettingsActionRow
import dev.qtremors.acqua.ui.components.SettingsSection
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
    val templates = remember(revision) { runCatching { store.all() }.getOrDefault(emptyList()) }
    val importedName = stringResource(R.string.command_template_imported)
    val example = stringResource(R.string.command_template_example)
    val preview = importState?.let { runCatching { store.previewImport(it, importedName) }.getOrNull() }
    val showPreview: (String) -> Unit = { raw ->
        runCatching { store.previewState(store.previewImport(raw, importedName)) }.onSuccess { importState = it; issue = false; mode = "preview" }
            .onFailure { issue = true }
    }
    val documents = rememberCommandTemplateDocuments(store, showPreview) { issue = true }
    SettingsSection(title = stringResource(R.string.command_templates)) {
        SettingsActionRow(title = stringResource(R.string.command_templates_manage), description = stringResource(R.string.command_templates_hint),
            onClick = { revision++; issue = false; mode = "list" })
    }
    when (mode) {
        "list" -> CommandTemplateListDialog(templates, issue, { mode = null }, onNew = {
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
private fun CommandTemplateListDialog(templates: List<CommandTemplate>, issue: Boolean, onDismiss: () -> Unit, onNew: () -> Unit,
    onEdit: (CommandTemplate) -> Unit, onRemove: (CommandTemplate) -> Unit, onCopy: () -> Unit, onPaste: () -> Unit,
    documents: CommandTemplateDocuments) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.command_templates)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.command_templates_hint))
            FlowRow {
                TextButton(onClick = onNew, enabled = templates.size < CommandTemplateStore.MAX_TEMPLATES) { Text(stringResource(R.string.command_template_new)) }
                TextButton(onClick = onCopy, enabled = templates.isNotEmpty()) { Text(stringResource(R.string.command_template_copy)) }
                TextButton(onClick = onPaste) { Text(stringResource(R.string.command_template_paste)) }
                TextButton(onClick = documents.exportFile, enabled = templates.isNotEmpty()) { Text(stringResource(R.string.command_template_export)) }
                TextButton(onClick = documents.importFile) { Text(stringResource(R.string.command_template_import)) }
            }
            if (issue) Text(stringResource(R.string.command_template_store_error))
            templates.forEach { template ->
                Text(template.name)
                Text(template.command, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                FlowRow {
                    TextButton(onClick = { onEdit(template) }) { Text(stringResource(R.string.command_template_edit, template.name)) }
                    TextButton(onClick = { onRemove(template) }) { Text(stringResource(R.string.command_template_remove, template.name)) }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}

@Composable
private fun CommandTemplateEditorDialog(id: String, name: String, command: String, issue: Boolean, onName: (String) -> Unit,
    onCommand: (String) -> Unit, onDismiss: () -> Unit, onSave: (CommandTemplate) -> Unit) {
    val valid = runCatching { CommandTemplate(id, name, command).validated() }.getOrNull()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.command_template_edit_title)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.command_templates_hint))
            OutlinedTextField(name, onName, label = { Text(stringResource(R.string.name)) }, singleLine = true)
            OutlinedTextField(command, onCommand, label = { Text(stringResource(R.string.command_template_options)) }, minLines = 3, maxLines = 6)
            if (issue || (name.isNotBlank() && valid == null)) Text(stringResource(R.string.command_template_store_error))
        }
    }, confirmButton = { TextButton(enabled = valid != null, onClick = { valid?.let(onSave) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun CommandTemplateImportDialog(preview: CommandTemplatePreview?, issue: Boolean, onDismiss: () -> Unit,
    onApply: (CommandTemplatePreview) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.command_template_import)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (preview != null) {
                Text(stringResource(R.string.command_template_import_preview, preview.added, preview.updated))
                preview.templates.forEach { Text(it.name); Text(it.command) }
            }
            if (issue || preview == null) Text(stringResource(R.string.command_template_store_error))
        }
    }, confirmButton = { TextButton(enabled = preview != null, onClick = { preview?.let(onApply) }) { Text(stringResource(R.string.command_template_apply_import)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
