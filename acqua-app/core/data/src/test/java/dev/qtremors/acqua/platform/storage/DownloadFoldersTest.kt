package dev.qtremors.acqua.platform.storage

import android.content.Context
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.content.ContentProvider
import android.content.ContentValues
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.data.history.HistoryRepository
import dev.qtremors.acqua.data.network.HttpMediaClient
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.data.settings.DownloadDestination
import dev.qtremors.acqua.data.settings.DownloadDestinationStore
import dev.qtremors.acqua.data.settings.FolderOrganization
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.downloader.DownloadOptionsCodec
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadFoldersTest {
    @get:Rule val temporary = TemporaryFolder()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = DownloadDestinationStore(context)
    private val media = ResolvedMedia("https://example.com/audio", MediaKind.AUDIO, downloadName = "Saved",
        collectionTitle = "../Collection/" + "楽".repeat(80))
    private val history = HistoryRepository(context)
    private val source by lazy { temporary.newFile("source.m4a").apply { writeText("downloaded bytes") } }
    private lateinit var provider: FolderProvider
    private lateinit var tree: Uri

    @Before fun prepare() {
        history.clear()
        context.getSharedPreferences("download_destinations_v1", 0).edit().clear().commit()
        context.getSharedPreferences("pending_document_outputs", 0).edit().clear().commit()
        provider = FolderProvider(temporary.newFolder()).apply {
            attachInfo(this@DownloadFoldersTest.context, ProviderInfo().apply {
                authority = "folders"; exported = true; grantUriPermissions = true
                readPermission = "android.permission.MANAGE_DOCUMENTS"; writePermission = readPermission
            })
        }
        ShadowContentResolver.registerProviderInternal("folders", provider)
        tree = DocumentsContract.buildTreeDocumentUri("folders", "primary:chosen")
        context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    @Test fun `independent choices persist across stores and accepted snapshots stay frozen`() {
        store.put(MediaKind.AUDIO, DownloadDestination(tree.toString(), FolderOrganization.SOURCE_COLLECTION, true))
        val accepted = AppSettingsRepository(context).downloadSettings()
        val encoded = DownloadOptionsCodec.encodeStorage(accepted)
        store.put(MediaKind.AUDIO, DownloadDestination())
        assertEquals(tree.toString(), DownloadOptionsCodec.decodeStorage(encoded)!!.destinations.audio.treeUri)
        assertEquals(null, accepted.destinations.images.treeUri)
        assertEquals(null, DownloadDestinationStore(context).get().audio.treeUri)
        assertEquals(null, DownloadOptionsCodec.decodeStorage("""{"folder":"Acqua","categorize":false,"filename":"{title}","audioFilename":"{title}"}""")!!.destinations.audio.treeUri)
    }

    @Test fun `organization names are bounded and hidden marker stays in its dedicated subfolder`() = runBlocking {
        val destination = DownloadDestination(tree.toString(), FolderOrganization.SOURCE_COLLECTION, true)
        val folders = destination.folders(media, "https://example.com/private?token=secret")
        assertEquals(listOf("Acqua hidden", "example.com"), folders.take(2))
        assertTrue(folders.last().toByteArray().size <= 80)
        assertFalse(folders.last().contains('/'))
        store.put(MediaKind.AUDIO, destination)
        val uri = save()
        assertEquals("downloaded bytes", context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
        assertTrue(File(provider.directory, "Acqua hidden/.nomedia").isFile)
        assertFalse(File(provider.directory, ".nomedia").exists())
        assertEquals(uri.toString(), history.load().single().fileUri)
        assertTrue(PendingDocumentRegistry.snapshot(context).isEmpty())
    }

    @Test fun `removable provider IDs work without filesystem assumptions`() = runBlocking {
        val removable = DocumentsContract.buildTreeDocumentUri("folders", "ABCD-1234:chosen")
        context.contentResolver.takePersistableUriPermission(removable, 3)
        store.put(MediaKind.AUDIO, DownloadDestination(removable.toString()))
        assertEquals("Saved.m4a", historyName(save()))
    }

    @Test fun `direct image transfers publish the final name to the image folder and clear private staging`() = runBlocking {
        val server = okhttp3.mockwebserver.MockWebServer()
        server.start()
        try {
            val bytes = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j5P0AAAAASUVORK5CYII=")
            server.enqueue(okhttp3.mockwebserver.MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(bytes)))
            store.put(MediaKind.IMAGE, DownloadDestination(tree.toString(), FolderOrganization.SOURCE))
            val image = ResolvedMedia(server.url("/photo.png").toString(), MediaKind.IMAGE, mimeType = "image/png", fileExtension = "png")
            val storage = MediaStorage(context, history, AppSettingsRepository(context), HttpMediaClient())
            val uri = storage.save(image, 0, "https://example.com/post", embedMetadata = false, finalDownloadName = { "Final picture" })
            assertEquals("Final picture.png", historyName(uri))
            org.junit.Assert.assertArrayEquals(bytes, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
            assertTrue(File(provider.directory, "example.com/Final picture.png").isFile)
            assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("folder-download-") })
        } finally { server.shutdown() }
    }

    @Test fun `revoked grants and read only roots fail without publishing or falling back`() {
        store.put(MediaKind.AUDIO, DownloadDestination(tree.toString()))
        context.contentResolver.releasePersistableUriPermission(tree, 3)
        assertThrows(DownloadFolderException::class.java) { runBlocking { save() } }
        context.contentResolver.takePersistableUriPermission(tree, 3)
        provider.writable = false
        assertThrows(DownloadFolderException::class.java) { runBlocking { save() } }
        assertTrue(provider.directory.listFiles()!!.isEmpty())
        assertTrue(history.load().isEmpty())
    }

    @Test fun `copy failure cancellation and history failure remove only newly created documents`() {
        val existing = File(provider.directory, "Saved.m4a").apply { writeText("previous") }
        store.put(MediaKind.AUDIO, DownloadDestination(tree.toString()))
        for (failure in listOf(IOException("storage full"), CancellationException("cancelled"))) {
            val storage = MediaStorage(context, history, AppSettingsRepository(context), HttpMediaClient()) { _, output ->
                output.write(7); throw failure
            }
            assertThrows(failure.javaClass) { runBlocking { save(storage) } }
            assertEquals(listOf(existing.name), provider.directory.list()!!.toList())
        }
        val failing = MediaStorage(context, { throw android.database.sqlite.SQLiteException("History failed") }, AppSettingsRepository(context), HttpMediaClient())
        assertThrows(android.database.sqlite.SQLiteException::class.java) { runBlocking { save(failing) } }
        assertEquals("previous", existing.readText())
        assertTrue(history.load().isEmpty())
    }

    @Test fun `parallel collisions preserve original bytes and distinct history names`() = runBlocking {
        val existing = File(provider.directory, "Saved.m4a").apply { writeText("previous") }
        store.put(MediaKind.AUDIO, DownloadDestination(tree.toString()))
        val saved = (1..4).map { async(Dispatchers.IO) { save() } }.awaitAll()
        assertEquals(4, saved.distinct().size)
        assertEquals(4, history.load().map { it.fileName }.distinct().size)
        assertEquals("previous", existing.readText())
        saved.forEach { assertEquals("downloaded bytes", context.contentResolver.openInputStream(it)!!.bufferedReader().use { reader -> reader.readText() }) }
    }

    @Test fun `restart cleanup removes abandoned outputs and retains failures without touching new work`() {
        val access = DownloadFolderAccess(context)
        val parent = access.root(tree)
        val abandoned = access.create(parent, "partial.m4a", "audio/mp4")
        PendingDocumentRegistry.track(context, abandoned)
        val written = access.create(parent, "finished.m4a", "audio/mp4")
        PendingDocumentRegistry.track(context, written)
        PendingDocumentRegistry.markWritten(context, written)
        val snapshot = PendingDocumentRegistry.snapshot(context)
        val live = access.create(parent, "live.m4a", "audio/mp4")
        PendingDocumentRegistry.track(context, live)
        provider.rejectDelete = true
        PendingDocumentRegistry.cleanup(context, snapshot)
        assertEquals(2, PendingDocumentRegistry.snapshot(context).size)
        provider.rejectDelete = false
        PendingDocumentRegistry.cleanup(context, snapshot)
        assertEquals(setOf(live.toString()), PendingDocumentRegistry.snapshot(context).keys)
        assertEquals(setOf("live.m4a", "finished.m4a"), provider.directory.list()!!.toSet())
        val previousGeneration = PendingDocumentRegistry.snapshot(context)
        PendingDocumentRegistry.track(context, live)
        PendingDocumentRegistry.cleanup(context, previousGeneration)
        assertTrue(File(provider.directory, "live.m4a").isFile)
    }

    private suspend fun save(storage: MediaStorage = MediaStorage(context, history, AppSettingsRepository(context), HttpMediaClient())) =
        storage.saveProcessedFile(source, media, DownloadContentType.AUDIO, "https://example.com/source")
    private fun historyName(uri: Uri) = history.load().single { it.fileUri == uri.toString() }.fileName
}

