package dev.qtremors.acqua.downloader

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale

data class CommandTemplatePreview(val templates: List<CommandTemplate>, val added: Int, val updated: Int)

class CommandTemplateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("command_templates_v1", Context.MODE_PRIVATE)
    private val resolver = context.applicationContext.contentResolver

    fun all(): List<CommandTemplate> = synchronized(LOCK) {
        preferences.getString("document", null)?.let(::parseDocument) ?: emptyList()
    }

    fun put(template: CommandTemplate) = synchronized(LOCK) {
        val checked = template.validated()
        save(validateList(all().filterNot { it.id == checked.id } + checked))
    }

    fun remove(id: String) = synchronized(LOCK) { save(all().filterNot { it.id == id }) }

    fun previewImport(text: String, fallbackName: String): CommandTemplatePreview = synchronized(LOCK) {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_DOCUMENT_BYTES) throw InvalidCommandTemplateException()
        val incoming = if (text.trimStart().startsWith('{')) parseDocument(text) else listOf(CommandTemplate(name = fallbackName, command = text).validated())
        val existing = all()
        validateList(existing.filterNot { old -> incoming.any { it.id == old.id } } + incoming)
        val updated = incoming.count { candidate -> existing.any { it.id == candidate.id } }
        CommandTemplatePreview(incoming, incoming.size - updated, updated)
    }

    fun applyImport(preview: CommandTemplatePreview) = synchronized(LOCK) {
        val imported = validateList(preview.templates)
        save(validateList(all().filterNot { old -> imported.any { it.id == old.id } } + imported))
    }

    fun export(): String = synchronized(LOCK) {
        val usedNames = mutableSetOf<String>()
        val templates = all().map { template ->
            val safe = SafeDiagnostics.text(template.name, 70).ifBlank { "Template" }
            var name = safe
            var number = 1
            while (!usedNames.add(name.lowercase(Locale.ROOT))) name = "$safe (${++number})"
            template.copy(name = name)
        }
        encode(templates)
    }

    fun validateAccepted(options: YtDlpDownloadOptions) {
        val id = options.commandTemplateId
        if (id == null) {
            if (options.commandTemplateSnapshot != null || options.formatSelectorOverride != null || options.templateAudioMultistreams) throw MissingCommandTemplateException()
            return
        }
        val template = all().firstOrNull { it.id == id } ?: throw MissingCommandTemplateException()
        if (template.command != options.commandTemplateSnapshot) throw MissingCommandTemplateException()
        CommandTemplateParser.validateOptions(options)
    }

    fun readImport(uri: Uri, fallbackName: String): CommandTemplatePreview {
        val bytes = resolver.openInputStream(uri)?.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_DOCUMENT_BYTES) throw InvalidCommandTemplateException()
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw InvalidCommandTemplateException()
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        return previewImport(decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF"), fallbackName)
    }

    fun writeExport(uri: Uri) {
        val bytes = export().toByteArray(Charsets.UTF_8)
        resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: throw InvalidCommandTemplateException()
    }

    fun previewState(preview: CommandTemplatePreview): String = encode(validateList(preview.templates))

    private fun save(templates: List<CommandTemplate>) {
        check(preferences.edit().putString("document", encode(templates)).commit()) { "Could not save command templates." }
    }

    companion object {
        const val MAX_TEMPLATES = 20
        const val MAX_DOCUMENT_BYTES = 256 * 1024
        private val LOCK = Any()

        private fun encode(templates: List<CommandTemplate>): String = JSONObject().put("kind", "acqua.command-templates").put("version", 1)
            .put("templates", JSONArray(templates.map { JSONObject().put("id", it.id).put("name", it.name).put("command", it.command) })).toString()

        private fun parseDocument(text: String): List<CommandTemplate> {
            if (text.toByteArray(Charsets.UTF_8).size > MAX_DOCUMENT_BYTES) throw InvalidCommandTemplateException()
            return runCatching {
                val parser = org.json.JSONTokener(text)
                val root = parser.nextValue() as? JSONObject ?: throw InvalidCommandTemplateException()
                require(parser.nextClean() == '\u0000')
                require(root.keys().asSequence().toSet() == setOf("kind", "version", "templates"))
                require(root.getString("kind") == "acqua.command-templates" && root.get("version") == 1)
                val rows = root.getJSONArray("templates")
                require(rows.length() <= MAX_TEMPLATES)
                validateList((0 until rows.length()).map { index -> rows.getJSONObject(index).let { row ->
                    require(row.keys().asSequence().toSet() == setOf("id", "name", "command"))
                    require(row.keys().asSequence().all { row.get(it) is String })
                    CommandTemplate(row.getString("id"), row.getString("name"), row.getString("command")).validated()
                } })
            }.getOrElse { throw InvalidCommandTemplateException() }
        }

        private fun validateList(templates: List<CommandTemplate>): List<CommandTemplate> {
            if (templates.size > MAX_TEMPLATES) throw InvalidCommandTemplateException()
            val validated = templates.map(CommandTemplate::validated)
            if (validated.map { it.id }.distinct().size != validated.size ||
                validated.map { it.name.lowercase(Locale.ROOT) }.distinct().size != validated.size) throw InvalidCommandTemplateException()
            return validated
        }
    }
}
