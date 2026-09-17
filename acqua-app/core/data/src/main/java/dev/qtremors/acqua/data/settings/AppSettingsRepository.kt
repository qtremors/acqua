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
    val filenamePattern: String,
    val audioFilenamePattern: String = FilenameFormatter.DEFAULT_AUDIO_PATTERN
)

data class MediaProcessingSettings(
    val maximumVideoHeight: Int,
    val audioFormat: AudioOutputFormat,
    val embedMetadata: Boolean,
    val embedThumbnail: Boolean,
    val autoUpdateYtDlp: Boolean,
    val lastYtDlpUpdate: Long
)

interface SettingsStore {
    fun screenProtectionEnabled(): Boolean
    fun setScreenProtectionEnabled(enabled: Boolean)
    fun screenProtectionEnabledFlow(): Flow<Boolean>
    fun downloadSettings(): DownloadSettings
    fun setBaseFolder(value: String)
    fun setCategorizeMedia(enabled: Boolean)
    fun setFilenamePattern(value: String)
    fun setAudioFilenamePattern(value: String)
    fun mediaProcessingSettings(): MediaProcessingSettings
    fun setMaximumVideoHeight(value: Int)
    fun setAudioFormat(value: AudioOutputFormat)
    fun setEmbedMetadata(enabled: Boolean)
    fun setEmbedThumbnail(enabled: Boolean)
    fun setAutoUpdateYtDlp(enabled: Boolean)
    fun lastDownloadEngine(): DownloadEngine
    fun setLastDownloadEngine(value: DownloadEngine)
    fun lastDownloadContentType(): DownloadContentType?
    fun setLastDownloadContentType(value: DownloadContentType)
}

class AppSettingsRepository(context: Context) : SettingsStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun useBrowserSessions(): Boolean = preferences.getBoolean(KEY_USE_BROWSER_SESSIONS, false)

    fun setUseBrowserSessions(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_USE_BROWSER_SESSIONS, enabled) }
    }

    override fun screenProtectionEnabled(): Boolean = preferences.getBoolean(KEY_SCREEN_PROTECTION, false)

    override fun setScreenProtectionEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_SCREEN_PROTECTION, enabled) }
    }

    override fun screenProtectionEnabledFlow(): Flow<Boolean> = callbackFlow {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_SCREEN_PROTECTION) trySend(screenProtectionEnabled())
        }
        trySend(screenProtectionEnabled())
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    override fun downloadSettings(): DownloadSettings = DownloadSettings(
        baseFolder = preferences.getString(KEY_BASE_FOLDER, DEFAULT_BASE_FOLDER).orEmpty()
            .ifBlank { DEFAULT_BASE_FOLDER },
        categorizeMedia = preferences.getBoolean(KEY_CATEGORIZE_MEDIA, false),
        filenamePattern = preferences.getString(KEY_FILENAME_PATTERN, FilenameFormatter.DEFAULT_PATTERN)
            .orEmpty()
            .ifBlank { FilenameFormatter.DEFAULT_PATTERN }
            .let { pattern ->
                if (pattern == FilenameFormatter.LEGACY_DEFAULT_PATTERN) FilenameFormatter.DEFAULT_PATTERN
                else pattern
            },
        audioFilenamePattern = preferences.getString(
            KEY_AUDIO_FILENAME_PATTERN,
            FilenameFormatter.DEFAULT_AUDIO_PATTERN
        ).orEmpty().ifBlank { FilenameFormatter.DEFAULT_AUDIO_PATTERN }
    )

    override fun setBaseFolder(value: String) {
        preferences.edit { putString(KEY_BASE_FOLDER, value) }
    }

    override fun setCategorizeMedia(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_CATEGORIZE_MEDIA, enabled) }
    }

    override fun setFilenamePattern(value: String) {
        preferences.edit { putString(KEY_FILENAME_PATTERN, value) }
    }

    override fun setAudioFilenamePattern(value: String) {
        preferences.edit { putString(KEY_AUDIO_FILENAME_PATTERN, value) }
    }

    override fun mediaProcessingSettings(): MediaProcessingSettings = MediaProcessingSettings(
        maximumVideoHeight = preferences.getInt(KEY_MAXIMUM_VIDEO_HEIGHT, 0),
        audioFormat = AudioOutputFormat.fromPreference(
            preferences.getString(KEY_AUDIO_FORMAT, AudioOutputFormat.ORIGINAL.preferenceValue).orEmpty()
        ),
        embedMetadata = preferences.getBoolean(KEY_EMBED_METADATA, true),
        embedThumbnail = preferences.getBoolean(KEY_EMBED_THUMBNAIL, true),
        autoUpdateYtDlp = preferences.getBoolean(KEY_AUTO_UPDATE_YT_DLP, true),
        lastYtDlpUpdate = preferences.getLong(KEY_LAST_YT_DLP_UPDATE, 0L)
    )

    override fun setMaximumVideoHeight(value: Int) {
        preferences.edit { putInt(KEY_MAXIMUM_VIDEO_HEIGHT, value.coerceAtLeast(0)) }
    }

    override fun setAudioFormat(value: AudioOutputFormat) {
        preferences.edit { putString(KEY_AUDIO_FORMAT, value.preferenceValue) }
    }

    override fun setEmbedMetadata(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_EMBED_METADATA, enabled) }
    }

    override fun setEmbedThumbnail(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_EMBED_THUMBNAIL, enabled) }
    }

    override fun lastDownloadEngine(): DownloadEngine = preferences.getString(KEY_DOWNLOAD_ENGINE, null)
        ?.let { value -> DownloadEngine.entries.firstOrNull { it.name == value } }
        ?: DownloadEngine.YT_DLP

    override fun setLastDownloadEngine(value: DownloadEngine) {
        preferences.edit { putString(KEY_DOWNLOAD_ENGINE, value.name) }
    }

    override fun lastDownloadContentType(): DownloadContentType? = preferences
        .getString(KEY_DOWNLOAD_CONTENT_TYPE, null)
        ?.let { value -> DownloadContentType.entries.firstOrNull { it.name == value } }

    override fun setLastDownloadContentType(value: DownloadContentType) {
        preferences.edit { putString(KEY_DOWNLOAD_CONTENT_TYPE, value.name) }
    }

    override fun setAutoUpdateYtDlp(enabled: Boolean) {
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
        private const val KEY_AUDIO_FILENAME_PATTERN = "acqua_audio_filename_format"
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
