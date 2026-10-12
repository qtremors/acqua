package dev.qtremors.acqua.downloader

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class DownloadArchiveEntry(val key: String, val title: String, val sourceUrl: String,
    val savedAt: Long, val uris: List<String>)
data class DownloadPreset(val name: String, val options: YtDlpDownloadOptions)

internal class DownloadLibraryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("download_library_v1", Context.MODE_PRIVATE)
    private val resolver = context.applicationContext.contentResolver

    fun archive(): List<DownloadArchiveEntry> = synchronized(LOCK) {
        preferences.all.filterKeys { it.startsWith("archive:") }.values.mapNotNull { raw ->
            runCatching {
                val json = JSONObject(raw as String)
                val uris = json.getJSONArray("uris")
                DownloadArchiveEntry(json.getString("key"), json.getString("title"), json.getString("source"),
                    json.getLong("saved"), (0 until uris.length()).map(uris::getString))
            }.getOrNull()
        }.sortedByDescending(DownloadArchiveEntry::savedAt)
    }

    fun existing(request: DownloadWorkRequest): DownloadArchiveEntry? = synchronized(LOCK) {
        val key = identity(request)
        archive().firstOrNull { it.key == key }?.takeIf { entry ->
            entry.uris.isNotEmpty() && entry.uris.all { raw ->
                runCatching { resolver.openFileDescriptor(Uri.parse(raw), "r")?.use { true } == true }.getOrDefault(false)
            }
        }
    }

    fun record(request: DownloadWorkRequest, result: DownloadExecutionResult) = synchronized(LOCK) {
        val key = identity(request)
        val json = JSONObject().put("key", key).put("title", result.title ?: request.media.title ?: request.sourceUrl)
            .put("source", request.sourceUrl).put("saved", System.currentTimeMillis())
            .put("uris", JSONArray(result.artifactUris.map(Uri::toString)))
        check(preferences.edit().putString("archive:$key", json.toString()).commit()) { "Could not save the download archive." }
        val removed = archive().drop(MAX_ARCHIVE_ENTRIES)
        if (removed.isNotEmpty()) {
            val edit = preferences.edit()
            removed.forEach { edit.remove("archive:${it.key}") }
            check(edit.commit())
        }
    }

    fun removeArchive(key: String) { check(preferences.edit().remove("archive:$key").commit()) }
    fun presets(): List<DownloadPreset> = preferences.all.filterKeys { it.startsWith("preset:") }.mapNotNull { (key, value) ->
        runCatching { DownloadPreset(key.removePrefix("preset:"), DownloadOptionsCodec.decode(value as String, YtDlpDownloadOptions())) }.getOrNull()
    }.sortedBy { it.name.lowercase() }

    fun savePreset(preset: DownloadPreset) {
        val name = preset.name.trim()
        require(name.isNotBlank() && name.length <= 60) { "Use a preset name of up to 60 characters." }
        require(preferences.contains("preset:$name") || presets().size < 30) { "Remove a preset before adding another." }
        check(preferences.edit().putString("preset:$name", DownloadOptionsCodec.encode(preset.options.copy(
            videoFormatId = null, audioFormatId = null, clipStartSeconds = null, clipEndSeconds = null))).commit())
    }

    fun removePreset(name: String) { check(preferences.edit().remove("preset:$name").commit()) }
    fun observeArchive(): Flow<List<DownloadArchiveEntry>> = observePreferences(preferences, ::archive)
    fun observePresets(): Flow<List<DownloadPreset>> = observePreferences(preferences, ::presets)

    companion object {
        private val LOCK = Any()
        const val MAX_ARCHIVE_ENTRIES = 1000
        fun identity(request: DownloadWorkRequest): String {
            val media = request.media
            val source = media.sourceIdentity ?: media.collectionUrl?.let { "$it#${media.collectionIndex}" }
                ?: dev.qtremors.acqua.domain.WebLink.mediaAssetIdentity(media.url)
            val kind = if (media.backend == dev.qtremors.acqua.domain.MediaBackend.DIRECT) media.kind.name else request.options.contentType.name
            val selection = if (media.backend == dev.qtremors.acqua.domain.MediaBackend.DIRECT) {
                "${request.options.embedMetadata}:${request.options.preserveOriginalMetadata}" +
                    if (request.options.downloadThumbnail) ":thumbnail" else ""
            } else DownloadOptionsCodec.encode(request.options.copy(skipArchived = false, acceptedStreams = emptyList(),
                commandTemplateId = null, commandTemplateSnapshot = null,
                filenamePatternOverride = null, audioFilenamePatternOverride = null)).let { encoded ->
                if (request.options.downloadThumbnail) encoded else JSONObject(encoded).apply { remove("downloadThumbnail") }.toString()
            }
            val authorization = media.authorizedSession?.let { "|$it" }.orEmpty()
            return MessageDigest.getInstance("SHA-256").digest("$source|$kind|$selection$authorization".toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}

internal fun <T> observePreferences(preferences: SharedPreferences, read: () -> T): Flow<T> = callbackFlow {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
    preferences.registerOnSharedPreferenceChangeListener(listener)
    trySend(read())
    awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
}
