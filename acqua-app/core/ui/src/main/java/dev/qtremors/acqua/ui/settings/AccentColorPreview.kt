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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccentColorPickerSheet(
    currentAccent: AccentColor,
    onAccentSelected: (AccentColor) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = ExpressiveShapes.sheet
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.select_accent_color),
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.done), style = MaterialTheme.typography.labelLarge)
                }
            }

            LiveAccentPreviewCard(
                accent = currentAccent,
                currentAccent = currentAccent
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.system_themes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SpecialAccentItem(
                    title = stringResource(R.string.accent_dynamic),
                    subtitle = stringResource(R.string.accent_dynamic_description),
                    icon = Icons.Default.ColorLens,
                    isSelected = currentAccent == AccentColor.DYNAMIC,
                    onClick = { onAccentSelected(AccentColor.DYNAMIC) },
                    modifier = Modifier.weight(1f)
                )
                SpecialAccentItem(
                    title = stringResource(R.string.accent_monochrome),
                    subtitle = stringResource(R.string.accent_monochrome_description),
                    icon = Icons.Default.Contrast,
                    isSelected = currentAccent == AccentColor.MONOCHROME,
                    onClick = { onAccentSelected(AccentColor.MONOCHROME) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(12.dp))

            AccentCategorySection(
                title = stringResource(R.string.accent_palette_warm),
                colors = listOf(
                    AccentColor.RED, AccentColor.PINK, AccentColor.DEEP_ORANGE,
                    AccentColor.ORANGE, AccentColor.AMBER, AccentColor.YELLOW
                ),
                currentAccent = currentAccent,
                onSelect = onAccentSelected
            )

            Spacer(modifier = Modifier.height(10.dp))

            AccentCategorySection(
                title = stringResource(R.string.accent_palette_cool_vibrant),
                colors = listOf(
                    AccentColor.BLUE, AccentColor.LIGHT_BLUE, AccentColor.CYAN,
                    AccentColor.INDIGO, AccentColor.PURPLE, AccentColor.DEEP_PURPLE
                ),
                currentAccent = currentAccent,
                onSelect = onAccentSelected
            )

            Spacer(modifier = Modifier.height(10.dp))

            AccentCategorySection(
                title = stringResource(R.string.accent_palette_nature_fresh),
                colors = listOf(
                    AccentColor.TEAL, AccentColor.GREEN, AccentColor.LIGHT_GREEN, AccentColor.LIME
                ),
                currentAccent = currentAccent,
                onSelect = onAccentSelected
            )

            Spacer(modifier = Modifier.height(10.dp))

            AccentCategorySection(
                title = stringResource(R.string.accent_palette_earth_neutral),
                colors = listOf(
                    AccentColor.BROWN, AccentColor.BLUE_GREY, AccentColor.GREY, AccentColor.BLACK
                ),
                currentAccent = currentAccent,
                onSelect = onAccentSelected
            )
        }
    }
}

@Composable
fun LiveAccentPreviewCard(
    accent: AccentColor,
    currentAccent: AccentColor,
    modifier: Modifier = Modifier
) {
    val scheme = resolvePreviewColorScheme(accent = accent, currentAccent = currentAccent)

    val primary by animateColorAsState(scheme.primary, spring(stiffness = 300f), label = "prevPrimary")
    val onPrimary by animateColorAsState(scheme.onPrimary, spring(stiffness = 300f), label = "prevOnPrimary")
    val primaryContainer by animateColorAsState(scheme.primaryContainer, spring(stiffness = 300f), label = "prevPrimaryContainer")
    val onPrimaryContainer by animateColorAsState(scheme.onPrimaryContainer, spring(stiffness = 300f), label = "prevOnPrimaryContainer")
    val secondaryContainer by animateColorAsState(scheme.secondaryContainer, spring(stiffness = 300f), label = "prevSecondaryContainer")
    val surfaceContainer by animateColorAsState(scheme.surfaceContainer, spring(stiffness = 300f), label = "prevSurfaceContainer")
    val surfaceContainerHigh by animateColorAsState(scheme.surfaceContainerHigh, spring(stiffness = 300f), label = "prevSurfaceContainerHigh")
    val onSurface by animateColorAsState(scheme.onSurface, spring(stiffness = 300f), label = "prevOnSurface")

    Card(
        shape = ExpressiveShapes.medium,
        colors = CardDefaults.cardColors(containerColor = surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.acqua_interface),
                        style = MaterialTheme.typography.titleSmall,
                        color = onSurface
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = primaryContainer
                ) {
                    Text(
                        text = stringResource(accentLabelRes(accent)),
                        style = MaterialTheme.typography.labelSmall,
                        color = onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = ExpressiveShapes.small,
                color = surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.list_item_and_controls),
                            style = MaterialTheme.typography.labelMedium,
                            color = onSurface
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = primary
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = onPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp)),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(primary))
                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(primaryContainer))
                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(secondaryContainer))
                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(surfaceContainerHigh))
            }
        }
    }
}
