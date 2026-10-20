package dev.qtremors.acqua.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.downloader.FilenameFormatter
import dev.qtremors.acqua.ui.components.SettingsChoice
import dev.qtremors.acqua.ui.components.SettingsChoicePair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsPairLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w320dp-h1000dp-mdpi")
    fun `filename variable pills stay readable at compact widths and double font size in RTL`() {
        val fontScale = mutableStateOf(1f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale.value), LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme { FilenameFormatsSetting("{title}", "{title}", {}, {}) }
            }
        }
        compose.onNodeWithText("Filename formats").performClick()
        compose.onNodeWithTag("filename-group-video").performClick()
        for (scale in listOf(1f, 2f)) {
            compose.runOnIdle { fontScale.value = scale }
            for (variable in FilenameFormatter.variables) {
                val tag = "filename-token-${variable.removeSurrounding("{", "}")}"
                compose.onNodeWithTag(tag).performScrollTo()
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNode(hasText(variable) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertFalse("Clipped $variable at font scale $scale: size=${layouts.single().size}, constraints=${layouts.single().layoutInput.constraints}, lines=${layouts.single().lineCount}, width=${layouts.single().didOverflowWidth}, height=${layouts.single().didOverflowHeight}", layouts.single().hasVisualOverflow)
                if (scale == 1f) assertEquals("Wrapped $variable", 1, layouts.single().lineCount)
            }
        }
    }

    @Test fun `paired selectors align and wrap without clipping at double font size in either direction`() {
        val fontScale = mutableStateOf(1f)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale.value), LocalLayoutDirection provides direction.value) {
                MaterialTheme {
                    Column(Modifier.width(320.dp)) {
                        SettingsChoicePair(SettingsChoice("Update channel", listOf("Nightly"), 0, {}),
                            SettingsChoice("Check interval", listOf("Monthly"), 0, {}))
                    }
                }
            }
        }
        for (scale in listOf(1f, 2f)) for (layoutDirection in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { fontScale.value = scale; direction.value = layoutDirection }
            val first = compose.onNodeWithContentDescription("Update channel: Nightly").fetchSemanticsNode().boundsInRoot
            val second = compose.onNodeWithContentDescription("Check interval: Monthly").fetchSemanticsNode().boundsInRoot
            assertEquals(first.top, second.top, 1f)
            assertEquals(first.bottom, second.bottom, 1f)
            assertTrue(if (layoutDirection == LayoutDirection.Ltr) first.right < second.left else second.right < first.left)
            for (label in listOf("Update channel", "Check interval", "Nightly", "Monthly")) {
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertFalse("Clipped $label at font scale $scale", layouts.single().hasVisualOverflow)
            }
        }
    }
}
