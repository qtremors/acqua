package dev.qtremors.acqua.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
    count: Int = 1,
    description: String? = null,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true
) {
    SettingsCardContainer(modifier.heightIn(min = 56.dp).toggleable(checked, enabled = enabled,
        role = Role.Switch, onValueChange = onCheckedChange).semantics(mergeDescendants = true) { }, index, count) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            leadingIcon?.let { Icon(it, null, tint = MaterialTheme.colorScheme.primary) }
            SettingsRowText(title, description, enabled, Modifier.weight(1f))
            ExpressiveSwitch(checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.clearAndSetSemantics { })
        }
    }
}

@Composable
fun SettingsActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
    count: Int = 1,
    description: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true
) {
    SettingsCardContainer(modifier.heightIn(min = 56.dp).clickable(enabled = enabled, role = Role.Button,
        onClick = onClick).semantics(mergeDescendants = true) { }, index, count) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            leadingIcon?.let { Icon(it, null, tint = MaterialTheme.colorScheme.primary) }
            SettingsRowText(title, description, enabled, Modifier.weight(1f))
            trailingIcon?.let { Icon(it, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun SettingsRowText(title: String, description: String?, enabled: Boolean, modifier: Modifier) {
    Column(modifier) {
        Text(title, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
        if (description != null) Text(description, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f))
    }
}

@Composable
fun SettingsCardContainer(
    modifier: Modifier = Modifier,
    index: Int = 0,
    count: Int = 1,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = when {
        count <= 1 -> androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
        index == 0 -> androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
        index == count - 1 -> androidx.compose.foundation.shape.RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}
