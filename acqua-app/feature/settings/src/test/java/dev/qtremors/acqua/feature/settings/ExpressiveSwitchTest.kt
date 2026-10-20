package dev.qtremors.acqua.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.LayoutDirection
import dev.qtremors.acqua.ui.components.ExpressiveSwitch
import dev.qtremors.acqua.ui.components.SettingsSwitchRow
import org.junit.Assert.assertEquals
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
class ExpressiveSwitchTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `standalone and row switches support keyboard access and ignore disabled touches`() {
        val checked = mutableStateOf(false)
        val enabled = mutableStateOf(true)
        lateinit var inputMode: InputModeManager
        var changes = 0
        val update: (Boolean) -> Unit = { changes++; checked.value = it }
        compose.setContent {
            inputMode = LocalInputModeManager.current
            MaterialTheme {
                Column {
                    ExpressiveSwitch(checked.value, update, Modifier.testTag("standalone-switch"), enabled.value)
                    SettingsSwitchRow("Row switch", checked.value, update, enabled = enabled.value)
                }
            }
        }
        compose.onNodeWithTag("standalone-switch").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("Row switch").assertIsOn().performClick().assertIsOff()
        assertEquals(2, changes)
        assertTrue(compose.onNodeWithTag("standalone-switch").fetchSemanticsNode().size.height >= 48)
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        compose.onNodeWithTag("standalone-switch").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("standalone-switch").assertIsFocused().performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        compose.onNodeWithTag("standalone-switch").assertIsOn()
        assertEquals(3, changes)
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithTag("standalone-switch").assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithText("Row switch").assertIsNotEnabled().performTouchInput { click() }
        assertEquals(3, changes)
    }

    @Test fun `the checked thumb follows the reading direction`() {
        val direction = mutableStateOf(LayoutDirection.Ltr)
        var thumbColor = 0
        var trackColor = 0
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                MaterialTheme {
                    thumbColor = MaterialTheme.colorScheme.onPrimary.toArgb()
                    trackColor = MaterialTheme.colorScheme.primary.toArgb()
                    ExpressiveSwitch(true, null, Modifier.testTag("switch-visual"))
                }
            }
        }
        var bitmap = compose.onNodeWithTag("switch-visual").captureToImage().asAndroidBitmap()
        assertEquals(thumbColor, bitmap.getPixel(36, 7))
        assertEquals(trackColor, bitmap.getPixel(16, 7))
        compose.runOnIdle { direction.value = LayoutDirection.Rtl }
        bitmap = compose.onNodeWithTag("switch-visual").captureToImage().asAndroidBitmap()
        assertEquals(thumbColor, bitmap.getPixel(16, 7))
        assertEquals(trackColor, bitmap.getPixel(36, 7))
    }
}
