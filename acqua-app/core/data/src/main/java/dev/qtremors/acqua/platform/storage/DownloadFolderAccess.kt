package dev.qtremors.acqua.platform.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dev.qtremors.acqua.downloader.FilenameFormatter
import java.io.IOException

class DownloadFolderException : IOException("The download folder is unavailable. Choose a writable folder again in Settings.")

class DownloadFolderAccess(
    context: Context,
    private val hasGrant: (Uri) -> Boolean = { uri -> context.contentResolver.persistedUriPermissions.any {
        it.uri == uri && it.isReadPermission && it.isWritePermission
    } }
) {
    private val resolver = context.applicationContext.contentResolver

    fun root(tree: Uri): Uri {
        if (tree.scheme != "content" || !DocumentsContract.isTreeUri(tree) || !hasGrant(tree)) throw DownloadFolderException()
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        requireDirectory(root)
        return root
    }

    fun label(tree: Uri): String = resolver.query(root(tree), arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
        null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: throw DownloadFolderException()

    fun directory(parent: Uri, name: String): Uri = synchronized(CREATION_LOCK) {
        val existing = children(parent).firstOrNull { it.name == name }
        if (existing != null) {
            requireDirectory(existing.uri)
            existing.uri
        } else DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
            ?.also(::requireDirectory) ?: throw DownloadFolderException()
    }

    fun create(parent: Uri, name: String, mime: String): Uri = synchronized(CREATION_LOCK) {
        requireDirectory(parent)
        val existing = children(parent)
        val names = existing.map { it.name }.toSet()
        val candidate = if (name !in names) name else (1..9999).asSequence()
            .map { FilenameFormatter.withCollisionSuffix(name, it) }.firstOrNull { it !in names }
            ?: throw DownloadFolderException()
        val created = DocumentsContract.createDocument(resolver, parent, mime, candidate) ?: throw DownloadFolderException()
        check(existing.none { it.uri == created }) { "The folder provider did not create a new document." }
        created
    }

    fun marker(parent: Uri) = synchronized(CREATION_LOCK) {
        if (children(parent).none { it.name == ".nomedia" }) {
            val marker = create(parent, ".nomedia", "application/octet-stream")
            val exactName = resolver.query(marker, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null)?.use { it.moveToFirst() && it.getString(0) == ".nomedia" } ?: false
            if (!exactName) {
                runCatching { DocumentsContract.deleteDocument(resolver, marker) }
                throw DownloadFolderException()
            }
            resolver.openOutputStream(marker, "w")?.use { it.flush() } ?: throw DownloadFolderException()
        }
    }

    private fun requireDirectory(uri: Uri) {
        val valid = resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { cursor ->
            cursor.moveToFirst() && cursor.getString(0) == DocumentsContract.Document.MIME_TYPE_DIR &&
                cursor.getInt(1) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0
        } ?: false
        if (!valid) throw DownloadFolderException()
    }

    private fun children(parent: Uri): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, DocumentsContract.getDocumentId(parent))
        return resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    if (size >= 10_000) throw DownloadFolderException()
                    add(Child(DocumentsContract.buildDocumentUriUsingTree(parent, cursor.getString(0)), cursor.getString(1)))
                }
            }
        } ?: throw DownloadFolderException()
    }

    private data class Child(val uri: Uri, val name: String)
    private companion object { val CREATION_LOCK = Any() }
}
