package dev.qtremors.acqua.downloader

import android.content.Context
import androidx.core.content.edit

/** Durable names shared by the UI, WorkManager, and user-initiated jobs. */
internal object DownloadNameOverrides {
    private fun preferences(context: Context) = context.applicationContext
        .getSharedPreferences("acqua_pending_download_names", Context.MODE_PRIVATE)

    @Synchronized
    fun register(context: Context, id: String) {
        preferences(context).edit { putString(id, ""); remove("closed_$id") }
    }

    @Synchronized
    fun rename(context: Context, id: String, name: String): Boolean {
        val preferences = preferences(context)
        if (!preferences.contains(id) || preferences.getBoolean("closed_$id", false) || name.isBlank()) return false
        preferences.edit { putString(id, name.trim()) }
        return true
    }

    @Synchronized
    fun freeze(context: Context, id: String): String? {
        val preferences = preferences(context)
        if (!preferences.contains(id)) return null
        preferences.edit { putBoolean("closed_$id", true) }
        return preferences.getString(id, null)?.takeIf(String::isNotBlank)
    }

    @Synchronized
    fun reopen(context: Context, id: String) {
        preferences(context).edit { remove("closed_$id") }
    }

    @Synchronized
    fun finish(context: Context, id: String): String? {
        val preferences = preferences(context)
        val name = preferences.getString(id, null)?.takeIf(String::isNotBlank)
        preferences.edit { remove(id); remove("closed_$id") }
        return name
    }
}
