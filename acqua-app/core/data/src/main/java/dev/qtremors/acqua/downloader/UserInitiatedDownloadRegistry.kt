package dev.qtremors.acqua.downloader

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.UUID

internal object UserInitiatedDownloadRegistry {
    private val state = MutableStateFlow<Map<UUID, DownloadQueueItem>>(emptyMap())
    @Volatile private var initialized = false

    fun observe(context: Context): StateFlow<Map<UUID, DownloadQueueItem>> {
        ensureInitialized(context)
        return state
    }

    fun reload(context: Context) = synchronized(this) {
        state.value = readStoredState(context)
        initialized = true
    }

    fun contains(context: Context, id: UUID): Boolean {
        ensureInitialized(context)
        return id in state.value
    }

    fun update(context: Context, item: DownloadQueueItem, starting: Boolean = false): Unit = synchronized(this) {
        ensureInitialized(context)
        val id = UUID.fromString(item.id)
        val existing = state.value[id]
        if (existing != null && !existing.isActive) return@synchronized
        if (!starting && existing?.state == DownloadQueueState.RETRYING && item.state == DownloadQueueState.RUNNING) return@synchronized
        state.value = state.value + (id to item)
        checkpointDownloadTask(context, item)
        preferences(context).edit(commit = true) {
            putString(item.id, item.toJson().toString())
        }
    }

    fun remove(context: Context, id: UUID) = synchronized(this) {
        ensureInitialized(context)
        state.value = state.value - id
        preferences(context).edit(commit = true) { remove(id.toString()) }
    }

    fun removeTerminals(context: Context, ids: List<String>) = synchronized(this) {
        ensureInitialized(context)
        val removed = state.value.filter { (id, item) -> id.toString() in ids && !item.isActive }.keys
        if (removed.isNotEmpty()) {
            state.value = state.value - removed
            preferences(context).edit(commit = true) { removed.forEach { remove(it.toString()) } }
        }
    }

    private fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            state.value = readStoredState(context)
            initialized = true
        }
    }

    @androidx.annotation.RequiresApi(34)
    fun reconcile(context: Context) = synchronized(this) {
        ensureInitialized(context)
        val pending = context.getSystemService(android.app.job.JobScheduler::class.java).allPendingJobs.map { it.id }.toSet()
        state.value.values.filter { it.isActive && UserInitiatedDownloadScheduler.jobId(UUID.fromString(it.id)) !in pending }
            .forEach { update(context, it.copy(state = DownloadQueueState.FAILED,
                error = DownloadFailure.PLATFORM_LIMIT.code, phase = DownloadPhase.COMPLETE)) }
    }

    private fun readStoredState(context: Context): Map<UUID, DownloadQueueItem> =
        preferences(context).all.mapNotNull { (key, value) ->
            runCatching {
                val id = UUID.fromString(key)
                id to JSONObject(value as String).toQueueItem(id)
            }.getOrNull()
        }.toMap()

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences(
        STORE_NAME,
        Context.MODE_PRIVATE
    )

    private fun DownloadQueueItem.toJson() = JSONObject()
        .put("state", state.name)
        .put("progress", progress.toDouble())
        .put("eta", etaSeconds)
        .put("downloaded", downloadedBytes)
        .put("total", totalBytes)
        .put("error", error)
        .put("phase", phase.name)
        .put("artifacts", artifactCount)

    private fun JSONObject.toQueueItem(id: UUID) = DownloadQueueItem(
        id = id.toString(),
        state = enumValueOrDefault(optString("state"), DownloadQueueState.FAILED),
        progress = optDouble("progress", 0.0).toFloat().coerceIn(0f, 100f),
        etaSeconds = optLong("eta", 0L).coerceAtLeast(0L),
        downloadedBytes = optLong("downloaded", 0L).coerceAtLeast(0L),
        totalBytes = optLong("total", 0L).coerceAtLeast(0L),
        error = optString("error").takeIf { it.isNotBlank() && it != "null" },
        phase = enumValueOrDefault(optString("phase"), DownloadPhase.QUEUED),
        artifactCount = optInt("artifacts")
    )

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback

    private const val STORE_NAME = "user_initiated_download_state"
}
