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
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.ui.theme.AccentColor
import dev.qtremors.acqua.ui.theme.ExpressiveShapes
import dev.qtremors.acqua.ui.theme.bounceClickable
import dev.qtremors.acqua.ui.theme.bounceCombinedClickable
import dev.qtremors.acqua.ui.theme.buildMonochromeScheme
import dev.qtremors.acqua.ui.theme.buildScheme
import dev.qtremors.acqua.ui.theme.sheet
import dev.qtremors.acqua.ui.theme.titleMediumBold

fun displayedAccentColors(): List<AccentColor> =
    buildList {
        add(AccentColor.DYNAMIC)
        add(AccentColor.MONOCHROME)
        AccentColor.entries
            .filter { it != AccentColor.DYNAMIC && it != AccentColor.MONOCHROME }
            .distinctBy { it.color?.value ?: it.name.hashCode().toULong() }
            .forEach(::add)
    }

@StringRes
fun accentLabelRes(accent: AccentColor): Int = when (accent) {
    AccentColor.DYNAMIC -> R.string.accent_dynamic
    AccentColor.RED -> R.string.color_red
    AccentColor.PINK -> R.string.color_pink
    AccentColor.PURPLE -> R.string.color_purple
    AccentColor.DEEP_PURPLE -> R.string.color_deep_purple
    AccentColor.CYAN -> R.string.color_cyan
    AccentColor.LIGHT_BLUE -> R.string.color_light_blue
    AccentColor.BLUE -> R.string.color_blue
    AccentColor.INDIGO -> R.string.color_indigo
    AccentColor.TEAL -> R.string.color_teal
    AccentColor.GREEN -> R.string.color_green
    AccentColor.LIGHT_GREEN -> R.string.color_light_green
    AccentColor.LIME -> R.string.color_lime
    AccentColor.DEEP_ORANGE -> R.string.color_deep_orange
    AccentColor.ORANGE -> R.string.color_orange
    AccentColor.AMBER -> R.string.color_amber
    AccentColor.YELLOW -> R.string.color_yellow
    AccentColor.BROWN -> R.string.color_brown
    AccentColor.BLUE_GREY -> R.string.color_blue_grey
    AccentColor.GREY -> R.string.color_grey
    AccentColor.BLACK -> R.string.color_black
    AccentColor.MONOCHROME -> R.string.accent_monochrome
}

@Composable
internal fun getAccentDisplayColor(accent: AccentColor): Color {
    val isDark = isSystemInDarkTheme()
    return when (accent) {
        AccentColor.DYNAMIC -> MaterialTheme.colorScheme.primary
        AccentColor.MONOCHROME -> if (isDark) Color.White else Color.Black
        else -> accent.color ?: Color.Gray
    }
}

@Composable
internal fun resolvePreviewColorScheme(
    accent: AccentColor,
    currentAccent: AccentColor
): ColorScheme {
    val currentScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    if (accent == currentAccent) {
        return currentScheme
    }

    return remember(accent, isDark) {
        when {
            accent == AccentColor.MONOCHROME -> buildMonochromeScheme(isDark = isDark, isOled = false)
            accent == AccentColor.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            accent != AccentColor.DYNAMIC -> {
                val primaryColor = accent.color ?: Color(0xFF2196F3)
                buildScheme(primary = primaryColor, isDark = isDark)
            }
            else -> currentScheme
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccentColorSelector(
    currentAccent: AccentColor,
    onAccentSelected: (AccentColor) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val allAccents = remember { displayedAccentColors() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.accent_color),
                style = MaterialTheme.typography.titleMediumBold
            )

            IconButton(
                onClick = { showPicker = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = stringResource(R.string.select_accent_color),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(allAccents, key = { it.name }) { accent ->
                val isSelected = currentAccent == accent
                val displayColor = getAccentDisplayColor(accent)
                val accentLabel = stringResource(accentLabelRes(accent))

                val animatedCornerRadius by animateDpAsState(
                    targetValue = if (isSelected) 14.dp else 26.dp,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                    label = "inlineCornerRadius"
                )

                val animatedScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.08f else 1f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                    label = "inlineScale"
                )

                val isDark = isSystemInDarkTheme()
                val animatedBorderColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        if (isDark) {
                            if (displayColor.luminance() > 0.6f) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.4f)
                        } else {
                            if (displayColor.luminance() > 0.6f) Color.Black.copy(alpha = 0.6f) else displayColor
                        }
                    } else {
                        displayColor.copy(alpha = 0.15f)
                    },
                    animationSpec = spring(stiffness = 300f),
                    label = "inlineBorder"
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .scale(animatedScale)
                        .size(52.dp)
                        .clip(RoundedCornerShape(animatedCornerRadius))
                        .background(displayColor)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = animatedBorderColor,
                            shape = RoundedCornerShape(animatedCornerRadius)
                        )
                        .bounceCombinedClickable(
                            onClick = { onAccentSelected(accent) },
                            onLongClick = { showPicker = true }
                        )
                        .semantics {
                            selected = isSelected
                            contentDescription = accentLabel
                        }
                ) {
                    when (accent) {
                        AccentColor.DYNAMIC -> {
                            Icon(
                                Icons.Default.ColorLens,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        AccentColor.MONOCHROME -> {
                            Icon(
                                Icons.Default.Contrast,
                                contentDescription = null,
                                tint = if (isSystemInDarkTheme()) Color.Black else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        else -> {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = isSelected,
                                enter = scaleIn() + fadeIn(),
                                exit = scaleOut() + fadeOut()
                            ) {
                                val iconTint = if (displayColor.luminance() > 0.5f) Color.Black else Color.White
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showPicker) {
            AccentColorPickerSheet(
                currentAccent = currentAccent,
                onAccentSelected = { accent ->
                    onAccentSelected(accent)
                },
                onDismiss = { showPicker = false }
            )
        }
    }
}
