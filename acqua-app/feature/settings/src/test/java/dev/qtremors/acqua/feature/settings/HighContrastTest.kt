package dev.qtremors.acqua.feature.settings

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.settings.ThemePreferences
import dev.qtremors.acqua.ui.theme.AccentColor
import dev.qtremors.acqua.ui.theme.DarkColorScheme
import dev.qtremors.acqua.ui.theme.DraculaDarkColorScheme
import dev.qtremors.acqua.ui.theme.DraculaLightColorScheme
import dev.qtremors.acqua.ui.theme.HighContrast
import dev.qtremors.acqua.ui.theme.LightColorScheme
import dev.qtremors.acqua.ui.theme.OledColorScheme
import dev.qtremors.acqua.ui.theme.ThemePreset
import dev.qtremors.acqua.ui.theme.ThemeState
import dev.qtremors.acqua.ui.theme.TokyoNightDarkColorScheme
import dev.qtremors.acqua.ui.theme.TokyoNightLightColorScheme
import dev.qtremors.acqua.ui.theme.buildScheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HighContrastTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `standard presets wallpaper custom and accent role pairs meet the contrast policy`() {
        val schemes = listOf(LightColorScheme, DarkColorScheme, OledColorScheme, DraculaDarkColorScheme, DraculaLightColorScheme,
            TokyoNightDarkColorScheme, TokyoNightLightColorScheme, dynamicLightColorScheme(context), dynamicDarkColorScheme(context)) +
            AccentColor.entries.mapNotNull { it.color }.flatMap { listOf(buildScheme(it, false), buildScheme(it, true)) } +
            listOf(LightColorScheme.copy(primary = Color.White, surface = Color(0xFF999999)), DarkColorScheme.copy(primary = Color.Black))
        for (base in schemes) for (dark in listOf(false, true)) {
            val scheme = HighContrast.scheme(base, dark)
            pairs(scheme).forEach { (text, background) -> assertTrue("${HighContrast.ratio(text, background)}", HighContrast.ratio(text, background) >= 7) }
            assertTrue(HighContrast.ratio(scheme.outline, scheme.surfaceContainer) >= 4.5)
            assertTrue(HighContrast.ratio(scheme.outlineVariant, scheme.surfaceContainer) >= 4.5)
            assertEquals(if (dark) Color.Black else Color.White, scheme.surface)
            val disabled = scheme.onSurface.copy(alpha = 0.38f).compositeOver(scheme.surfaceContainer)
            assertTrue(HighContrast.ratio(disabled, scheme.surfaceContainer) >= 2.5)
        }
    }

    @Test fun `semantic category and selected states retain readable foregrounds`() {
        for (dark in listOf(false, true)) {
            val scheme = HighContrast.scheme(if (dark) DarkColorScheme else LightColorScheme, dark)
            val categories = HighContrast.categories(dev.qtremors.acqua.ui.theme.LightCategoryColors, scheme.surfaceContainer)
            listOf(categories.images, categories.videos, categories.audio, categories.docs, categories.archives, categories.apks).forEach {
                assertTrue(HighContrast.ratio(it, scheme.surfaceContainer) >= 7)
                assertTrue(HighContrast.ratio(it, it.copy(alpha = 0.16f).compositeOver(scheme.surfaceContainer)) >= 3)
            }
            val semantic = HighContrast.semantic(dev.qtremors.acqua.ui.theme.LightSemanticColors, scheme)
            assertTrue(HighContrast.ratio(semantic.onSuccess, semantic.success) >= 7)
            assertTrue(HighContrast.ratio(semantic.onWarning, semantic.warning) >= 7)
            assertTrue(HighContrast.ratio(semantic.success, semantic.successContainer) >= 7)
            assertTrue(HighContrast.ratio(semantic.warning, semantic.warningContainer) >= 7)
        }
    }

    @Test fun `preference persists without overwriting the underlying custom theme`() = runBlocking {
        val preferences = ThemePreferences(context)
        val original = ThemeState(themePreset = ThemePreset.CUSTOM, customPrimaryColorHex = "#ffff00", customBackgroundColorHex = "#555555")
        try {
            preferences.saveThemeState(original.copy(highContrast = true))
            assertEquals(original.copy(highContrast = true), ThemePreferences(context).themeState.first())
            preferences.saveThemeState(preferences.themeState.first().copy(highContrast = false))
            assertEquals(original, preferences.themeState.first())
        } finally { preferences.saveThemeState(ThemeState()) }
    }

    private fun pairs(scheme: ColorScheme): List<Pair<Color, Color>> = with(scheme) { listOf(
        onPrimary to primary, onPrimaryContainer to primaryContainer, onSecondary to secondary, onSecondaryContainer to secondaryContainer,
        onTertiary to tertiary, onTertiaryContainer to tertiaryContainer, onError to error, onErrorContainer to errorContainer,
        onSurface to surface, onSurface to surfaceContainer, onSurfaceVariant to surfaceVariant, onBackground to background,
        primary to surfaceContainer, secondary to surfaceContainer, tertiary to surfaceContainer, error to surfaceContainer,
        inverseOnSurface to inverseSurface, inversePrimary to inverseSurface,
        onPrimaryFixed to primaryFixed, onPrimaryFixedVariant to primaryFixedDim,
        onSecondaryFixed to secondaryFixed, onSecondaryFixedVariant to secondaryFixedDim,
        onTertiaryFixed to tertiaryFixed, onTertiaryFixedVariant to tertiaryFixedDim) }
}
