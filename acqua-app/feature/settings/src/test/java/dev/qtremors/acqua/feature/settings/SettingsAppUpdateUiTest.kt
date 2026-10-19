package dev.qtremors.acqua.feature.settings

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.data.updater.AppUpdateInfo
import dev.qtremors.acqua.downloader.YtDlpMaintenanceGateway
import dev.qtremors.acqua.downloader.YtDlpMaintenanceResult
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h900dp-mdpi")
class SettingsAppUpdateUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `hero checks Acqua then downloads and installs directly while keeping settings open`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val model = SettingsViewModel(AppSettingsRepository(context), object : YtDlpMaintenanceGateway {
            override suspend fun update(force: Boolean) = YtDlpMaintenanceResult("current", null, 0L)
        }, { _, _ -> })
        val check = CompletableDeferred<Result<AppUpdateInfo>>()
        val gateway = FakeSettingsAppUpdateGateway().apply { downloadGate = CompletableDeferred() }
        var checkCount = 0
        compose.setContent {
            MaterialTheme {
                SettingsScreen(model, checkForAppUpdate = { checkCount++; check.await() }, appUpdateGateway = gateway)
            }
        }
        compose.onNodeWithText("Check for updates").performClick()
        compose.onNodeWithText("Checking for updates…").assertIsNotEnabled()
        compose.runOnIdle { check.complete(Result.success(testAppUpdateInfo())) }
        compose.onNodeWithText("Updates available").assertIsDisplayed()
        compose.onNodeWithText("Download").performClick()
        compose.onNodeWithText("Downloading Acqua 0.4.1…").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Check for updates").performClick()
        compose.onNodeWithText("Downloading Acqua 0.4.1…").assertIsDisplayed()
        compose.runOnIdle { gateway.downloadGate!!.complete(gateway.downloadResult) }
        compose.onNodeWithText("Update ready").assertIsDisplayed()
        compose.onNodeWithText("Install").performClick()
        compose.runOnIdle {
            assertEquals(1, gateway.permissionRequests)
            gateway.allowsInstall = true
        }
        compose.onNodeWithText("Install").performClick()
        compose.runOnIdle {
            assertEquals(1, gateway.installs)
            assertEquals(1, gateway.downloads)
            assertEquals(1, checkCount)
        }
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Check for updates").assertIsDisplayed()
    }
}
