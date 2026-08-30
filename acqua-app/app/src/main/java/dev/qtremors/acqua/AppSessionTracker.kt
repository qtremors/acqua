package dev.qtremors.acqua

import java.util.UUID

enum class AppLaunchMode {
    ColdLauncher,
    ConfigurationRecreation
}

data class AppLaunchContext(
    val mode: AppLaunchMode,
    val navigationSessionId: String
)

/**
 * Owns process-lifetime launch identity.
 *
 * Android may restore an Activity's saved state after recreating the app process.
 * Tracking launch identity distinguishes cold process starts from in-process configuration recreation.
 */
class AppSessionTracker(
    private val navigationSessionIdFactory: () -> String = { UUID.randomUUID().toString() }
) {
    private var hasCreatedMainActivity = false
    private var navigationSessionId = navigationSessionIdFactory()

    @Synchronized
    fun onMainActivityCreated(hasSavedInstanceState: Boolean): AppLaunchContext {
        val mode = if (hasCreatedMainActivity && hasSavedInstanceState) {
            AppLaunchMode.ConfigurationRecreation
        } else {
            AppLaunchMode.ColdLauncher
        }
        if (hasCreatedMainActivity && mode == AppLaunchMode.ColdLauncher) {
            navigationSessionId = navigationSessionIdFactory()
        }
        hasCreatedMainActivity = true
        return AppLaunchContext(
            mode = mode,
            navigationSessionId = navigationSessionId
        )
    }
}
