package dev.qtremors.acqua.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.downloader.CommandTemplateStore
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions

@Composable
fun CommandTemplateSelector(options: YtDlpDownloadOptions, onChange: (YtDlpDownloadOptions) -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { CommandTemplateStore(context) }
    var open by rememberSaveable { mutableStateOf(false) }
    val templates = remember(open, options.commandTemplateId, options.commandTemplateSnapshot) { runCatching { store.all() }.getOrDefault(emptyList()) }
    val selected = templates.firstOrNull { it.id == options.commandTemplateId }
    val label = when {
        options.commandTemplateId == null -> stringResource(R.string.command_template_none)
        selected == null || selected.command != options.commandTemplateSnapshot -> stringResource(R.string.command_template_missing)
        runCatching { selected.applyTo(options) != options }.getOrDefault(true) -> stringResource(R.string.command_template_modified, selected.name)
        else -> selected.name
    }
    TextButton(onClick = { open = true }) { Text(stringResource(R.string.command_template_summary, label)) }
    if (open) {
        var draft by rememberSaveable { mutableStateOf(options.commandTemplateId.orEmpty()) }
        val template = templates.firstOrNull { it.id == draft }
        val resolved = if (draft.isEmpty()) options.copy(commandTemplateId = null, commandTemplateSnapshot = null,
            formatSelectorOverride = null, templateAudioMultistreams = false) else runCatching { template?.applyTo(options) }.getOrNull()
        AlertDialog(onDismissRequest = { open = false }, title = { Text(stringResource(R.string.command_templates)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                Text(stringResource(R.string.command_template_precedence))
                TemplateChoice(stringResource(R.string.command_template_none), draft.isEmpty()) { draft = "" }
                templates.forEach { item -> TemplateChoice(item.name, draft == item.id) { draft = item.id } }
                if (templates.isEmpty()) EmptyState(variant = EmptyStateVariant.Templates, compact = true)
                if (template != null) Text(template.command)
                if (resolved == null) Text(stringResource(R.string.command_template_invalid))
            }
        }, confirmButton = { TextButton(enabled = resolved != null, onClick = { resolved?.let(onChange); open = false }) { Text(stringResource(R.string.done)) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
private fun TemplateChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, role = Role.RadioButton, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null)
        Text(label)
    }
}
