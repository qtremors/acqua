package dev.qtremors.acqua.feature.about

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.qtremors.acqua.ui.theme.AcquaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aboutScreenRendersBrandAndBuildInformation() {
        composeRule.setContent {
            AcquaTheme {
                AboutScreen(onOpenNotices = {})
            }
        }

        composeRule.onNodeWithContentDescription("Acqua").assertIsDisplayed()
        composeRule.onNodeWithText("App info").assertIsDisplayed()
        composeRule.onNodeWithText("Version").assertIsDisplayed()
        composeRule.onNodeWithText("Application ID").assertIsDisplayed()
    }

    @Test
    fun debugApplicationLabelIsBackedByAStringResource() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val applicationInfo = context.applicationInfo

        assertNotEquals(0, applicationInfo.labelRes)
        assertEquals(
            "Acqua Debug",
            context.packageManager.getApplicationLabel(applicationInfo).toString()
        )
    }
}
