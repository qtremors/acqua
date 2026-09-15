package dev.qtremors.acqua.downloader

import android.content.Context
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    private val settings: AppSettingsRepository
) : YtDlpMaintenanceGateway {
    private val appContext = context.applicationContext

    fun version(): String? = YtDlpRuntime.version(appContext)

    override suspend fun update(force: Boolean): YtDlpMaintenanceResult = withContext(Dispatchers.IO) {
        val preferences = settings.mediaProcessingSettings()
        val updateDue = System.currentTimeMillis() - preferences.lastYtDlpUpdate >= UPDATE_INTERVAL_MS
        if (!force && (!preferences.autoUpdateYtDlp || !updateDue)) {
            return@withContext YtDlpMaintenanceResult(version(), null, preferences.lastYtDlpUpdate)
        }
        val status = YtDlpRuntime.update(appContext)
        val checkedAt = System.currentTimeMillis()
        settings.markYtDlpUpdated(checkedAt)
        YtDlpMaintenanceResult(version(), status, checkedAt)
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
