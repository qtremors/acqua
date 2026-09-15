package dev.qtremors.acqua.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.platform.HapticSignal
import dev.qtremors.acqua.platform.performHaptic
import dev.qtremors.acqua.ui.theme.LocalReducedMotionEnabled
import kotlinx.coroutines.delay

@Composable
fun <T> ExpressiveSegmentedButtonRow(
    items: List<T>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    label: @Composable (T) -> String,
    leadingIcon: (@Composable (item: T, isSelected: Boolean) -> Unit)? = null,
    trailingBadge: (@Composable (item: T, isSelected: Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val reducedMotion = LocalReducedMotionEnabled.current

    val weightSpring = remember(reducedMotion) {
        if (reducedMotion) snap() else spring<Float>(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        )
    }
    val cornerSpring = remember(reducedMotion) {
        if (reducedMotion) snap() else spring<Dp>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )
    }
    val pressSpring = remember(reducedMotion) {
        if (reducedMotion) snap() else spring<Float>(
            dampingRatio = 0.75f,
            stiffness = Spring.StiffnessMediumLow
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = selectedIndex == index
            val interactionSource = remember(index) { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()
            var tapGeneration by remember(index) { mutableIntStateOf(0) }
            var tapped by remember(index) { mutableStateOf(false) }

            LaunchedEffect(index, tapGeneration) {
                if (tapGeneration > 0) {
                    tapped = true
                    delay(100L)
                    tapped = false
                }
            }

            val visualPressed = pressed || tapped

            val animatedWeight by animateFloatAsState(
                targetValue = when {
                    visualPressed -> 1.35f
                    isSelected -> 1.18f
                    else -> 1f
                },
                animationSpec = weightSpring,
                label = "segmented_weight_$index"
            )

            val outerRadius = 24.dp
            val innerRadius = 8.dp
            val pressedRadius = 14.dp

            val startRadius by animateDpAsState(
                targetValue = if (visualPressed) pressedRadius else if (isSelected || index == 0) outerRadius else innerRadius,
                animationSpec = cornerSpring,
                label = "segmented_start_radius_$index"
            )
            val endRadius by animateDpAsState(
                targetValue = if (visualPressed) pressedRadius else if (isSelected || index == items.lastIndex) outerRadius else innerRadius,
                animationSpec = cornerSpring,
                label = "segmented_end_radius_$index"
            )

            val contentScale by animateFloatAsState(
                targetValue = if (visualPressed) 0.96f else 1f,
                animationSpec = pressSpring,
                label = "segmented_scale_$index"
            )

            val containerColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                animationSpec = if (reducedMotion) snap() else tween(durationMillis = 200),
                label = "segmented_container_color_$index"
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = if (reducedMotion) snap() else tween(durationMillis = 200),
                label = "segmented_content_color_$index"
            )

            Button(
                onClick = {
                    context.performHaptic(HapticSignal.CLICK)
                    tapGeneration++
                    onSelect(index)
                },
                interactionSource = interactionSource,
                shape = RoundedCornerShape(
                    topStart = startRadius,
                    bottomStart = startRadius,
                    topEnd = endRadius,
                    bottomEnd = endRadius
                ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = containerColor,
                    contentColor = contentColor
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp
                ),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier = Modifier
                    .weight(animatedWeight)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleX = contentScale
                        scaleY = contentScale
                    }
                    .semantics {
                        role = Role.Tab
                        this.selected = isSelected
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                ) {
                    if (leadingIcon != null) {
                        leadingIcon(item, isSelected)
                    }
                    Text(
                        text = label(item),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (trailingBadge != null) {
                        trailingBadge(item, isSelected)
                    }
                }
            }
        }
    }
}
