package dev.qtremors.acqua.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R

data class SettingsChoice(
    val title: String,
    val choices: List<String>,
    val selectedIndex: Int,
    val onSelect: (Int) -> Unit,
    val enabled: Boolean = true,
    val hint: String? = null
)

/** A compact summary with the same shaped selection control as the download sheet. */
@Composable
fun SettingsChoiceRow(
    title: String,
    choices: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    index: Int = 0,
    count: Int = 1,
    enabled: Boolean = true,
    hint: String? = null
) {
    SettingsCardContainer(index = index, count = count) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsChoiceControl(SettingsChoice(title, choices, selectedIndex, onSelect, enabled, hint), Modifier.padding(top = 8.dp))
    }
}

@Composable
fun SettingsChoicePair(first: SettingsChoice, second: SettingsChoice) {
    val choices = listOf(first, second)
    SettingsCardContainer {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            choices.forEach { choice ->
                Text(choice.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { choice -> SettingsChoiceControl(choice, Modifier.weight(1f).fillMaxHeight()) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsChoiceControl(choice: SettingsChoice, modifier: Modifier = Modifier) {
    val (title, choices, selectedIndex, onSelect, enabled, hint) = choice
    var open by rememberSaveable { mutableStateOf(false) }
    val description = stringResource(R.string.download_selection_description, title, choices.getOrElse(selectedIndex) { "" })
    FilledTonalButton(
        onClick = { open = true }, enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp).semantics { contentDescription = description },
        shapes = ButtonDefaults.shapes(RoundedCornerShape(22.dp), RoundedCornerShape(12.dp)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(choices.getOrElse(selectedIndex) { "" }, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(18.dp))
    }
    if (open) {
        var draft by rememberSaveable { mutableIntStateOf(selectedIndex) }
        AlertDialog(
            onDismissRequest = { open = false }, title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    hint?.let { Text(it, Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall) }
                    choices.forEachIndexed { option, label ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (draft == option) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                    .selectable(draft == option, role = Role.RadioButton, onClick = { draft = option })
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                RadioButton(draft == option, onClick = null)
                                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onSelect(draft); open = false }) { Text(stringResource(R.string.done)) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
