package dev.qtremors.acqua.data.settings

import android.content.Context
import androidx.core.content.edit
import dev.qtremors.acqua.downloader.FilenameFormatter
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.downloader.DownloadOptionsCodec
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.MediaKind
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow

data class DownloadSettings(
    val baseFolder: String,
    val categorizeMedia: Boolean,
    val filenamePattern: String,
    val audioFilenamePattern: String = FilenameFormatter.DEFAULT_AUDIO_PATTERN,
    val destinations: DownloadDestinations = DownloadDestinations()
)

data class MediaProcessingSettings(
    val maximumVideoHeight: Int,
    val audioFormat: AudioOutputFormat,
    val embedMetadata: Boolean,
    val embedThumbnail: Boolean,
    val autoUpdateYtDlp: Boolean,
    val lastYtDlpUpdate: Long,
    val cropArtwork: Boolean = true,
    val preserveOriginalMetadata: Boolean = false,
    val ytDlpUpdateChannel: dev.qtremors.acqua.downloader.YtDlpUpdateChannel = dev.qtremors.acqua.downloader.YtDlpUpdateChannel.STABLE,
    val ytDlpUpdateInterval: dev.qtremors.acqua.downloader.YtDlpUpdateInterval = dev.qtremors.acqua.downloader.YtDlpUpdateInterval.WEEKLY,
    val lastYtDlpAttempt: Long = 0L
)

data class LastDownloadSelection(val engine: DownloadEngine, val options: YtDlpDownloadOptions)

interface SettingsStore : ConcurrentDownloadSettings {
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
    fun setCropArtwork(enabled: Boolean)
    fun setPreserveOriginalMetadata(enabled: Boolean)
    fun setAutoUpdateYtDlp(enabled: Boolean)
    fun setYtDlpUpdateChannel(value: dev.qtremors.acqua.downloader.YtDlpUpdateChannel) {}
    fun setYtDlpUpdateInterval(value: dev.qtremors.acqua.downloader.YtDlpUpdateInterval) {}
    fun lastDownloadEngine(): DownloadEngine
    fun setLastDownloadEngine(value: DownloadEngine)
    fun lastDownloadContentType(): DownloadContentType?
    fun setLastDownloadContentType(value: DownloadContentType)
    fun downloadDefaults(): YtDlpDownloadOptions = YtDlpDownloadOptions()
    fun setDownloadDefaults(options: YtDlpDownloadOptions) {}
    fun preferenceChanges(): Flow<Unit> = emptyFlow()
    fun lastDownloadSelection(kind: MediaKind): LastDownloadSelection? = null
    fun rememberDownloadSelection(kind: MediaKind, selection: LastDownloadSelection) {}
}

