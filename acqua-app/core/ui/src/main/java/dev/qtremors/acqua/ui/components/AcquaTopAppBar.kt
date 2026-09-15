package dev.qtremors.acqua.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.platform.HapticSignal
import dev.qtremors.acqua.platform.performHaptic

val LocalKeepAppBarsCollapsed = staticCompositionLocalOf { false }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun acquaLargeTopAppBarHeight(forceCompact: Boolean = false): Dp =
    if (forceCompact || LocalKeepAppBarsCollapsed.current) {
        TopAppBarDefaults.TopAppBarExpandedHeight
    } else {
        TopAppBarDefaults.LargeAppBarExpandedHeight
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AcquaTopAppBar(
    title: Any,
    modifier: Modifier = Modifier,
    subtitle: Any? = null,
    hasBack: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    expandable: Boolean = true,
    isSmall: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val resolvedTitle = when (title) {
        is Int -> stringResource(title)
        is String -> title
        else -> ""
    }
    val resolvedSubtitle = when (subtitle) {
        is Int -> stringResource(subtitle)
        is String -> subtitle
        else -> null
    }

    val titleContent: @Composable () -> Unit = {
        if (resolvedSubtitle != null) {
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = resolvedTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = resolvedSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text(
                text = resolvedTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    val navigationIconContent: @Composable () -> Unit = {
        if (hasBack) {
            IconButton(
                onClick = {
                    context.performHaptic(HapticSignal.CLICK)
                    onBackClick?.invoke()
                },
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    val appColors = TopAppBarDefaults.topAppBarColors(
        containerColor = containerColor,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )

    val forceCompact = isSmall || !expandable
    if (forceCompact && scrollBehavior == null) {
        TopAppBar(
            title = titleContent,
            navigationIcon = navigationIconContent,
            actions = actions,
            colors = appColors,
            scrollBehavior = scrollBehavior,
            modifier = modifier
        )
    } else {
        LargeTopAppBar(
            title = titleContent,
            navigationIcon = navigationIconContent,
            actions = actions,
            colors = appColors,
            scrollBehavior = scrollBehavior,
            expandedHeight = acquaLargeTopAppBarHeight(forceCompact = forceCompact),
            modifier = modifier
        )
    }
}

@Composable
fun AcquaTopAppBarAction(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    hasBadge: Boolean = false,
    badgeColor: Color = MaterialTheme.colorScheme.error,
    icon: @Composable () -> Unit
) {
    val context = LocalContext.current
    Box(modifier = modifier) {
        IconButton(
            onClick = {
                context.performHaptic(HapticSignal.CLICK)
                onClick()
            },
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
        ) {
            icon()
        }
        if (hasBadge) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .align(Alignment.TopEnd)
                    .background(badgeColor, CircleShape)
            )
        }
    }
}
