package dev.qtremors.acqua.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

object HighContrast {
    fun ratio(foreground: Color, background: Color): Double {
        val back = background.compositeOver(Color.White)
        val front = foreground.compositeOver(back)
        val first = front.luminance().toDouble()
        val second = back.luminance().toDouble()
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }

    fun foreground(color: Color, background: Color, minimum: Double = 7.0): Color {
        val opaque = color.copy(alpha = 1f)
        if (ratio(opaque, background) >= minimum) return opaque
        val target = if (ratio(Color.Black, background) >= ratio(Color.White, background)) Color.Black else Color.White
        return (1..64).asSequence().map { lerp(opaque, target, it / 64f) }.firstOrNull { ratio(it, background) >= minimum } ?: target
    }

    fun scheme(base: ColorScheme, dark: Boolean): ColorScheme {
        val surface = if (dark) Color.Black else Color.White
        val content = if (dark) Color.White else Color.Black
        val container = if (dark) Color(0xFF141414) else Color(0xFFF2F2F2)
        val primary = foreground(base.primary, container)
        val secondary = foreground(base.secondary, container)
        val tertiary = foreground(base.tertiary, container)
        val error = foreground(base.error, container)
        return base.copy(
            primary = primary, onPrimary = foreground(content, primary), primaryContainer = container, onPrimaryContainer = content,
            secondary = secondary, onSecondary = foreground(content, secondary), secondaryContainer = container, onSecondaryContainer = content,
            tertiary = tertiary, onTertiary = foreground(content, tertiary), tertiaryContainer = container, onTertiaryContainer = content,
            error = error, onError = foreground(content, error), errorContainer = container, onErrorContainer = content,
            background = surface, onBackground = content, surface = surface, onSurface = content,
            surfaceVariant = container, onSurfaceVariant = content, surfaceTint = Color.Transparent,
            surfaceBright = surface, surfaceDim = container, surfaceContainerLowest = surface, surfaceContainerLow = container,
            surfaceContainer = container, surfaceContainerHigh = container, surfaceContainerHighest = container,
            outline = content, outlineVariant = foreground(base.outlineVariant, container, 4.5),
            inverseSurface = content, inverseOnSurface = surface, inversePrimary = foreground(base.primary, content),
            primaryFixed = container, primaryFixedDim = container, onPrimaryFixed = content, onPrimaryFixedVariant = content,
            secondaryFixed = container, secondaryFixedDim = container, onSecondaryFixed = content, onSecondaryFixedVariant = content,
            tertiaryFixed = container, tertiaryFixedDim = container, onTertiaryFixed = content, onTertiaryFixedVariant = content)
    }

    fun categories(base: CategoryColors, background: Color): CategoryColors = CategoryColors(
        foreground(base.images, background), foreground(base.videos, background), foreground(base.audio, background),
        foreground(base.docs, background), foreground(base.archives, background), foreground(base.apks, background))

    fun semantic(base: SemanticColors, scheme: ColorScheme): SemanticColors {
        val success = foreground(base.success, scheme.surfaceContainer)
        val warning = foreground(base.warning, scheme.surfaceContainer)
        return SemanticColors(success, foreground(scheme.onSurface, success), scheme.surfaceContainer,
            warning, foreground(scheme.onSurface, warning), scheme.surfaceContainer,
            foreground(base.operationProgress, scheme.surfaceContainer), foreground(base.hiddenBadge, scheme.surfaceContainer))
    }
}
