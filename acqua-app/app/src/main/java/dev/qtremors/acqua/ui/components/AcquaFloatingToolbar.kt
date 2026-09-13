package dev.qtremors.acqua.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.R
import dev.qtremors.acqua.platform.HapticSignal
import dev.qtremors.acqua.platform.performHaptic

data class AcquaTabItem(
    val icon: ImageVector? = null,
    val iconRes: Int? = null,
    @StringRes val labelRes: Int,
    val onClick: () -> Unit,
    val hasBadge: Boolean = false
) {
    constructor(
        icon: ImageVector,
        @StringRes labelRes: Int,
        onClick: () -> Unit
    ) : this(icon = icon, iconRes = null, labelRes = labelRes, onClick = onClick, hasBadge = false)
}

data class AcquaFabAction(
    val icon: ImageVector? = null,
    val iconRes: Int? = null,
    @StringRes val contentDescriptionRes: Int? = null,
    val contentDescription: String? = null,
    val onClick: () -> Unit
) {
    constructor(
        icon: ImageVector,
        @StringRes contentDescriptionRes: Int,
        onClick: () -> Unit
    ) : this(icon = icon, iconRes = null, contentDescriptionRes = contentDescriptionRes, contentDescription = null, onClick = onClick)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AcquaFloatingToolbar(
    modifier: Modifier = Modifier,
    items: List<AcquaTabItem> = emptyList(),
    selectedIndex: Int = -1,
    title: String? = null,
    isBeta: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    onHelpClick: (() -> Unit)? = null,
    fabAction: AcquaFabAction? = null,
    floatingActionButton: (@Composable () -> Unit)? = null,
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
    expanded: Boolean = true
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val screenWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }.value

    // Hide label if font scale is large or screen width is too small
    val isLargeFont = fontScale > 1.25f
    val isCompactScreen = screenWidth < 400
    val shouldHideLabel = isLargeFont || (isCompactScreen && items.size > 3)

    val finalFab: (@Composable () -> Unit)? = when {
        floatingActionButton != null -> floatingActionButton
        onHelpClick != null && fabAction == null -> {
            {
                FloatingActionButton(
                    onClick = {
                        context.performHaptic(HapticSignal.CLICK)
                        onHelpClick()
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.large,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = stringResource(R.string.about_title)
                    )
                }
            }
        }
        fabAction != null -> {
            {
                FloatingActionButton(
                    onClick = {
                        context.performHaptic(HapticSignal.CLICK)
                        fabAction.onClick()
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.large,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                ) {
                    val desc = fabAction.contentDescriptionRes?.let { stringResource(it) } ?: fabAction.contentDescription
                    if (fabAction.icon != null) {
                        Icon(
                            imageVector = fabAction.icon,
                            contentDescription = desc,
                            modifier = Modifier.size(24.dp)
                        )
                    } else if (fabAction.iconRes != null) {
                        Icon(
                            painter = painterResource(id = fabAction.iconRes),
                            contentDescription = desc,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
        else -> null
    }

    val toolbarContent: @Composable RowScope.() -> Unit = {
        if (onBackClick != null) {
                // BACK BUTTON - Unified with Tabbed style (pop-out effect)
                IconButton(
                    onClick = {
                        context.performHaptic(HapticSignal.CLICK)
                        onBackClick()
                    },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (title != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .widthIn(min = 100.dp, max = 250.dp)
                            .padding(horizontal = 8.dp)
                            .align(Alignment.CenterVertically)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.background,
                            maxLines = 1,
                            modifier = Modifier
                                .basicMarquee()
                                .weight(1f, fill = false)
                        )
                        if (isBeta) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.background
                                ),
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    text = "Beta",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            } else {
                // TABBED MODE - Expanding labels
                items.forEachIndexed { index, item ->
                    val isSelected = selectedIndex == index

                    val itemWidth by animateDpAsState(
                        targetValue = if (expanded || isSelected) 48.dp else 0.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "tab_item_width_$index"
                    )

                    val labelWidth by animateDpAsState(
                        targetValue = if (isSelected && !shouldHideLabel) 80.dp else 0.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "tab_label_width_$index"
                    )

                    val spacerWidth by animateDpAsState(
                        targetValue = if (index < items.size - 1) 8.dp else 0.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "tab_spacer_$index"
                    )

                    if (itemWidth > 0.dp || isSelected) {
                        IconButton(
                            onClick = {
                                context.performHaptic(HapticSignal.CLICK)
                                item.onClick()
                            },
                            modifier = Modifier
                                .width(itemWidth + labelWidth)
                                .height(48.dp),
                            colors = if (isSelected) {
                                IconButtonDefaults.filledIconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary,
                                    containerColor = MaterialTheme.colorScheme.background
                                )
                            } else {
                                IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.background,
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Box {
                                    if (item.icon != null) {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = stringResource(id = item.labelRes),
                                            tint = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.background
                                            },
                                            modifier = Modifier.size(24.dp)
                                        )
                                    } else if (item.iconRes != null) {
                                        Icon(
                                            painter = painterResource(id = item.iconRes),
                                            contentDescription = stringResource(id = item.labelRes),
                                            tint = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.background
                                            },
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    if (item.hasBadge) {
                                        Canvas(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .align(Alignment.TopEnd)
                                        ) {
                                            drawCircle(
                                                color = Color.Red
                                            )
                                        }
                                    }
                                }
                                if (isSelected && !shouldHideLabel) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(id = item.labelRes),
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.basicMarquee()
                                    )
                                }
                            }
                        }

                        // Animated spacing between buttons
                        if (index < items.size - 1) {
                            Spacer(modifier = Modifier.width(spacerWidth))
                        }
                    }
            }
        }
    }

    val toolbarModifier = modifier
        .windowInsetsPadding(WindowInsets.navigationBars)
        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
    val toolbarColors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(
        toolbarContentColor = MaterialTheme.colorScheme.onSurface,
        toolbarContainerColor = MaterialTheme.colorScheme.primary
    )

    if (finalFab != null) {
        HorizontalFloatingToolbar(
            modifier = toolbarModifier,
            expanded = expanded,
            floatingActionButton = finalFab,
            scrollBehavior = scrollBehavior,
            colors = toolbarColors,
            content = toolbarContent
        )
    } else {
        HorizontalFloatingToolbar(
            modifier = toolbarModifier,
            expanded = expanded,
            scrollBehavior = scrollBehavior,
            colors = toolbarColors,
            content = toolbarContent
        )
    }
}
