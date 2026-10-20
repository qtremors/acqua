package dev.qtremors.acqua.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LanguageSettingUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `RTL large-font picker supports keyboard opening and restores draft`() {
        var accepted: String? = null
        lateinit var inputMode: InputModeManager
        val restore = StateRestorationTester(compose)
        restore.setContent {
            inputMode = LocalInputModeManager.current
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme { Column(Modifier.width(320.dp)) { AppLanguageButton("") { accepted = it } } }
            }
        }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        compose.onNodeWithText("System default").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText("System default").assertIsFocused().performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        compose.onNodeWithText("Español").performClick()
        compose.onNodeWithText("Español").assertIsSelected()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Español").assertIsSelected()
        compose.onNodeWithText("Done").performClick()
        assertEquals("es", accepted)
    }

    @Test fun `cancel preserves selection and system default remains available`() {
        var accepted: String? = null
        compose.setContent { MaterialTheme { AppLanguageButton("hi") { accepted = it } } }
        compose.onNodeWithText("हिन्दी").performClick()
        compose.onNodeWithText("العربية").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertNull(accepted)
        compose.onNodeWithText("हिन्दी").performClick()
        compose.onNodeWithText("System default").performClick()
        compose.onNodeWithText("Done").performClick()
        assertEquals("", accepted)
    }
}
