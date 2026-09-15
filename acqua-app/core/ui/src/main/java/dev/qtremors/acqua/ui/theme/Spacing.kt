package dev.qtremors.acqua.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class Spacing(
    val none: Dp = 0.dp,
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,

    // Intermediate values following 4dp grid
    val space12: Dp = 12.dp,
    val space20: Dp = 20.dp,

    // Semantic spacing tokens
    val screenGutter: Dp = 16.dp,
    val cardPadding: Dp = 16.dp,
    val segmentedGap: Dp = 2.dp,
    val listItemHorizontal: Dp = 16.dp,
    val listItemVertical: Dp = 8.dp,
    val sheetHorizontal: Dp = 20.dp,
    val toolbarBottomGap: Dp = 80.dp,
    val sectionGap: Dp = 24.dp,
    val compactGap: Dp = 12.dp
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

val MaterialTheme.spacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current
