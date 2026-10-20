package dev.qtremors.acqua.feature.settings

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.ui.components.EmptyState
import dev.qtremors.acqua.ui.components.EmptyStateVariant
import dev.qtremors.acqua.ui.theme.LocalReducedMotionEnabled
import java.io.File
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
@Config(sdk = [35], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EmptyStateUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `list empty states wrap their content animate and respect reduced motion`() {
        val reducedMotion = mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionEnabled provides reducedMotion.value) {
                MaterialTheme {
                    Column(Modifier.width(320.dp)) {
                        EmptyState(Modifier.testTag("empty-state"), variant = EmptyStateVariant.Websites)
                        TextButton(onClick = {}) { Text("Following content") }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)
        val initial = compose.onNodeWithTag("empty-state").captureToImage().asAndroidBitmap()
        assertTrue(initial.height in 200..500)
        compose.onNodeWithText("Following content").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(900)
        val animated = compose.onNodeWithTag("empty-state").captureToImage().asAndroidBitmap()
        assertFalse("The shared empty state should animate", initial.sameAs(animated))
        File("build/reports/empty-state-list.png").also { file ->
            file.parentFile?.mkdirs()
            file.outputStream().use { animated.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.runOnIdle { reducedMotion.value = true }
        compose.mainClock.advanceTimeBy(100)
        val still = compose.onNodeWithTag("empty-state").captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(900)
        assertTrue("Reduced motion should stop decorative animation", still.sameAs(compose.onNodeWithTag("empty-state").captureToImage().asAndroidBitmap()))
    }

    @Test fun `compact dialog keeps recovery and dismissal available at large font sizes and RTL`() {
        var recoveryCalls = 0
        var dismissCalls = 0
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionEnabled provides true) {
                MaterialTheme {
                    AlertDialog(onDismissRequest = { dismissCalls++ }, title = { LargeRtlContent { Text("Saved sessions") } },
                        text = {
                            LargeRtlContent {
                                Column(Modifier.verticalScroll(rememberScrollState())) {
                                    EmptyState(variant = EmptyStateVariant.Sessions, compact = true,
                                        action = { TextButton(onClick = { recoveryCalls++ }) { Text("Save session") } })
                                }
                            }
                        },
                        confirmButton = { LargeRtlContent { TextButton(onClick = { dismissCalls++ }) { Text("Close") } } })
                }
            }
        }
        compose.onNodeWithText("Close").assertIsDisplayed()
        assertTrue(compose.onNodeWithText("No saved sessions").fetchSemanticsNode().boundsInRoot.height > 32f)
        compose.onNodeWithText("Save session").performScrollTo().assertIsDisplayed().performClick()
        compose.onNode(isDialog()).captureToImage().asAndroidBitmap().let { bitmap ->
            File("build/reports/empty-state-dialog-large-rtl.png").also { file ->
                file.parentFile?.mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        compose.onNodeWithText("Close").performClick()
        assertEquals(1, recoveryCalls)
        assertEquals(1, dismissCalls)
    }
}

@Composable
private fun LargeRtlContent(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalDensity provides Density(LocalDensity.current.density, 2f),
        LocalLayoutDirection provides LayoutDirection.Rtl,
        content = content
    )
}
