package dev.qtremors.acqua.data.settings

import android.content.Context
import androidx.core.content.edit

interface ConcurrentDownloadSettings {
    val maximumConcurrentDownloads: Int get() = 1
    fun setMaximumConcurrentDownloads(value: Int) {}
}

class DownloadConcurrencyPreferences(context: Context) : ConcurrentDownloadSettings {
    private val preferences = context.applicationContext.getSharedPreferences(AppSettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
    override val maximumConcurrentDownloads: Int
        get() = runCatching { preferences.getInt(KEY, 1) }.getOrDefault(1).takeIf { it in 1..2 } ?: 1

    override fun setMaximumConcurrentDownloads(value: Int) {
        require(value in 1..2)
        preferences.edit { putInt(KEY, value) }
        dev.qtremors.acqua.downloader.DownloadExecutionGate.configure(value)
    }

    private companion object { const val KEY = "acqua_concurrent_downloads" }
}
