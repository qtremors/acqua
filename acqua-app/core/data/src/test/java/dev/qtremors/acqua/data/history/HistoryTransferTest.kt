package dev.qtremors.acqua.data.history

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.downloader.CommandTemplate
import dev.qtremors.acqua.downloader.CommandTemplateStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryTransferTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = HistoryRepository(context)
    private val transfer = HistoryTransfer(context, repository)
    private val provider = FilesProvider()
    @Before fun reset() {
        repository.clear()
        provider.attachInfo(context, ProviderInfo().apply { authority = "library.test" })
        ShadowContentResolver.registerProviderInternal("library.test", provider)
        context.getSharedPreferences("command_templates_v1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `selected and all exports page through History and retain metadata and dates on round trip`() {
        val entries = (1..260).map { index -> entry(index.toString()) }
        entries.forEach(repository::add)
        val selected = transfer.export(HistoryTransferFormat.JSON, setOf("1", "260"))
        assertEquals(2, JSONObject(selected).getJSONArray("entries").length())
        val all = transfer.export(HistoryTransferFormat.JSON)
        assertEquals(260, JSONObject(all).getJSONArray("entries").length())
        val unchanged = transfer.stage(all)
        assertEquals(260, transfer.preview(unchanged).duplicates)
        assertEquals(entries.size, repository.load().size)
        transfer.discard(unchanged)
        repository.clear()
        val draft = transfer.stage(selected)
        assertEquals(2, HistoryTransfer(context).preview(draft).entries.size)
        assertEquals(2, transfer.apply(draft, InaccessibleHistoryPolicy.KEEP))
        for (expected in listOf(entries.first(), entries.last())) assertEquals(expected, repository.find(expected.id))
        assertEquals(2, transfer.export(HistoryTransferFormat.URLS).lines().size)
    }

    @Test fun `versioned library adapters preserve dates and offer keep links or skip for ungranted paths`() {
        val row = JSONObject().put("sourceUrl", "https://example.com/source").put("fileName", "External.mp4")
            .put("uri", "file:///untrusted/private/path.mp4").put("mimeType", "video/mp4").put("sizeBytes", 123L)
            .put("timestamp", 1234567890123L).put("title", "External title").put("author", "Author").put("sourceIdentity", "source-1")
        val text = JSONObject().put("kind", "acqua.library").put("version", 1).put("entries", JSONArray().put(row)).toString()
        InaccessibleHistoryPolicy.entries.forEach { policy ->
            repository.clear()
            val draft = transfer.stage(text)
            val preview = transfer.preview(draft)
            assertEquals(1, preview.unavailableIds.size)
            assertEquals("", preview.entries.single().fileUri)
            assertTrue(repository.load().isEmpty())
            assertEquals(if (policy == InaccessibleHistoryPolicy.SKIP) 0 else 1, transfer.apply(draft, policy))
            if (policy != InaccessibleHistoryPolicy.SKIP) {
                val imported = repository.load().single()
                assertEquals(1234567890123L, imported.timestamp)
                assertEquals("External title", imported.title)
                assertEquals("Author", imported.author)
                assertEquals("source-1", imported.sourceIdentity)
                assertEquals(policy == InaccessibleHistoryPolicy.KEEP, imported.isDownloaded)
            }
        }
    }

    @Test fun `unsupported malformed oversized or wrongly typed records fail atomically with safe errors`() {
        repository.add(entry("original"))
        val valid = transfer.export(HistoryTransferFormat.JSON)
        val variants = listOf("{", valid + " trailing", "--exec token=private", "https://user:private@example.com/", "file:///private",
            "x".repeat(HistoryTransfer.MAX_BYTES + 1), JSONObject(valid).put("version", 2).toString(),
            JSONObject(valid).put("kind", "unknown").toString(), JSONObject(valid).put("unexpected", true).toString(),
            JSONObject(valid).apply { getJSONArray("entries").getJSONObject(0).put("timestamp", 1.5) }.toString(),
            JSONObject(valid).apply { getJSONArray("entries").getJSONObject(0).put("isDownloaded", "true") }.toString(),
            JSONObject(valid).apply { getJSONArray("entries").getJSONObject(0).put("mimeType", "video/mp4\nsecret") }.toString(),
            JSONObject(valid).apply { getJSONArray("entries").getJSONObject(0).put("customCommand", "secret") }.toString())
        variants.forEach { text ->
            val error = assertThrows(InvalidHistoryDocumentException::class.java) { transfer.stage(text) }
            assertFalse(error.message.orEmpty().contains("private"))
            assertEquals(listOf(entry("original")), repository.load())
        }
        val uri = Uri.parse("content://fixture/malformed")
        shadowOf(context.contentResolver).registerInputStream(uri, byteArrayOf(0xC3.toByte(), 0x28).inputStream())
        assertThrows(java.nio.charset.CharacterCodingException::class.java) { transfer.read(uri) }
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArray(HistoryTransfer.MAX_BYTES + 1).inputStream())
        assertThrows(InvalidHistoryDocumentException::class.java) { transfer.read(uri) }
    }

    @Test fun `preview deduplicates identities and rechecks concurrent changes at apply without overwrites`() {
        repository.add(entry("original"))
        val text = transfer.export(HistoryTransferFormat.JSON)
        val root = JSONObject(text)
        root.getJSONArray("entries").put(JSONObject(root.getJSONArray("entries").getJSONObject(0).toString()).put("id", "different"))
        repository.clear()
        val draft = transfer.stage(root.toString())
        assertEquals(1, transfer.preview(draft).duplicates)
        assertEquals(1, transfer.preview(draft).entries.size)
        repository.add(entry("original").copy(title = "Keep this original"))
        assertEquals(0, transfer.apply(draft, InaccessibleHistoryPolicy.KEEP))
        assertEquals("Keep this original", repository.find("original")!!.title)
        val missingOutputs = JSONObject(root.toString()).apply {
            put("entries", JSONArray((1..2).map { index -> JSONObject(root.getJSONArray("entries").getJSONObject(0).toString())
                .put("id", "missing-$index").put("fileUri", "").put("fileName", "output-$index.mp4") }))
        }
        assertEquals(2, transfer.apply(transfer.stage(missingOutputs.toString()), InaccessibleHistoryPolicy.KEEP))
        val urls = transfer.stage("https://example.com/one\nhttps://example.com/one\nhttps://example.com/two")
        assertEquals(1, transfer.preview(urls).duplicates)
        assertEquals(2, transfer.apply(urls, InaccessibleHistoryPolicy.KEEP))
    }

    @Test fun `relink previews remain isolated and access is checked again before import`() {
        repository.add(entry("original").copy(fileUri = "content://absent.test/old"))
        val text = transfer.export(HistoryTransferFormat.JSON)
        repository.clear()
        val draft = transfer.stage(text)
        assertEquals(setOf("original"), transfer.preview(draft).unavailableIds)
        val uri = Uri.parse("content://library.test/relinked")
        transfer.relink(draft, "original", uri)
        assertTrue(repository.load().isEmpty())
        assertTrue(HistoryTransfer(context).preview(draft).unavailableIds.isEmpty())
        provider.denied = true
        assertEquals(setOf("original"), transfer.preview(draft).unavailableIds)
        provider.denied = false
        assertEquals(1, transfer.apply(draft, InaccessibleHistoryPolicy.KEEP))
        assertEquals(uri.toString(), repository.find("original")!!.fileUri)
        transfer.relinkExisting("original", Uri.parse("content://library.test/new"))
        assertEquals("content://library.test/new", repository.find("original")!!.fileUri)
        provider.denied = true
        assertThrows(IllegalArgumentException::class.java) { transfer.relinkExisting("original", uri) }
        assertEquals("content://library.test/new", repository.find("original")!!.fileUri)
    }

    @Test fun `file interchange uses bounded UTF8 and cancelled drafts cannot change History`() {
        repository.add(entry("file"))
        val exported = transfer.export(HistoryTransferFormat.JSON)
        val target = Uri.parse("content://fixture/export")
        val bytes = java.io.ByteArrayOutputStream()
        shadowOf(context.contentResolver).registerOutputStream(target, bytes)
        transfer.write(target, transfer.stageExport(HistoryTransferFormat.JSON, null))
        assertEquals(exported, bytes.toString(Charsets.UTF_8.name()))
        val source = Uri.parse("content://fixture/import")
        shadowOf(context.contentResolver).registerInputStream(source, exported.byteInputStream())
        val draft = transfer.read(source)
        transfer.discard(draft)
        assertThrows(InvalidHistoryDocumentException::class.java) { transfer.preview(draft) }
        assertThrows(InvalidHistoryDocumentException::class.java) { transfer.preview("../outside") }
        assertEquals(1, repository.load().size)
    }

    @Test fun `command documents preview and import templates without adding History or starting downloads`() {
        val store = CommandTemplateStore(context)
        store.put(CommandTemplate(name = "Portable", command = "--embed-metadata"))
        val text = store.export()
        val id = store.all().single().id
        store.remove(id)
        val draft = transfer.stage(text)
        assertEquals(1, transfer.preview(draft).templates!!.templates.size)
        assertTrue(store.all().isEmpty())
        assertTrue(repository.load().isEmpty())
        assertEquals(1, transfer.apply(draft, InaccessibleHistoryPolicy.KEEP))
        assertEquals(id, store.all().single().id)
        assertTrue(repository.load().isEmpty())
    }

    private fun entry(id: String) = HistoryEntry(id, 1234567890123L, "https://example.com/$id", "$id.mp4",
        "content://library.test/$id", true, "video/mp4", 123, true, title = "Title $id", author = "Author", sourceIdentity = id, sourceHost = "example.com")

    class FilesProvider : ContentProvider() {
        var denied = false
        override fun onCreate() = true
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (denied) throw SecurityException("Denied")
            val file = File(context!!.cacheDir, "history-transfer-readable").apply { writeText("fixture") }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun getType(uri: Uri) = "video/mp4"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
