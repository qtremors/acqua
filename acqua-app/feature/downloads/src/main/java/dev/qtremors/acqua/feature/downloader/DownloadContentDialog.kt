package dev.qtremors.acqua.feature.downloader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.qtremors.acqua.core.data.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DownloadContentDialog(
    title: String, tag: String, enabled: Boolean, onDismiss: () -> Unit, onApply: () -> Unit,
    tabs: @Composable () -> Unit = {}, content: @Composable (Modifier) -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.imePadding().padding(horizontal = 16.dp, vertical = 24.dp).widthIn(max = 560.dp)
            .fillMaxWidth().heightIn(max = 620.dp).testTag(tag),
            shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
                }
                tabs()
                content(Modifier.weight(1f, fill = false))
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("$tag-cancel")) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = onApply, enabled = enabled, modifier = Modifier.testTag("$tag-done"),
                        shapes = ButtonDefaults.shapes(), colors = ButtonDefaults.textButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                        Text(stringResource(R.string.done))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DownloadDialogTabs(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { index, label ->
            val checked = index == selectedIndex
            val shapes = when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                labels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            ToggleButton(checked = checked, onCheckedChange = { onSelect(index) }, shapes = shapes,
                modifier = Modifier.heightIn(min = 48.dp).semantics { role = Role.RadioButton; selected = checked }) {
                Text(label, maxLines = 1)
            }
        }
    }
}
