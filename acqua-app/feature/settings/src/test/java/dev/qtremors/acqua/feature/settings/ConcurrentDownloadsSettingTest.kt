package dev.qtremors.acqua.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConcurrentDownloadsSettingTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `two download setting persists through a compact large-font screen recreation`() {
        val settings = AppSettingsRepository(context)
        settings.setMaximumConcurrentDownloads(1)
        try {
            val restore = StateRestorationTester(compose)
            restore.setContent {
                var maximum by remember { mutableStateOf(AppSettingsRepository(context).maximumConcurrentDownloads) }
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    MaterialTheme { Column(Modifier.width(320.dp)) { ConcurrentDownloadsSetting(maximum) {
                        settings.setMaximumConcurrentDownloads(it); maximum = it
                    } } }
                }
            }
            compose.onNodeWithText("One download").assertIsSelected()
            compose.onNodeWithText("Two downloads").performClick().assertIsSelected()
            assertEquals(2, AppSettingsRepository(context).maximumConcurrentDownloads)
            restore.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("Two downloads").assertIsSelected()
            compose.onNodeWithText("One download").performClick().assertIsSelected()
        } finally { settings.setMaximumConcurrentDownloads(1) }
    }

    @Test fun `invalid or corrupt preferences fall back to serial and unsupported choices are rejected`() {
        val preferences = context.getSharedPreferences(AppSettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
        val settings = AppSettingsRepository(context)
        try {
            preferences.edit().remove("acqua_concurrent_downloads").commit()
            assertEquals(1, settings.maximumConcurrentDownloads)
            preferences.edit().putInt("acqua_concurrent_downloads", 9).commit()
            assertEquals(1, settings.maximumConcurrentDownloads)
            preferences.edit().putString("acqua_concurrent_downloads", "many").commit()
            assertEquals(1, settings.maximumConcurrentDownloads)
            assertThrows(IllegalArgumentException::class.java) { settings.setMaximumConcurrentDownloads(3) }
        } finally { settings.setMaximumConcurrentDownloads(1) }
    }
}
