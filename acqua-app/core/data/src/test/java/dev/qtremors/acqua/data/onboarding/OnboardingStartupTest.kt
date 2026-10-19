package dev.qtremors.acqua.data.onboarding

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.datastore.preferences.core.emptyPreferences
import dev.qtremors.acqua.core.data.BuildConfig
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.downloader.YtDlpMaintenance
import dev.qtremors.acqua.downloader.YtDlpUpdateStatus
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class OnboardingStartupTest {
    @Test fun `successful automatic checks do not identify a fresh install as an existing user`() = runBlocking {
        assertFreshInstallAfterMaintenance(fail = false)
    }

    @Test fun `failed automatic checks do not identify a fresh install as an existing user`() = runBlocking {
        assertFreshInstallAfterMaintenance(fail = true)
    }

    @Test fun `restored user preferences still skip onboarding`() = runBlocking {
        val context = freshInstallContext()
        AppSettingsRepository(context).setBaseFolder("Existing downloads")

        val migration = ExistingInstallOnboardingMigration(context)
        assertTrue(migration.shouldMigrate(emptyPreferences()))
        val state = migration.migrate(emptyPreferences())

        assertEquals(true, state[OnboardingPreferences.KEY_IS_COMPLETED])
        assertEquals(BuildConfig.VERSION_CODE, state[OnboardingPreferences.KEY_COMPLETED_VERSION_CODE])
    }

    private suspend fun assertFreshInstallAfterMaintenance(fail: Boolean) {
        val context = freshInstallContext()
        val preferences = context.getSharedPreferences(AppSettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
        assertTrue(preferences.all.isEmpty())
        var attempts = 0
        val maintenance = YtDlpMaintenance(context, AppSettingsRepository(context),
            now = { 5_000_000_000L },
            updateRuntime = {
                attempts++
                if (fail) throw IOException("Offline startup check")
                YtDlpUpdateStatus.ALREADY_CURRENT
            },
            runtimeVersion = { "fixture-runtime" })

        val result = runCatching { maintenance.update(force = false) }

        assertEquals(!fail, result.isSuccess)
        assertEquals(1, attempts)
        assertTrue(preferences.contains("acqua_ytdlp_attempt"))
        assertFalse(ExistingInstallOnboardingMigration(context).shouldMigrate(emptyPreferences()))
    }

    private fun freshInstallContext(): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(context.packageName, 0).apply {
            firstInstallTime = 1_000L
            lastUpdateTime = 1_000L
        }
        shadowOf(context.packageManager).installPackage(info)
        return context
    }
}
