package dev.qtremors.acqua.data.onboarding

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.qtremors.acqua.core.data.BuildConfig
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "acqua_onboarding_prefs",
    produceMigrations = { context -> listOf(ExistingInstallOnboardingMigration(context)) }
)

data class OnboardingState(
    val isCompleted: Boolean = false,
    val completedVersionCode: Int = 0,
    val notificationPermissionHandled: Boolean = false
)

interface OnboardingStateStore {
    val onboardingState: Flow<OnboardingState>
    suspend fun completeOnboarding(versionCode: Int, markNotificationHandled: Boolean = true)
    suspend fun setNotificationHandled(handled: Boolean)
}

class OnboardingPreferences(private val context: Context) : OnboardingStateStore {

    companion object {
        val KEY_IS_COMPLETED = booleanPreferencesKey("is_completed")
        val KEY_COMPLETED_VERSION_CODE = intPreferencesKey("completed_version_code")
        val KEY_NOTIFICATION_HANDLED = booleanPreferencesKey("notification_handled")
    }

    override val onboardingState: Flow<OnboardingState> = context.onboardingDataStore.data.map { preferences ->
        OnboardingState(
            isCompleted = preferences[KEY_IS_COMPLETED] ?: false,
            completedVersionCode = preferences[KEY_COMPLETED_VERSION_CODE] ?: 0,
            notificationPermissionHandled = preferences[KEY_NOTIFICATION_HANDLED] ?: false
        )
    }

    override suspend fun completeOnboarding(versionCode: Int, markNotificationHandled: Boolean) {
        context.onboardingDataStore.edit { preferences ->
            preferences[KEY_IS_COMPLETED] = true
            preferences[KEY_COMPLETED_VERSION_CODE] = versionCode
            if (markNotificationHandled) {
                preferences[KEY_NOTIFICATION_HANDLED] = true
            }
        }
    }

    override suspend fun setNotificationHandled(handled: Boolean) {
        context.onboardingDataStore.edit { preferences ->
            preferences[KEY_NOTIFICATION_HANDLED] = handled
        }
    }
}

private class ExistingInstallOnboardingMigration(
    private val context: Context
) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        if (currentData.contains(OnboardingPreferences.KEY_IS_COMPLETED)) return false
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val hasLegacyPreferences = context.getSharedPreferences(
            AppSettingsRepository.PREFS_NAME,
            Context.MODE_PRIVATE
        ).all.isNotEmpty()
        return shouldSkipOnboardingForExistingInstall(
            firstInstallTime = packageInfo?.firstInstallTime ?: 0L,
            lastUpdateTime = packageInfo?.lastUpdateTime ?: 0L,
            hasLegacyPreferences = hasLegacyPreferences
        )
    }

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply {
            this[OnboardingPreferences.KEY_IS_COMPLETED] = true
            this[OnboardingPreferences.KEY_COMPLETED_VERSION_CODE] = BuildConfig.VERSION_CODE
        }

    override suspend fun cleanUp() = Unit
}

internal fun shouldSkipOnboardingForExistingInstall(
    firstInstallTime: Long,
    lastUpdateTime: Long,
    hasLegacyPreferences: Boolean
): Boolean = hasLegacyPreferences ||
    (firstInstallTime > 0L && lastUpdateTime > firstInstallTime)
