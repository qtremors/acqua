package dev.qtremors.acqua.downloader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class DownloadDiagnostic(
    val id: String,
    val timestamp: Long,
    val failure: DownloadFailure,
    val phase: DownloadPhase,
    val backend: String,
    val summary: String,
    val details: String? = null
) {
    fun copyText(): String = listOfNotNull("Task: $id", "Time: $timestamp", "Failure: ${failure.code}",
        "Phase: ${phase.name}", "Backend: $backend", summary, details).joinToString("\n").take(10_240)
}

class DownloadDiagnosticsStore(context: Context, private val now: () -> Long = System::currentTimeMillis) {
    private val preferences = context.applicationContext.getSharedPreferences("acqua_diagnostics_v1", Context.MODE_PRIVATE)

    fun load(): List<DownloadDiagnostic> = synchronized(LOCK) {
        val stored = read()
        val retained = stored.filter { now() - it.timestamp <= RETENTION_MS }.sortedByDescending { it.timestamp }.take(MAX_RECORDS)
        if (stored != retained) write(retained)
        retained
    }

    fun get(id: String): DownloadDiagnostic? = load().firstOrNull { it.id == id }

    fun clear() = synchronized(LOCK) { check(preferences.edit().clear().commit()) { "Could not clear diagnostics" } }

    fun record(id: String, error: Throwable, failure: DownloadFailure, phase: DownloadPhase,
        backend: String, transcript: DiagnosticTranscript? = null) = synchronized(LOCK) {
        require(isRecordId(id))
        val summary = SafeDiagnostics.redact(error).message.orEmpty()
        val details = transcript?.finish(error)
        val entry = DownloadDiagnostic(id, now(), failure, phase, backend.take(32), summary, details)
        write((listOf(entry) + load().filterNot { it.id == id }).sortedByDescending { it.timestamp }.take(MAX_RECORDS))
    }

    private fun read(): List<DownloadDiagnostic> = runCatching {
        val raw = preferences.getString("records", "[]").orEmpty()
        require(raw.length <= 300_000)
        val rows = JSONArray(raw)
        buildList {
            for (index in 0 until minOf(rows.length(), MAX_RECORDS)) {
                val row = rows.getJSONObject(index)
                val id = row.getString("id")
                if (!isRecordId(id)) continue
                add(DownloadDiagnostic(id, row.getLong("time"), DownloadFailure.fromCode(row.getString("failure")),
                    DownloadPhase.valueOf(row.getString("phase")), row.getString("backend").take(32),
                    SafeDiagnostics.text(row.getString("summary")),
                    row.optString("details").takeIf { it.isNotBlank() && it != "null" }?.let { SafeDiagnostics.text(it, 8192) }))
            }
        }
    }.onFailure { preferences.edit().remove("records").commit() }.getOrDefault(emptyList())

    private fun write(entries: List<DownloadDiagnostic>) {
        val array = JSONArray()
        entries.forEach { entry -> array.put(JSONObject().put("id", entry.id).put("time", entry.timestamp)
            .put("failure", entry.failure.code).put("phase", entry.phase.name).put("backend", entry.backend)
            .put("summary", entry.summary).put("details", entry.details)) }
        check(preferences.edit().putString("records", array.toString()).commit()) { "Could not save diagnostics" }
    }

    companion object {
        const val EXTRA_TASK_ID = "dev.qtremors.acqua.diagnostic_task"
        const val MAX_RECORDS = 20
        const val RETENTION_MS = 7 * 24 * 60 * 60 * 1000L
        private val LOCK = Any()
        fun isRecordId(value: String): Boolean = value == "app-crash" || runCatching { UUID.fromString(value) }.isSuccess
    }
}

class DiagnosticTranscript(private val enabled: Boolean) {
    private val lines = ArrayDeque<String>()
    @Synchronized fun append(value: String) {
        if (!enabled || value.isBlank()) return
        val safe = SafeDiagnostics.text(value, 256)
        if (lines.lastOrNull() != safe) lines.addLast(safe)
        while (lines.size > 24) lines.removeFirst()
    }
    @Synchronized fun finish(error: Throwable): String? = if (!enabled) null else {
        val trace = error.stackTrace.take(12).joinToString("\n") { "${it.className}.${it.methodName}:${it.lineNumber}" }
        SafeDiagnostics.text((lines + trace).joinToString("\n"), 8192)
    }
}

class DiagnosticCrashHandler(
    private val store: DownloadDiagnosticsStore,
    private val verbose: () -> Boolean,
    private val next: Thread.UncaughtExceptionHandler
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, error: Throwable) {
        try {
            store.record("app-crash", error, error.toDownloadFailure(), DownloadPhase.COMPLETE, "app",
                DiagnosticTranscript(verbose()))
        } catch (_: RuntimeException) {
            // Diagnostics must never replace Android's existing crash handler.
        } finally { next.uncaughtException(thread, error) }
    }
}
