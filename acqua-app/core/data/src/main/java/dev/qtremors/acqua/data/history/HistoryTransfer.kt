package dev.qtremors.acqua.data.history

import android.content.Context
import android.net.Uri
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.downloader.CommandTemplatePreview
import dev.qtremors.acqua.downloader.CommandTemplateStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.UUID

enum class HistoryTransferFormat { JSON, URLS }
enum class InaccessibleHistoryPolicy { KEEP, LINKS_ONLY, SKIP }
class InvalidHistoryDocumentException : IllegalArgumentException("Could not read this History document. Check its format and size.")
data class HistoryImportPreview(val entries: List<HistoryEntry>, val duplicates: Int, val unavailableIds: Set<String>,
    val templates: CommandTemplatePreview? = null)

/** Interchange never grants file access or starts downloads. Drafts live outside saved-state bundles. */
class HistoryTransfer(context: Context, private val repository: HistoryRepository = HistoryRepository(context)) {
    private val app = context.applicationContext
    private val drafts = File(app.cacheDir, "history_transfer")
    private val templates = CommandTemplateStore(app)

    fun export(format: HistoryTransferFormat, ids: Set<String>? = null): String {
        val entries = repository.exportEntries(ids)
        return bounded(if (format == HistoryTransferFormat.JSON) encode(entries) else entries.map { validUrl(it.url) }.distinct().joinToString("\n"))
    }

    fun stage(text: String): String {
        val checked = bounded(text.removePrefix("\uFEFF"))
        previewText(checked)
        prepareDrafts()
        val token = UUID.randomUUID().toString()
        file(token).writeText(checked, Charsets.UTF_8)
        return token
    }

