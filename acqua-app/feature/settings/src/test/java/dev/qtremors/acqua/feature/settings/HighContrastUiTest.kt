package dev.qtremors.acqua.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.ui.theme.AcquaTheme
import dev.qtremors.acqua.ui.theme.ThemeState
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HighContrastUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `setting remains operable at double font size and survives restoration`() {
        val restore = StateRestorationTester(compose)
        restore.setContent {
            var enabled by rememberSaveable { mutableStateOf(false) }
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                AcquaTheme(ThemeState(highContrast = enabled)) {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) { HighContrastSetting(enabled) { enabled = it } }
                }
            }
        }
        compose.onNodeWithText("High contrast").performClick().assertIsOn()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("High contrast", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertFalse("size=${layout.size}, lines=${layout.lineCount}, width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}", layout.hasVisualOverflow)
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("High contrast").assertIsOn()
    }
}
