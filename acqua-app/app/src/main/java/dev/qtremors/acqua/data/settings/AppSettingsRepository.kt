package dev.qtremors.acqua.data.settings

import android.content.Context
import androidx.core.content.edit
import dev.qtremors.acqua.downloader.FilenameFormatter
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.domain.DownloadEngine
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class DownloadSettings(
    val baseFolder: String,
    val categorizeMedia: Boolean,
    val filenamePattern: String
)

data class MediaProcessingSettings(
    val maximumVideoHeight: Int,
    val audioFormat: AudioOutputFormat,
    val embedMetadata: Boolean,
    val embedThumbnail: Boolean,
    val autoUpdateYtDlp: Boolean,
    val lastYtDlpUpdate: Long
)

class AppSettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun useBrowserSessions(): Boolean = preferences.getBoolean(KEY_USE_BROWSER_SESSIONS, false)

    fun setUseBrowserSessions(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_USE_BROWSER_SESSIONS, enabled) }
    }

    fun screenProtectionEnabled(): Boolean = preferences.getBoolean(KEY_SCREEN_PROTECTION, false)

    fun setScreenProtectionEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_SCREEN_PROTECTION, enabled) }
    }

    fun screenProtectionEnabledFlow(): Flow<Boolean> = callbackFlow {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_SCREEN_PROTECTION) trySend(screenProtectionEnabled())
        }
        trySend(screenProtectionEnabled())
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun downloadSettings(): DownloadSettings = DownloadSettings(
        baseFolder = preferences.getString(KEY_BASE_FOLDER, DEFAULT_BASE_FOLDER).orEmpty()
            .ifBlank { DEFAULT_BASE_FOLDER },
        categorizeMedia = preferences.getBoolean(KEY_CATEGORIZE_MEDIA, false),
        filenamePattern = preferences.getString(KEY_FILENAME_PATTERN, FilenameFormatter.DEFAULT_PATTERN)
            .orEmpty()
            .ifBlank { FilenameFormatter.DEFAULT_PATTERN }
            .let { pattern ->
                if (pattern == FilenameFormatter.LEGACY_DEFAULT_PATTERN) FilenameFormatter.DEFAULT_PATTERN
                else pattern
            }
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

    fun mediaProcessingSettings(): MediaProcessingSettings = MediaProcessingSettings(
        maximumVideoHeight = preferences.getInt(KEY_MAXIMUM_VIDEO_HEIGHT, 0),
        audioFormat = AudioOutputFormat.fromPreference(
            preferences.getString(KEY_AUDIO_FORMAT, AudioOutputFormat.ORIGINAL.preferenceValue).orEmpty()
        ),
        embedMetadata = preferences.getBoolean(KEY_EMBED_METADATA, true),
        embedThumbnail = preferences.getBoolean(KEY_EMBED_THUMBNAIL, true),
        autoUpdateYtDlp = preferences.getBoolean(KEY_AUTO_UPDATE_YT_DLP, true),
        lastYtDlpUpdate = preferences.getLong(KEY_LAST_YT_DLP_UPDATE, 0L)
    )

    fun setMaximumVideoHeight(value: Int) {
        preferences.edit { putInt(KEY_MAXIMUM_VIDEO_HEIGHT, value.coerceAtLeast(0)) }
    }

    fun setAudioFormat(value: AudioOutputFormat) {
        preferences.edit { putString(KEY_AUDIO_FORMAT, value.preferenceValue) }
    }

    fun setEmbedMetadata(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_EMBED_METADATA, enabled) }
    }

    fun setEmbedThumbnail(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_EMBED_THUMBNAIL, enabled) }
    }

    fun lastDownloadEngine(): DownloadEngine = preferences.getString(KEY_DOWNLOAD_ENGINE, null)
        ?.let { value -> DownloadEngine.entries.firstOrNull { it.name == value } }
        ?: DownloadEngine.YT_DLP

    fun setLastDownloadEngine(value: DownloadEngine) {
        preferences.edit { putString(KEY_DOWNLOAD_ENGINE, value.name) }
    }

    fun lastDownloadContentType(): DownloadContentType? = preferences
        .getString(KEY_DOWNLOAD_CONTENT_TYPE, null)
        ?.let { value -> DownloadContentType.entries.firstOrNull { it.name == value } }

    fun setLastDownloadContentType(value: DownloadContentType) {
        preferences.edit { putString(KEY_DOWNLOAD_CONTENT_TYPE, value.name) }
    }

    fun setAutoUpdateYtDlp(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_AUTO_UPDATE_YT_DLP, enabled) }
    }

    fun markYtDlpUpdated(timestamp: Long = System.currentTimeMillis()) {
        preferences.edit { putLong(KEY_LAST_YT_DLP_UPDATE, timestamp) }
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
        const val KEY_SCREEN_PROTECTION = "acqua_screen_protection"
        private const val KEY_BASE_FOLDER = "acqua_base_folder"
        private const val KEY_CATEGORIZE_MEDIA = "acqua_categorize_media"
        private const val KEY_FILENAME_PATTERN = "acqua_filename_format"
        private const val KEY_MAXIMUM_VIDEO_HEIGHT = "acqua_maximum_video_height"
        private const val KEY_AUDIO_FORMAT = "acqua_audio_output_format"
        private const val KEY_EMBED_METADATA = "acqua_embed_metadata"
        private const val KEY_EMBED_THUMBNAIL = "acqua_embed_thumbnail"
        private const val KEY_DOWNLOAD_ENGINE = "acqua_last_download_engine"
        private const val KEY_DOWNLOAD_CONTENT_TYPE = "acqua_last_download_content_type"
        private const val KEY_AUTO_UPDATE_YT_DLP = "acqua_auto_update_ytdlp"
        private const val KEY_LAST_YT_DLP_UPDATE = "acqua_last_ytdlp_update"
        private const val DEFAULT_BASE_FOLDER = "Acqua"
    }
}
