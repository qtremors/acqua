package dev.qtremors.acqua.platform.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/** Only URIs returned by creation of a new output are registered here. */
object PendingDocumentRegistry {
    fun snapshot(context: Context): Map<String, String> = synchronized(this) {
        val prefs = preferences(context)
        prefs.getStringSet("uris", emptySet()).orEmpty().associateWith { prefs.getString("token.$it", "legacy")!! }
    }

    fun track(context: Context, uri: Uri) = synchronized(this) {
        val prefs = preferences(context)
        check(prefs.edit().putStringSet("uris", snapshot(context).keys + uri.toString())
            .putString("token.$uri", java.util.UUID.randomUUID().toString())
            .putStringSet("written", prefs.getStringSet("written", emptySet()).orEmpty() - uri.toString()).commit())
    }

    fun create(context: Context, create: () -> Uri): Uri = synchronized(this) {
        val uri = create()
        try { track(context, uri); uri } catch (error: RuntimeException) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            throw error
        }
    }

    fun complete(context: Context, uri: Uri) = synchronized(this) {
        val prefs = preferences(context)
        check(prefs.edit().putStringSet("uris", snapshot(context).keys - uri.toString()).remove("token.$uri")
            .putStringSet("written", prefs.getStringSet("written", emptySet()).orEmpty() - uri.toString()).commit())
    }

    fun markWritten(context: Context, uri: Uri) = synchronized(this) {
        val prefs = preferences(context)
        check(prefs.edit().putStringSet("written", prefs.getStringSet("written", emptySet()).orEmpty() + uri.toString()).commit())
    }

    fun cleanup(context: Context, abandoned: Map<String, String>) {
        abandoned.forEach { (value, generation) ->
            synchronized(this) {
                if (snapshot(context)[value] != generation) return@synchronized
                val uri = Uri.parse(value)
                val written = value in preferences(context).getStringSet("written", emptySet()).orEmpty()
                if (written || runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }.getOrDefault(false)) {
                    complete(context, uri)
                }
            }
        }
    }

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences("pending_document_outputs", Context.MODE_PRIVATE)
}