internal class FolderProvider(val directory: File) : ContentProvider() {
    var writable = true
    var rejectDelete = false
    private val documents = linkedMapOf<String, File>()
    private val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_FLAGS)
    override fun onCreate() = true
    private fun file(id: String) = if (id.endsWith(":chosen")) directory else documents[id] ?: throw java.io.FileNotFoundException()
    private fun row(id: String, target: File) = arrayOf<Any>(id, target.name,
        if (target.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else "audio/mp4",
        if (target.isDirectory && writable) DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE else DocumentsContract.Document.FLAG_SUPPORTS_WRITE)
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
        MatrixCursor(projection ?: columns).apply {
            fun add(id: String, target: File) { val values = row(id, target); addRow(columnNames.map { values[columns.indexOf(it)] }.toTypedArray()) }
            val id = DocumentsContract.getDocumentId(uri)
            if (uri.lastPathSegment != "children") add(id, file(id))
            else file(id).listFiles()!!.forEach { child ->
                val id = documents.entries.firstOrNull { it.value == child }?.key ?: UUID.randomUUID().toString().also { documents[it] = child }
                add(id, child)
            }
        }
    private fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        if (!writable) throw java.io.FileNotFoundException("Read only")
        val target = File(file(parentDocumentId), displayName)
        check(if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) target.mkdir() else target.createNewFile())
        return UUID.randomUUID().toString().also { documents[it] = target }
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file(DocumentsContract.getDocumentId(uri)), ParcelFileDescriptor.parseMode(mode))
    private fun deleteDocument(documentId: String) {
        if (rejectDelete) throw java.io.FileNotFoundException("Unavailable")
        check(file(documentId).delete()); documents.remove(documentId)
    }
    @Suppress("DEPRECATION")
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val uri = extras!!.getParcelable<Uri>("uri")!!
        return Bundle().apply {
            when (method) {
                "android:createDocument" -> {
                    val id = createDocument(DocumentsContract.getDocumentId(uri), extras.getString("mime_type")!!, extras.getString("_display_name")!!)
                    val created = DocumentsContract.buildDocumentUriUsingTree(uri, id)
                    if (file(id).isFile) {
                        shadowOf(context!!.contentResolver).registerOutputStreamSupplier(created) { file(id).outputStream() }
                        shadowOf(context!!.contentResolver).registerInputStreamSupplier(created) { file(id).inputStream() }
                    }
                    putParcelable("uri", created)
                }
                "android:deleteDocument" -> deleteDocument(DocumentsContract.getDocumentId(uri))
                else -> error("Unexpected provider method $method")
            }
        }
    }
    override fun getType(uri: Uri) = if (file(DocumentsContract.getDocumentId(uri)).isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else "audio/mp4"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Unexpected insert")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
}
