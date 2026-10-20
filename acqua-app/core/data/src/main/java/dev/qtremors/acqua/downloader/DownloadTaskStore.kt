package dev.qtremors.acqua.downloader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Retains terminal task records independently of platform scheduler pruning. */
internal class DownloadTaskStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("download_tasks_v1", Context.MODE_PRIVATE)
    fun observe() = observePreferences(preferences, ::read)
    fun read(): List<DownloadQueueItem> = synchronized(LOCK) {
        val entries = runCatching { JSONArray(preferences.getString("tasks", "[]")) }.getOrDefault(JSONArray())
        (0 until entries.length()).mapNotNull { index -> runCatching { decode(entries.getJSONObject(index)) }.getOrNull() }
    }
    fun hidden(): Set<String> = preferences.getStringSet("hidden", emptySet()).orEmpty().toSet()
    fun record(items: List<DownloadQueueItem>): List<String> = synchronized(LOCK) {
        val before = read()
        val terminal = items.filter { !it.isActive && it.id !in hidden() }
        val merged = (before + terminal).associateBy(DownloadQueueItem::id).values
        val retained = merged.filter { it.createdAt >= System.currentTimeMillis() - RETENTION_MILLIS }
            .sortedByDescending(DownloadQueueItem::createdAt).take(MAX_TASKS).sortedBy(DownloadQueueItem::createdAt)
        val encoded = JSONArray(retained.map(::encode)).toString()
        if (encoded != preferences.getString("tasks", "[]")) check(preferences.edit().putString("tasks", encoded).commit())
        merged.map(DownloadQueueItem::id) - retained.map(DownloadQueueItem::id).toSet()
    }
    fun dismiss(id: String) = synchronized(LOCK) {
        val hidden = (hidden() + id).toList().takeLast(MAX_TASKS * 2).toSet()
        check(preferences.edit().putStringSet("hidden", hidden)
            .putString("tasks", JSONArray(read().filterNot { it.id == id }.map(::encode)).toString()).commit())
    }
    private fun encode(item: DownloadQueueItem) = JSONObject().put("id", item.id).put("state", item.state.name)
        .put("progress", item.progress).put("error", item.error).put("created", item.createdAt)
        .put("title", item.title).put("source", item.sourceUrl).put("artifacts", item.artifactCount)
        .put("options", item.acceptedOptions?.let(DownloadOptionsCodec::encode)).put("folder", item.acceptedFolder)
        .put("kind", item.acceptedKind?.name)
    private fun decode(json: JSONObject) = DownloadQueueItem(json.getString("id"), DownloadQueueState.valueOf(json.getString("state")),
        progress = json.optDouble("progress").toFloat(), error = json.optional("error"), createdAt = json.getLong("created"),
        title = json.optional("title"), sourceUrl = json.optional("source"), phase = DownloadPhase.COMPLETE,
        artifactCount = json.optInt("artifacts"), acceptedFolder = json.optional("folder"),
        acceptedKind = json.optional("kind")?.let(dev.qtremors.acqua.domain.MediaKind::valueOf),
        acceptedOptions = json.optional("options")?.let { DownloadOptionsCodec.decode(it, YtDlpDownloadOptions()) })
    private fun JSONObject.optional(key: String) = optString(key).takeIf { it.isNotBlank() && it != "null" }
    companion object {
        private val LOCK = Any()
        const val MAX_TASKS = 200
        const val RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}

internal fun checkpointDownloadTask(context: Context, item: DownloadQueueItem) {
    if (item.isActive) return
    val requests = DownloadRequestStore(context)
    val request = requests.input(java.util.UUID.fromString(item.id))?.let(DownloadWorkData::decode)
    DownloadTaskStore(context).record(listOf(item.copy(
        title = request?.media?.downloadName ?: request?.media?.title ?: request?.media?.username,
        sourceUrl = request?.sourceUrl, createdAt = requests.created(item.id).takeIf { it > 0 } ?: System.currentTimeMillis(),
        acceptedOptions = request?.options, acceptedFolder = request?.storageSettings?.baseFolder,
        acceptedKind = request?.acceptedKind(),
        artifactCount = maxOf(item.artifactCount, requests.publishedCount(request?.publicationId ?: item.id)))))
}

internal fun DownloadWorkRequest.acceptedKind() = if (media.backend == dev.qtremors.acqua.domain.MediaBackend.DIRECT) media.kind
    else if (options.contentType == DownloadContentType.AUDIO) dev.qtremors.acqua.domain.MediaKind.AUDIO else dev.qtremors.acqua.domain.MediaKind.VIDEO
