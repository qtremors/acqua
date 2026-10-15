package dev.qtremors.acqua.feature.downloader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
internal fun ContentChoiceRow(title: String, selected: Boolean, onSelect: () -> Unit,
    detail: String? = null, multiple: Boolean = false, tag: String = "content-choice", enabled: Boolean = true) {
    Surface(shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
        val select = if (multiple) Modifier.toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onSelect() })
            else Modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag).then(select).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (multiple) Checkbox(selected, onCheckedChange = null, enabled = enabled) else RadioButton(selected, onClick = null, enabled = enabled)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
