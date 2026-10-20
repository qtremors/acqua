package dev.qtremors.acqua.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.ui.components.SettingsActionRow
import dev.qtremors.acqua.ui.components.SettingsSwitchRow
import dev.qtremors.acqua.ui.theme.AcquaTheme
import dev.qtremors.acqua.ui.theme.AccentColor
import dev.qtremors.acqua.ui.theme.ThemeMode
import dev.qtremors.acqua.ui.theme.ThemeState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w900dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsReadableRowsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `settings descriptions wrap at compact and expanded widths fonts themes and directions`() {
        val width = mutableStateOf(320)
        val font = mutableStateOf(1f)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val mode = mutableStateOf(ThemeMode.LIGHT)
        val checked = mutableStateOf(false)
        val title = "Keep the original media dates"
        val description = "Use the original capture date when it is available from the selected source."
        val actionDescription = "Choose a writable folder for future audio downloads and their caption files."
        var actions = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font.value), LocalLayoutDirection provides direction.value) {
                AcquaTheme(ThemeState(themeMode = mode.value, accentColor = AccentColor.BLUE, highContrast = true)) {
                    Box(Modifier.width(width.value.dp)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            SettingsSwitchRow(title, checked.value, { checked.value = it }, description = description, leadingIcon = Icons.Default.Folder)
                            SettingsActionRow("Audio folder", { actions++ }, description = actionDescription, leadingIcon = Icons.Default.Folder)
                        }
                    }
                }
            }
        }
        for ((index, size) in listOf(1f, 1.5f, 2f).withIndex()) for (wide in listOf(false, true)) {
            compose.runOnIdle {
                font.value = size; width.value = if (wide) 840 else 320
                direction.value = if (wide) LayoutDirection.Rtl else LayoutDirection.Ltr
                mode.value = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.OLED)[index]
            }
            for (text in listOf(title, description, "Audio folder", actionDescription)) {
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(text, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue(layouts.isNotEmpty())
                val layout = layouts.single()
                assertFalse("Clipped: $text at $size, width ${width.value}, ${direction.value}, ${layout.size}, ${layout.layoutInput.constraints}, height ${layout.multiParagraph.height}, widthOverflow ${layout.didOverflowWidth}", layout.hasVisualOverflow)
            }
        }
        compose.onNodeWithText(title).performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("Audio folder").performScrollTo().performClick()
        assertEquals(1, actions)
    }
}
