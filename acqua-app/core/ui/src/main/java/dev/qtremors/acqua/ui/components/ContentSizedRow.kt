package dev.qtremors.acqua.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp

/** Fits each control's content first, then shares the remaining width. */
@Composable
fun ContentSizedRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    stackWhenNeeded: Boolean = false,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier.fillMaxWidth()) { measurables, constraints ->
        val naturalWidths = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val gap = spacing.roundToPx()
        val totalSpacing = gap * (measurables.size - 1).coerceAtLeast(0)
        val naturalWidth = naturalWidths.sum() + totalSpacing
        val width = constraints.constrainWidth(naturalWidth)
        val stacked = stackWhenNeeded && naturalWidth > width
        val availableWidth = (width - totalSpacing).coerceAtLeast(0)
        val weights = measurables.map { (it.parentData as? Float) ?: 1f }
        val totalWeight = weights.sum().coerceAtLeast(1f)
        val totalContentWidth = naturalWidths.sum().coerceAtLeast(1)
        var remainingWidth = availableWidth
        val widths = naturalWidths.mapIndexed { index, natural ->
            val allocated = when {
                stacked -> width
                index == naturalWidths.lastIndex -> remainingWidth
                totalContentWidth <= availableWidth -> natural +
                    ((availableWidth - totalContentWidth) * weights[index] / totalWeight).toInt()
                else -> (availableWidth.toFloat() * natural / totalContentWidth).toInt()
            }.coerceIn(0, if (stacked) width else remainingWidth)
            if (!stacked) remainingWidth -= allocated
            allocated
        }
        val heights = measurables.mapIndexed { index, measurable ->
            measurable.maxIntrinsicHeight(widths[index])
        }
        val rowHeight = heights.maxOrNull() ?: 0
        val placeables = measurables.mapIndexed { index, measurable ->
            val height = (if (stacked) heights[index] else rowHeight).coerceAtMost(constraints.maxHeight)
            measurable.measure(Constraints.fixed(widths[index], height))
        }
        val height = constraints.constrainHeight(
            if (stacked) placeables.sumOf { it.height } + totalSpacing else rowHeight
        )
        layout(width, height) {
            var position = 0
            placeables.forEach { placeable ->
                placeable.placeRelative(if (stacked) 0 else position, if (stacked) position else 0)
                position += (if (stacked) placeable.height else placeable.width) + gap
            }
        }
    }
}

/** Changes how spare space is distributed without taking space needed by another label. */
fun Modifier.contentSizedWeight(weight: Float): Modifier = then(ContentSizedWeight(weight))

private data class ContentSizedWeight(val weight: Float) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = weight
}
