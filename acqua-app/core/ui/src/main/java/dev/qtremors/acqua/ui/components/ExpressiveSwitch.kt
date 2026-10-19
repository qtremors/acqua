package dev.qtremors.acqua.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    val trackColor by animateColorAsState(if (checked) colors.primary else colors.surfaceContainerHighest, label = "switch-track")
    val thumbColor by animateColorAsState(if (checked) colors.onPrimary else colors.outline, label = "switch-thumb")
    val thumbOffset by animateDpAsState(if (checked) 24.dp else 4.dp,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "switch-offset")
    val checkedFraction by animateFloatAsState(if (checked) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "switch-symbol")
    val interaction = if (onCheckedChange != null) {
        Modifier.toggleable(checked, interactionSource = remember { MutableInteractionSource() }, indication = null,
            enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange).minimumInteractiveComponentSize()
    } else {
        Modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(checked)
            if (!enabled) disabled()
        }
    }
    Box(modifier.then(interaction), contentAlignment = Alignment.Center) {
        Box(Modifier.requiredSize(52.dp, 32.dp).graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .clip(CircleShape).background(trackColor)
            .then(if (checked) Modifier else Modifier.border(2.dp, colors.outline, CircleShape)),
            contentAlignment = Alignment.CenterStart) {
            Box(Modifier.offset { IntOffset(thumbOffset.roundToPx(), 0) }.size(24.dp).background(thumbColor, CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = colors.primary, modifier = Modifier.size(12.dp).graphicsLayer {
                    val fraction = checkedFraction.coerceIn(0f, 1f)
                    alpha = fraction; scaleX = fraction; scaleY = fraction
                })
                Icon(Icons.Filled.Close, null, tint = colors.surface, modifier = Modifier.size(12.dp).graphicsLayer {
                    val fraction = 1f - checkedFraction.coerceIn(0f, 1f)
                    alpha = fraction; scaleX = fraction; scaleY = fraction
                })
            }
        }
    }
}
