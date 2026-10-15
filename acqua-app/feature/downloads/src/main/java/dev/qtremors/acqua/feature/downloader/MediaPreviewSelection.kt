package dev.qtremors.acqua.feature.downloader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal fun Modifier.previewSelection(
    selected: Boolean,
    selecting: Boolean,
    enabled: Boolean,
    selectLabel: String,
    deselectLabel: String,
    onSelect: (beginSelection: Boolean) -> Unit
): Modifier = combinedClickable(
    enabled = enabled,
    role = Role.Checkbox,
    onClickLabel = if (selecting) if (selected) deselectLabel else selectLabel else null,
    onLongClickLabel = selectLabel,
    onLongClick = { onSelect(true) },
    onClick = { if (selecting) onSelect(false) }
).semantics { this.selected = selected }

@Composable
internal fun PreviewSelectionIndicator(selected: Boolean, index: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.size(32.dp).testTag("preview-selection-indicator-$index"),
        shape = CircleShape,
        color = if (selected) colors.primary else colors.surfaceContainerHigh,
        contentColor = colors.onPrimary,
        border = BorderStroke(2.dp, if (selected) colors.primary else colors.outline),
        shadowElevation = 3.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) Icon(Icons.Filled.Check, null, Modifier.size(22.dp))
        }
    }
}
