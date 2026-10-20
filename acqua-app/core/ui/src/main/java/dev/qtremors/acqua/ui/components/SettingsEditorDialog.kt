package dev.qtremors.acqua.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.qtremors.acqua.core.data.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsEditorDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    dismissEnabled: Boolean = true,
    showCancel: Boolean = true,
    tag: String = "settings-editor-dialog",
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = { if (dismissEnabled) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.imePadding().padding(horizontal = 16.dp, vertical = 24.dp).widthIn(max = 560.dp)
            .fillMaxWidth().heightIn(max = 680.dp).testTag(tag), shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    IconButton(enabled = dismissEnabled, onClick = onDismiss) {
                        Icon(Icons.Filled.Close, stringResource(R.string.close))
                    }
                }
                Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (showCancel) TextButton(enabled = dismissEnabled, onClick = onDismiss,
                        modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) }
                    FilledTonalButton(enabled = confirmEnabled, onClick = onConfirm, shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp)) { Text(confirmLabel, textAlign = TextAlign.Center) }
                }
            }
        }
    }
}

@Composable
fun SettingsEditorInfo(text: String, label: String = stringResource(R.string.settings_editor_details)) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val state = stringResource(if (expanded) R.string.filename_group_expanded else R.string.filename_group_collapsed)
    Column {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.semantics { stateDescription = state }) {
            Icon(Icons.Filled.Info, null, Modifier.padding(end = 8.dp).size(18.dp))
            Text(label)
        }
        AnimatedVisibility(expanded) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsEditorOptions(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, columns: Int = 2) {
    FlowRow(Modifier.fillMaxWidth().selectableGroup(), maxItemsInEachRow = columns,
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            ToggleButton(checked = selectedIndex == index, onCheckedChange = { onSelect(index) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { role = Role.RadioButton; selected = selectedIndex == index }) {
                Text(label, Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun SettingsEditorChoicePair(first: SettingsChoice, second: SettingsChoice) {
    val choices = listOf(first, second)
    SettingsCardContainer {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            choices.forEach { Text(it.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { choice ->
                val description = stringResource(R.string.download_selection_description, choice.title, choice.choices[choice.selectedIndex])
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    var expanded by rememberSaveable { mutableStateOf(false) }
                    FilledTonalButton(enabled = choice.enabled, onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        modifier = Modifier.fillMaxWidth().fillMaxHeight().heightIn(min = 52.dp)
                        .semantics { contentDescription = description }) {
                        Text(choice.choices[choice.selectedIndex], Modifier.weight(1f))
                        Icon(Icons.Filled.ExpandMore, null, Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        choice.choices.forEachIndexed { index, label ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { choice.onSelect(index); expanded = false },
                                modifier = Modifier.semantics { selected = choice.selectedIndex == index },
                                trailingIcon = { if (choice.selectedIndex == index) Icon(Icons.Filled.Check, null) })
                        }
                    }
                }
            }
        }
    }
}
