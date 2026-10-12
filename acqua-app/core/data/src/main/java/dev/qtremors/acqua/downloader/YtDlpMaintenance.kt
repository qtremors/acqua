package dev.qtremors.acqua.downloader

import android.content.Context
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class YtDlpUpdateChannel { STABLE, NIGHTLY, MASTER }

enum class YtDlpUpdateInterval(val milliseconds: Long) {
    DAILY(86_400_000L), WEEKLY(7 * 86_400_000L), MONTHLY(30 * 86_400_000L)
}

data class YtDlpMaintenanceResult(
    val version: String?,
    val updateStatus: YtDlpUpdateStatus?,
    val lastCheckedAt: Long
)

interface YtDlpMaintenanceGateway {
    suspend fun update(force: Boolean): YtDlpMaintenanceResult
}

class YtDlpMaintenance(
    context: Context,
    private val settings: AppSettingsRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val updateRuntime: (YtDlpUpdateChannel) -> YtDlpUpdateStatus = { YtDlpRuntime.update(context.applicationContext, it) },
    private val runtimeVersion: () -> String? = { YtDlpRuntime.version(context.applicationContext) }
) : YtDlpMaintenanceGateway {
    fun version(): String? = runtimeVersion()

    override suspend fun update(force: Boolean): YtDlpMaintenanceResult = withContext(Dispatchers.IO) {
        maintenanceLock.withLock {
            val preferences = settings.mediaProcessingSettings()
            val time = now()
            val due = preferences.lastYtDlpUpdate == 0L || time - preferences.lastYtDlpUpdate >= preferences.ytDlpUpdateInterval.milliseconds
            val retryDue = preferences.lastYtDlpAttempt == 0L || time - preferences.lastYtDlpAttempt >= FAILURE_RETRY_MS
            if (!force && (!preferences.autoUpdateYtDlp || !due || !retryDue)) {
                return@withLock YtDlpMaintenanceResult(settings.cachedYtDlpVersion(), null, preferences.lastYtDlpUpdate)
            }
            settings.markYtDlpAttempt(time)
            val channel = preferences.ytDlpUpdateChannel
            val status = updateRuntime(channel)
            val version = version()
            val checkedAt = now()
            // A channel changed during the update must still be checked on the next entry.
            if (settings.mediaProcessingSettings().ytDlpUpdateChannel == channel) settings.markYtDlpUpdated(checkedAt, version)
            YtDlpMaintenanceResult(version, status, checkedAt)
        }
    }

    private companion object {
        val maintenanceLock = Mutex()
        const val FAILURE_RETRY_MS = 6 * 60 * 60 * 1000L
    }
}
