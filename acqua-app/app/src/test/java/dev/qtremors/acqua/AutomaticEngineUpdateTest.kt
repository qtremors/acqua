package dev.qtremors.acqua

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.data.onboarding.OnboardingState
import dev.qtremors.acqua.data.onboarding.OnboardingStateStore
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.downloader.YtDlpMaintenance
import dev.qtremors.acqua.downloader.YtDlpUpdateStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AutomaticEngineUpdateTest {
    @Test fun `foreground checks wait for completed onboarding without creating first-run settings`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val onboarding = object : OnboardingStateStore {
            override val onboardingState = MutableStateFlow(OnboardingState())
            override suspend fun completeOnboarding(versionCode: Int, markNotificationHandled: Boolean) {
                onboardingState.value = OnboardingState(true, versionCode, markNotificationHandled)
            }
            override suspend fun setNotificationHandled(handled: Boolean) {
                onboardingState.value = onboardingState.value.copy(notificationPermissionHandled = handled)
            }
        }
        val preferences = context.getSharedPreferences(AppSettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
        var attempts = 0
        val maintenance = YtDlpMaintenance(context, AppSettingsRepository(context),
            now = { 5_000_000_000L },
            updateRuntime = { attempts++; YtDlpUpdateStatus.ALREADY_CURRENT },
            runtimeVersion = { "fixture-runtime" })

        repeat(2) { runAutomaticEngineUpdate(onboarding, maintenance) }

        assertEquals(0, attempts)
        assertTrue(preferences.all.isEmpty())
        assertFalse(onboarding.onboardingState.value.isCompleted)

        onboarding.completeOnboarding(BuildConfig.VERSION_CODE, markNotificationHandled = true)
        runAutomaticEngineUpdate(onboarding, maintenance)
        runAutomaticEngineUpdate(onboarding, maintenance)

        assertEquals(1, attempts)
        assertTrue(preferences.contains("acqua_ytdlp_attempt"))
        assertTrue(onboarding.onboardingState.value.isCompleted)
    }
}