class AppSettingsRepository(context: Context) : SettingsStore, ConcurrentDownloadSettings by DownloadConcurrencyPreferences(context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val destinationStore = DownloadDestinationStore(context)

    internal fun hasLegacyUserPreferences(): Boolean {
        val maintenanceKeys = setOf(KEY_LAST_YT_DLP_UPDATE, KEY_YT_DLP_ATTEMPT, KEY_YT_DLP_VERSION)
        return preferences.all.keys.any { it !in maintenanceKeys }
    }

    override fun lastDownloadSelection(kind: MediaKind): LastDownloadSelection? = runCatching {
        val encoded = preferences.getString("$KEY_LAST_SELECTION.${kind.name}", null) ?: return null
        val engine = preferences.getString("$KEY_LAST_SELECTION.${kind.name}.engine", null)
            ?.let(DownloadEngine::valueOf) ?: lastDownloadEngine()
        LastDownloadSelection(engine, DownloadOptionsCodec.decode(encoded, YtDlpDownloadOptions()))
    }.getOrNull()

    override fun rememberDownloadSelection(kind: MediaKind, selection: LastDownloadSelection) {
        val reusable = selection.options.copy(videoFormatId = null, audioFormatId = null,
            cookieProfileId = null, cookieProfileFingerprint = null,
            additionalAudioFormatIds = emptySet(), acceptedStreams = emptyList(),
            commandTemplateId = null, commandTemplateSnapshot = null, formatSelectorOverride = null, templateAudioMultistreams = false,
            videoFormatIncludesAudio = true, clipStartSeconds = null, clipEndSeconds = null, clipRanges = emptyList(), captionTiming = dev.qtremors.acqua.downloader.CaptionTiming(), splitChapters = false,
            filenamePatternOverride = null, audioFilenamePatternOverride = null)
        val remembered = selection.copy(options = reusable)
        if (lastDownloadSelection(kind) == remembered) return
        preferences.edit {
            putString("$KEY_LAST_SELECTION.${kind.name}", DownloadOptionsCodec.encode(reusable))
            putString("$KEY_LAST_SELECTION.${kind.name}.engine", selection.engine.name)
        }
    }

    override fun downloadDefaults(): YtDlpDownloadOptions = runCatching {
        DownloadOptionsCodec.decode(preferences.getString(KEY_DOWNLOAD_DEFAULTS, null), YtDlpDownloadOptions())
    }.getOrDefault(YtDlpDownloadOptions())

    override fun setDownloadDefaults(options: YtDlpDownloadOptions) {
        val defaults = YtDlpDownloadOptions(videoContainer = options.videoContainer, audioBitrateKbps = options.audioBitrateKbps,
            captionMode = options.captionMode, captionFormat = options.captionFormat,
            captionLanguages = options.captionLanguages, automaticCaptions = options.automaticCaptions,
            skipArchived = options.skipArchived, recordHistory = options.recordHistory, verboseDiagnostics = options.verboseDiagnostics,
            network = options.network.validated(), removeSegments = options.removeSegments.toSet(), formatPolicy = options.formatPolicy.validated().copy(deviceCodecs = emptySet()))
        preferences.edit { putString(KEY_DOWNLOAD_DEFAULTS, DownloadOptionsCodec.encode(defaults)) }
    }

    override fun preferenceChanges(): Flow<Unit> = callbackFlow {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

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
        ).orEmpty().ifBlank { FilenameFormatter.DEFAULT_AUDIO_PATTERN },
        destinations = destinationStore.get()
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
        lastYtDlpUpdate = preferences.getLong(KEY_LAST_YT_DLP_UPDATE, 0L),
        cropArtwork = preferences.getBoolean(KEY_CROP_ARTWORK, true),
        preserveOriginalMetadata = preferences.getBoolean(KEY_ORIGINAL_METADATA, false),
        ytDlpUpdateChannel = dev.qtremors.acqua.downloader.YtDlpUpdateChannel.entries.firstOrNull {
            it.name == preferences.getString(KEY_YT_DLP_CHANNEL, null) } ?: dev.qtremors.acqua.downloader.YtDlpUpdateChannel.STABLE,
        ytDlpUpdateInterval = dev.qtremors.acqua.downloader.YtDlpUpdateInterval.entries.firstOrNull {
            it.name == preferences.getString(KEY_YT_DLP_INTERVAL, null) } ?: dev.qtremors.acqua.downloader.YtDlpUpdateInterval.WEEKLY,
        lastYtDlpAttempt = preferences.getLong(KEY_YT_DLP_ATTEMPT, 0L)
    )

    override fun setMaximumVideoHeight(value: Int) {
        preferences.edit { putInt(KEY_MAXIMUM_VIDEO_HEIGHT, value.coerceAtLeast(0)) }
        lastDownloadSelection(MediaKind.VIDEO)?.let {
            rememberDownloadSelection(MediaKind.VIDEO, it.copy(options = it.options.copy(maximumVideoHeight = value.coerceAtLeast(0))))
        }
    }

    override fun setAudioFormat(value: AudioOutputFormat) {
        preferences.edit { putString(KEY_AUDIO_FORMAT, value.preferenceValue) }
        lastDownloadSelection(MediaKind.AUDIO)?.let {
            rememberDownloadSelection(MediaKind.AUDIO, it.copy(options = it.options.copy(audioFormat = value)))
        }
    }

    override fun setEmbedMetadata(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_EMBED_METADATA, enabled) }
    }

    override fun setEmbedThumbnail(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_EMBED_THUMBNAIL, enabled) }
    }

    override fun setCropArtwork(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_CROP_ARTWORK, enabled) }
    }

    override fun setPreserveOriginalMetadata(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_ORIGINAL_METADATA, enabled) }
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

    override fun setYtDlpUpdateChannel(value: dev.qtremors.acqua.downloader.YtDlpUpdateChannel) {
        if (mediaProcessingSettings().ytDlpUpdateChannel == value) return
        preferences.edit { putString(KEY_YT_DLP_CHANNEL, value.name); remove(KEY_LAST_YT_DLP_UPDATE); remove(KEY_YT_DLP_ATTEMPT) }
    }

    override fun setYtDlpUpdateInterval(value: dev.qtremors.acqua.downloader.YtDlpUpdateInterval) {
        preferences.edit { putString(KEY_YT_DLP_INTERVAL, value.name) }
    }

    fun cachedYtDlpVersion(): String? = preferences.getString(KEY_YT_DLP_VERSION, null)

    fun markYtDlpAttempt(timestamp: Long) { preferences.edit { putLong(KEY_YT_DLP_ATTEMPT, timestamp) } }

    fun markYtDlpUpdated(timestamp: Long = System.currentTimeMillis(), version: String? = null) {
        preferences.edit { putLong(KEY_LAST_YT_DLP_UPDATE, timestamp); putString(KEY_YT_DLP_VERSION, version) }
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
        private const val KEY_CROP_ARTWORK = "acqua_crop_artwork"
        private const val KEY_ORIGINAL_METADATA = "acqua_original_metadata"
        private const val KEY_EMBED_THUMBNAIL = "acqua_embed_thumbnail"
        private const val KEY_DOWNLOAD_ENGINE = "acqua_last_download_engine"
        private const val KEY_DOWNLOAD_CONTENT_TYPE = "acqua_last_download_content_type"
        private const val KEY_DOWNLOAD_DEFAULTS = "acqua_download_defaults"
        private const val KEY_LAST_SELECTION = "acqua_last_selection"
        private const val KEY_AUTO_UPDATE_YT_DLP = "acqua_auto_update_ytdlp"
        private const val KEY_LAST_YT_DLP_UPDATE = "acqua_last_ytdlp_update"
        private const val KEY_YT_DLP_ATTEMPT = "acqua_ytdlp_attempt"
        private const val KEY_YT_DLP_CHANNEL = "acqua_ytdlp_channel"
        private const val KEY_YT_DLP_INTERVAL = "acqua_ytdlp_interval"
        private const val KEY_YT_DLP_VERSION = "acqua_ytdlp_version"
        private const val DEFAULT_BASE_FOLDER = "Acqua"
    }
}
