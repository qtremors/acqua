package dev.qtremors.acqua.platform.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dev.qtremors.acqua.data.settings.DownloadDestination
import dev.qtremors.acqua.domain.ResolvedMedia
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class DownloadFolderPublisher(
    context: Context,
    private val access: DownloadFolderAccess = DownloadFolderAccess(context)
) {
    private val app = context.applicationContext

    suspend fun publish(
        source: File, media: ResolvedMedia, sourceUrl: String, destination: DownloadDestination,
        name: String, mime: String,
        copy: (File, OutputStream) -> Unit,
        record: (Uri, String) -> Unit
    ): Uri = try {
        publishInFolder(source, media, sourceUrl, destination, name, mime, copy, record)
    } catch (error: java.io.IOException) {
        throw DownloadFolderException()
    } catch (error: SecurityException) {
        throw DownloadFolderException()
    }

    private suspend fun publishInFolder(
        source: File, media: ResolvedMedia, sourceUrl: String, destination: DownloadDestination,
        name: String, mime: String,
        copy: (File, OutputStream) -> Unit,
        record: (Uri, String) -> Unit
    ): Uri {
        val coroutine = currentCoroutineContext()
        coroutine.ensureActive()
        var parent = access.root(Uri.parse(destination.validated().treeUri ?: throw DownloadFolderException()))
        destination.folders(media, sourceUrl).forEachIndexed { index, folder ->
            coroutine.ensureActive()
            parent = access.directory(parent, folder)
            if (destination.hidden && index == 0) access.marker(parent)
        }
        val uri = PendingDocumentRegistry.create(app) { access.create(parent, name, mime) }
        var complete = false
        try {
            app.contentResolver.openOutputStream(uri, "w")?.use { stream ->
                copy(source, object : OutputStream() {
                    override fun write(value: Int) { coroutine.ensureActive(); stream.write(value) }
                    override fun write(bytes: ByteArray, offset: Int, length: Int) {
                        coroutine.ensureActive(); stream.write(bytes, offset, length)
                    }
                    override fun flush() = stream.flush()
                })
            } ?: throw DownloadFolderException()
            coroutine.ensureActive()
            val finalName = app.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: name
            PendingDocumentRegistry.markWritten(app, uri)
            record(uri, finalName)
            complete = true
            PendingDocumentRegistry.complete(app, uri)
            return uri
        } finally {
            if (!complete && runCatching { DocumentsContract.deleteDocument(app.contentResolver, uri) }.getOrDefault(false)) {
                PendingDocumentRegistry.complete(app, uri)
            }
        }
    }
}
