package dev.qtremors.acqua.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.ui.components.SettingsCardContainer

@Composable
internal fun HighContrastSetting(enabled: Boolean, count: Int = 4, onChange: (Boolean) -> Unit) {
    SettingsCardContainer(index = 3, count = count, modifier = Modifier.toggleable(enabled, role = Role.Switch, onValueChange = onChange)) {
        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.high_contrast), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(enabled, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics { })
        }
        Text(stringResource(R.string.high_contrast_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
