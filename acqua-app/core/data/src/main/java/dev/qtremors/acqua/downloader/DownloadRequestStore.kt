package dev.qtremors.acqua.downloader

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.work.Data
import org.json.JSONObject
import java.util.UUID

/** Private, versioned request snapshots. Platform extras contain only the request identity. */
internal class DownloadRequestStore(context: Context, private val profiles: dev.qtremors.acqua.data.session.CookieProfileStore = dev.qtremors.acqua.data.session.CookieProfileStore(context)) {
    private val preferences = context.applicationContext.getSharedPreferences("download_requests_v1", Context.MODE_PRIVATE)
    private val resolver = context.applicationContext.contentResolver

    fun put(id: UUID, data: Data, timeline: String? = null, authorization: String? = null) = synchronized(LOCK) {
        val values = JSONObject()
        data.keyValueMap.forEach { (key, value) ->
            if (value != null) values.put(key, JSONObject().put("type", value.javaClass.simpleName).put("value", value))
        }
        val createdAt = created(id.toString()).takeIf { it > 0L } ?: System.currentTimeMillis()
        check(preferences.edit().putString(id.toString(), JSONObject().put("version", 1)
            .put("created", createdAt).put("request", values).put("timeline", timeline).put("authorization", authorization).toString()).commit()) {
            "Could not save the download request."
        }
    }

    fun changed() { changes.value += 1L }

    fun input(id: UUID): Data? = synchronized(LOCK) {
        runCatching {
            val root = JSONObject(preferences.getString(id.toString(), null) ?: return null)
            require(root.getInt("version") == 1)
            val values = root.getJSONObject("request")
            val builder = Data.Builder()
            values.keys().forEach { key ->
                val encoded = values.getJSONObject(key)
                when (encoded.getString("type")) {
                    "String" -> builder.putString(key, encoded.getString("value"))
                    "Integer" -> builder.putInt(key, encoded.getInt("value"))
                    "Long" -> builder.putLong(key, encoded.getLong("value"))
                    "Boolean" -> builder.putBoolean(key, encoded.getBoolean("value"))
                }
            }
            builder.build()
        }.getOrNull()
    }

    fun resolve(data: Data): DownloadWorkRequest? = data.getString(DownloadWorkData.KEY_REQUEST_REF)?.let { raw ->
        runCatching { val id = UUID.fromString(raw); input(id)?.let(DownloadWorkData::decode)?.let { request ->
            val snapshot = timeline(id)
            val session = authorization(id)?.let(profiles::restore)
            require(request.options.cookieProfileId == session?.id)
            request.copy(media = MediaTimelineSource.restore(snapshot, request.media).copy(authorizedSession = session?.json(),
                requestUserAgent = session?.userAgent ?: request.media.requestUserAgent,
                explicitBrowserSessionAuthorized = session != null || request.media.explicitBrowserSessionAuthorized),
                outputMedia = MediaTimelineSource.restore(snapshot, request.outputMedia))
        } }.getOrNull()
    } ?: DownloadWorkData.decode(data)

    fun timeline(id: UUID): String? = preferences.getString(id.toString(), null)?.let { text ->
        JSONObject(text).let { root -> if (root.has("timeline")) root.getString("timeline") else null }
    }
    fun authorization(id: UUID): String? = preferences.getString(id.toString(), null)?.let { text ->
        JSONObject(text).let { root -> if (root.has("authorization")) root.getString("authorization") else null }
    }

    fun created(id: String): Long = runCatching {
        JSONObject(preferences.getString(id, null) ?: return 0L).optLong("created")
    }.getOrDefault(0L)

    fun replacement(id: UUID): UUID? = preferences.getString("retry:$id", null)?.let(UUID::fromString)
    fun recordReplacement(id: UUID, replacement: UUID) {
        check(preferences.edit().putString("retry:$id", replacement.toString()).commit()) { "Could not save the retry identity." }
        changes.value += 1L
    }

    fun published(id: String, artifact: String): Uri? = synchronized(LOCK) {
        val value = preferences.getString("output:$id:$artifact", null) ?: return null
        val uri = Uri.parse(value)
        runCatching { resolver.openFileDescriptor(uri, "r")?.use { uri } }.getOrNull()
    }

    fun recordPublished(id: String, artifact: String, uri: Uri) = synchronized(LOCK) {
        check(preferences.edit().putString("output:$id:$artifact", uri.toString()).commit()) {
            "Could not save the published output checkpoint."
        }
    }

    fun remove(id: UUID) = synchronized(LOCK) { preferences.edit(commit = true) { remove(id.toString()) } }

    fun publishedCount(id: String): Int = preferences.all.keys.count { it.startsWith("output:$id:") }

    fun discard(ids: List<String>) = synchronized(LOCK) {
        val keys = preferences.all.keys
        if (ids.none { it in keys || "retry:$it" in keys }) return@synchronized
        val retainedPublications = keys.asSequence().filter { it.length == 36 && ':' !in it && it !in ids }.mapNotNull { raw ->
            runCatching { input(UUID.fromString(raw))?.let(DownloadWorkData::decode)?.publicationId }.getOrNull()
        }.toSet()
        val removed = mutableSetOf<String>()
        ids.forEach { raw ->
            val request = runCatching { input(UUID.fromString(raw))?.let(DownloadWorkData::decode) }.getOrNull()
            removed += listOf(raw, "retry:$raw").filter { it in keys }
            val publication = request?.publicationId ?: raw
            if (publication !in retainedPublications) {
                removed += keys.filter { it.startsWith("output:$publication:") }
            }
        }
        if (removed.isNotEmpty()) {
            val edit = preferences.edit()
            removed.forEach(edit::remove)
            check(edit.commit())
            val retained = preferences.all.filterKeys { key -> key.length == 36 && ':' !in key }.values.mapNotNull { value ->
                runCatching { JSONObject(value as String).optString("authorization").takeIf { it.startsWith("profile:") } }.getOrNull()
            }.toSet()
            profiles.pruneSnapshots(retained)
        }
    }

    companion object {
        private val LOCK = Any()
        val changes = kotlinx.coroutines.flow.MutableStateFlow(0L)
    }
}
