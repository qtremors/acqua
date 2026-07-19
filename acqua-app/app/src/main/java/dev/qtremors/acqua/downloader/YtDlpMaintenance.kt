package dev.qtremors.acqua.downloader

import android.content.Context
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YtDlpMaintenance(
    context: Context,
    private val settings: AppSettingsRepository
) {
    private val appContext = context.applicationContext

    fun version(): String? = YtDlpRuntime.version(appContext)

    suspend fun update(force: Boolean): String? = withContext(Dispatchers.IO) {
        val preferences = settings.mediaProcessingSettings()
        val updateDue = System.currentTimeMillis() - preferences.lastYtDlpUpdate >= UPDATE_INTERVAL_MS
        if (!force && (!preferences.autoUpdateYtDlp || !updateDue)) return@withContext version()
        YtDlpRuntime.update(appContext)
        settings.markYtDlpUpdated()
        version()
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
