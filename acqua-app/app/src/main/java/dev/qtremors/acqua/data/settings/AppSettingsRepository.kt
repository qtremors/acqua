package dev.qtremors.acqua.data.settings

import android.content.Context
import androidx.core.content.edit
import dev.qtremors.acqua.downloader.FilenameFormatter

data class DownloadSettings(
    val baseFolder: String,
    val categorizeMedia: Boolean,
    val filenamePattern: String
)

class AppSettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun useBrowserSessions(): Boolean = preferences.getBoolean(KEY_USE_BROWSER_SESSIONS, false)

    fun setUseBrowserSessions(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_USE_BROWSER_SESSIONS, enabled) }
    }

    fun downloadSettings(): DownloadSettings = DownloadSettings(
        baseFolder = preferences.getString(KEY_BASE_FOLDER, DEFAULT_BASE_FOLDER).orEmpty()
            .ifBlank { DEFAULT_BASE_FOLDER },
        categorizeMedia = preferences.getBoolean(KEY_CATEGORIZE_MEDIA, false),
        filenamePattern = preferences.getString(KEY_FILENAME_PATTERN, FilenameFormatter.DEFAULT_PATTERN)
            .orEmpty()
            .ifBlank { FilenameFormatter.DEFAULT_PATTERN }
    )

    fun setBaseFolder(value: String) {
        preferences.edit { putString(KEY_BASE_FOLDER, value) }
    }

    fun setCategorizeMedia(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_CATEGORIZE_MEDIA, enabled) }
    }

    fun setFilenamePattern(value: String) {
        preferences.edit { putString(KEY_FILENAME_PATTERN, value) }
    }

    fun sanitizedBaseFolder(value: String): String = value
        .trim()
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '.')
        .take(80)
        .ifBlank { DEFAULT_BASE_FOLDER }

    companion object {
        const val PREFS_NAME = "acqua_prefs"
        const val KEY_USE_BROWSER_SESSIONS = "acqua_use_session_cookies"
        private const val KEY_BASE_FOLDER = "acqua_base_folder"
        private const val KEY_CATEGORIZE_MEDIA = "acqua_categorize_media"
        private const val KEY_FILENAME_PATTERN = "acqua_filename_format"
        private const val DEFAULT_BASE_FOLDER = "Acqua"
    }
}
