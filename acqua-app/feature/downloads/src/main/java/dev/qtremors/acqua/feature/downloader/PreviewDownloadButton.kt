package dev.qtremors.acqua.feature.downloader

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.ui.theme.expressiveSegmentedShapes

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PreviewDownloadButton(
    enabled: Boolean,
    selection: Set<Int>?,
    available: Set<Int>,
    menuEnabled: Boolean,
    onDownload: () -> Unit,
    onSelection: (Set<Int>?) -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val buttonColors = ButtonDefaults.buttonColors()
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val radius by animateDpAsState(if (pressed) 16.dp else 28.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(), label = "preview-download-shape")
    val shape = RoundedCornerShape(radius)
    val canOpenMenu = menuEnabled && available.isNotEmpty()
    val menuLabel = stringResource(R.string.preview_selection_actions)
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(canOpenMenu) { if (!canOpenMenu) menu = false }

    Box(propagateMinConstraints = true) {
        Surface(
            modifier = Modifier.heightIn(min = 48.dp).testTag("preview-download-action")
                .clip(shape).combinedClickable(
                    enabled = enabled || canOpenMenu,
                    role = Role.Button,
                    interactionSource = interactions,
                    indication = LocalIndication.current,
                    onLongClickLabel = menuLabel,
                    onLongClick = if (canOpenMenu) ({ menu = true }) else null,
                    onClick = { if (enabled) onDownload() }
                ).semantics { if (!enabled) disabled() },
            shape = shape,
            color = if (enabled) buttonColors.containerColor else buttonColors.disabledContainerColor,
            contentColor = if (enabled) buttonColors.contentColor else buttonColors.disabledContentColor
        ) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center, content = content)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false },
            modifier = Modifier.testTag("preview-selection-menu"),
            shape = RoundedCornerShape(26.dp), containerColor = colors.surfaceContainerLow) {
            Column(Modifier.width(IntrinsicSize.Max).padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val actions = listOf(
                    Triple(R.string.preview_select_all, Icons.Filled.SelectAll, available),
                    Triple(R.string.preview_invert_selection, Icons.Filled.SwapHoriz, available - selection.orEmpty()),
                    Triple(R.string.preview_cancel_selection, Icons.Filled.Close, null)
                )
                actions.forEachIndexed { index, (label, icon, selected) ->
                    SegmentedListItem(
                        onClick = { menu = false; onSelection(selected) },
                        enabled = index == 0 || selection != null,
                        shapes = expressiveSegmentedShapes(if (index < 2) index else 0, if (index < 2) 2 else 1),
                        leadingContent = { Icon(icon, null, Modifier.size(20.dp), tint = colors.primary) },
                        content = { Text(stringResource(label), style = MaterialTheme.typography.bodyMedium) },
                        colors = ListItemDefaults.segmentedColors(containerColor = colors.surfaceContainer),
                        modifier = Modifier.fillMaxWidth().padding(top = if (index == 2) 6.dp else 0.dp)
                            .height(IntrinsicSize.Min)
                    )
                }
            }
        }
    }
}
