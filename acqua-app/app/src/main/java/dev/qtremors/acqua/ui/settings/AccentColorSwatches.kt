package dev.qtremors.acqua.ui.settings

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.R
import dev.qtremors.acqua.ui.theme.AccentColor
import dev.qtremors.acqua.ui.theme.ExpressiveShapes
import dev.qtremors.acqua.ui.theme.bounceClickable
import dev.qtremors.acqua.ui.theme.bounceCombinedClickable
import dev.qtremors.acqua.ui.theme.buildMonochromeScheme
import dev.qtremors.acqua.ui.theme.buildScheme
import dev.qtremors.acqua.ui.theme.sheet
import dev.qtremors.acqua.ui.theme.titleMediumBold

@Composable
internal fun AccentCategorySection(
    title: String,
    colors: List<AccentColor>,
    currentAccent: AccentColor,
    onSelect: (AccentColor) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            colors.forEach { accent ->
                ExpressiveColorSwatch(
                    accent = accent,
                    isSelected = currentAccent == accent,
                    onSelect = { onSelect(accent) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ExpressiveColorSwatch(
    accent: AccentColor,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayColor = accent.color ?: Color.Gray
    val accentLabel = stringResource(accentLabelRes(accent))
    val isDark = isSystemInDarkTheme()

    val cornerRadius by animateDpAsState(
        targetValue = if (isSelected) 14.dp else 24.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "swatchCornerRadius"
    )

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "swatchScale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            if (isDark) {
                if (displayColor.luminance() > 0.6f) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.4f)
            } else {
                if (displayColor.luminance() > 0.6f) Color.Black.copy(alpha = 0.6f) else displayColor
            }
        } else {
            displayColor.copy(alpha = 0.2f)
        },
        animationSpec = spring(stiffness = 300f),
        label = "swatchBorder"
    )

    val shape = RoundedCornerShape(cornerRadius)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(shape)
            .background(displayColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = shape
            )
            .bounceClickable { onSelect() }
            .semantics {
                contentDescription = accentLabel
                selected = isSelected
            }
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = isSelected,
            enter = scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f)) + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            val iconTint = if (displayColor.luminance() > 0.5f) Color.Black else Color.White
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (displayColor.luminance() > 0.6f) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun SpecialAccentItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedBg by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = spring(stiffness = 300f),
        label = "specialBg"
    )

    Surface(
        shape = ExpressiveShapes.medium,
        color = animatedBg,
        border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)) else null,
        modifier = modifier.bounceClickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