    fun read(uri: Uri): String = app.contentResolver.openInputStream(uri)?.use { stream ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_BYTES) invalid()
            output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        stage(decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString())
    } ?: invalid()

    fun preview(token: String): HistoryImportPreview = previewText(readDraft(token))

    fun relink(token: String, id: String, uri: Uri) {
        if (uri.scheme != "content") invalid()
        val parsed = parse(readDraft(token))
        if (parsed.templates != null || parsed.entries.none { it.id == id && it.isDownloaded }) invalid()
        val updated = parsed.entries.map { if (it.id == id) it.copy(fileUri = uri.toString()) else it }
        if (!repository.fileExists(updated.first { it.id == id })) invalid()
        file(token).writeText(bounded(encode(updated)), Charsets.UTF_8)
    }

    fun apply(token: String, policy: InaccessibleHistoryPolicy): Int {
        val preview = preview(token)
        val commands = preview.templates
        if (commands != null) {
            templates.applyImport(commands)
            discard(token)
            return commands.templates.size
        }
        val imported = preview.entries.mapNotNull { entry ->
            if (entry.id !in preview.unavailableIds) entry else when (policy) {
                InaccessibleHistoryPolicy.KEEP -> entry
                InaccessibleHistoryPolicy.LINKS_ONLY -> entry.copy(isDownloaded = false, fileUri = "", sizeBytes = 0)
                InaccessibleHistoryPolicy.SKIP -> null
            }
        }
        val count = repository.importEntries(imported)
        discard(token)
        return count
    }

    fun write(uri: Uri, token: String) {
        val bytes = readDraft(token).toByteArray(Charsets.UTF_8)
        app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: invalid()
        discard(token)
    }

    fun stageExport(format: HistoryTransferFormat, ids: Set<String>?): String {
        val text = export(format, ids)
        prepareDrafts()
        return UUID.randomUUID().toString().also { file(it).writeText(text, Charsets.UTF_8) }
    }

    fun discard(token: String) { file(token).delete() }

    fun retainRead(uri: Uri) {
        require(uri.scheme == "content")
        app.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun relinkExisting(id: String, uri: Uri) = repository.relink(id, uri)

    private fun prepareDrafts() {
        check(drafts.isDirectory || drafts.mkdirs())
        drafts.listFiles()?.filter { it.name.matches(Regex("[a-f0-9-]{36}\\.json")) &&
            System.currentTimeMillis() - it.lastModified() > MAX_DRAFT_AGE }?.forEach(File::delete)
    }

    private fun readDraft(token: String): String {
        val source = file(token)
        if (!source.isFile || source.length() > MAX_BYTES || System.currentTimeMillis() - source.lastModified() > MAX_DRAFT_AGE) invalid()
        return bounded(source.readText(Charsets.UTF_8))
    }

    private fun file(token: String): File {
        if (runCatching { UUID.fromString(token).toString() == token }.getOrDefault(false).not()) invalid()
        return File(drafts, "$token.json")
    }

    private fun previewText(text: String): HistoryImportPreview {
        val parsed = parse(text)
        if (parsed.templates != null) return parsed
        val existing = repository.load()
        val ids = existing.mapTo(mutableSetOf(), HistoryEntry::id)
        val identities = existing.mapTo(mutableSetOf(), ::identity)
        var duplicates = 0
        val unique = parsed.entries.filter { entry ->
            if (entry.id in ids || identity(entry) in identities) { duplicates++; false }
            else { ids += entry.id; identities += identity(entry); true }
        }
        return HistoryImportPreview(unique, duplicates, unique.filter { it.isDownloaded &&
            (Uri.parse(it.fileUri).scheme != "content" || !repository.fileExists(it)) }.mapTo(mutableSetOf(), HistoryEntry::id))
    }

    private fun parse(text: String): HistoryImportPreview = runCatching {
        if (!text.trimStart().startsWith('{')) {
            val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
            require(lines.size in 1..MAX_RECORDS)
            return@runCatching HistoryImportPreview(lines.map { url -> HistoryEntry(UUID.nameUUIDFromBytes(validUrl(url).toByteArray()).toString(),
                System.currentTimeMillis(), validUrl(url), validUrl(url), "", false, "", 0, false, sourceHost = WebLink.host(url)) }, 0, emptySet())
        }
        val parser = org.json.JSONTokener(text)
        val root = parser.nextValue() as? JSONObject ?: invalid()
        require(parser.nextClean() == '\u0000')
        if (root.optString("kind") == "acqua.command-templates") return@runCatching HistoryImportPreview(emptyList(), 0, emptySet(), templates.previewImport(text, "Imported"))
        require(root.keys().asSequence().toSet() == setOf("kind", "version", "entries"))
        require(root.get("version") == 1)
        val library = when (root.getString("kind")) { "acqua.history" -> false; "acqua.library" -> true; else -> error("Unsupported document") }
        val rows = root.getJSONArray("entries")
        require(rows.length() <= MAX_RECORDS)
        HistoryImportPreview((0 until rows.length()).map { index -> parseEntry(rows.getJSONObject(index), library) }, 0, emptySet())
    }.getOrElse { invalid() }

    private fun parseEntry(row: JSONObject, library: Boolean): HistoryEntry {
        val allowed = if (library) LIBRARY_KEYS else HISTORY_KEYS
        require(row.keys().asSequence().all { it in allowed })
        require(row.keys().asSequence().filter { it !in setOf("timestamp", "sizeBytes", "isVideo", "isDownloaded") }
            .all { row.isNull(it) || row.get(it) is String })
        listOf("timestamp", "sizeBytes").forEach { key -> require(row.get(key) is Number && row.get(key).toString().toLongOrNull() != null) }
        if (!library) require(row.get("isDownloaded") is Boolean && row.get("isVideo") is Boolean)
        val url = validUrl(row.getString(if (library) "sourceUrl" else "url"))
        val downloaded = if (library) true else row.getBoolean("isDownloaded")
        val rawUri = row.optString(if (library) "uri" else "fileUri")
        require(rawUri.length <= 8192 && rawUri.none(Char::isISOControl))
        val uri = rawUri.takeIf { downloaded && Uri.parse(it).scheme == "content" }.orEmpty()
        if (rawUri.isNotEmpty()) require(Uri.parse(rawUri).scheme in setOf("content", "file"))
        val mime = row.getString("mimeType").also { require(it.length <= 128 && (it.isEmpty() || it.matches(Regex("[A-Za-z0-9.+_-]+/[A-Za-z0-9.+_-]+")))) }
        val id = if (library) row.optString("id").ifBlank { UUID.nameUUIDFromBytes("$url#$rawUri".toByteArray()).toString() } else row.getString("id")
        require(id.isNotBlank() && id.length <= 200 && id.none(Char::isISOControl))
        fun optional(key: String): String? = if (row.isNull(key) || !row.has(key)) null else row.getString(key).also { require(it.length <= 1024 && it.none { c -> c.isISOControl() && c !in "\n\t" }) }
        return HistoryEntry(id, row.getLong("timestamp").also { require(it >= 0) }, url,
            row.getString("fileName").also { require(it.length <= 512 && it.none(Char::isISOControl)) }, uri,
            if (library) mime.startsWith("video/") else row.getBoolean("isVideo"), mime,
            row.getLong("sizeBytes").also { require(it >= 0) }, downloaded, title = optional("title"), author = optional("author"),
            sourceIdentity = optional("sourceIdentity"), sourceHost = WebLink.host(url))
    }

    companion object {
        const val MAX_BYTES = 1024 * 1024
        const val MAX_RECORDS = 2000
        const val MAX_CLIPBOARD_BYTES = 256 * 1024
        private const val MAX_DRAFT_AGE = 24 * 60 * 60 * 1000L
        private val HISTORY_KEYS = setOf("id", "timestamp", "url", "fileName", "fileUri", "isVideo", "mimeType", "sizeBytes", "isDownloaded", "title", "author", "sourceIdentity")
        private val LIBRARY_KEYS = HISTORY_KEYS - setOf("url", "fileUri", "isVideo", "isDownloaded") + setOf("sourceUrl", "uri")
        internal fun identity(entry: HistoryEntry): String = when {
            !entry.isDownloaded -> "link:${entry.url}"
            entry.fileUri.isNotEmpty() -> "file:${entry.fileUri}"
            else -> "unavailable:${entry.url}#${entry.fileName}#${entry.mimeType}"
        }
        private fun invalid(): Nothing = throw InvalidHistoryDocumentException()
        private fun bounded(text: String): String = text.also { if (it.toByteArray(Charsets.UTF_8).size > MAX_BYTES) invalid() }
        private fun validUrl(value: String): String {
            if (value.length > 8192 || value.any { it.isWhitespace() || it.isISOControl() }) invalid()
            val uri = runCatching { URI(value) }.getOrElse { invalid() }
            if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) invalid()
            return uri.toASCIIString()
        }
        private fun encode(entries: List<HistoryEntry>): String = JSONObject().put("kind", "acqua.history").put("version", 1)
            .put("entries", JSONArray(entries.map { entry -> JSONObject().put("id", entry.id).put("timestamp", entry.timestamp).put("url", entry.url)
                .put("fileName", entry.fileName).put("fileUri", entry.fileUri).put("isVideo", entry.isVideo).put("mimeType", entry.mimeType)
                .put("sizeBytes", entry.sizeBytes).put("isDownloaded", entry.isDownloaded).put("title", entry.title)
                .put("author", entry.author).put("sourceIdentity", entry.sourceIdentity) })).toString()
    }
}
