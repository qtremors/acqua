package dev.qtremors.acqua.platform.storage

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.edit
import androidx.core.net.toUri

/**
 * Records app-owned pending MediaStore rows before bytes are written.
 *
 * MediaStore normally hides these rows while IS_PENDING is set, but it does not
 * remove them when Android kills the process. A synchronous registry checkpoint
 * lets the next process delete only rows that Acqua created and never finalized.
 */
object PendingMediaStoreRegistry {
    fun track(context: Context, uri: Uri) = synchronized(this) {
        val preferences = preferences(context)
        preferences.edit(commit = true) {
            putStringSet(KEY_URIS, preferences.registeredUris() + uri.toString())
        }
    }

    fun complete(context: Context, uri: Uri) = synchronized(this) {
        val preferences = preferences(context)
        preferences.edit(commit = true) {
            putStringSet(KEY_URIS, preferences.registeredUris() - uri.toString())
        }
    }

    fun cleanup(context: Context) = synchronized(this) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@synchronized
        val appContext = context.applicationContext
        val preferences = preferences(appContext)
        val remaining = preferences.registeredUris().toMutableSet()
        remaining.toList().forEach { encodedUri ->
            val cleaned = runCatching {
                val uri = encodedUri.toUri()
                if (isPending(appContext, uri)) {
                    appContext.contentResolver.delete(uri, null, null)
                }
            }.isSuccess
            if (cleaned) remaining -= encodedUri
        }
        preferences.edit(commit = true) {
            if (remaining.isEmpty()) remove(KEY_URIS) else putStringSet(KEY_URIS, remaining)
        }
    }

    private fun isPending(context: Context, uri: Uri): Boolean =
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.IS_PENDING),
            null,
            null,
            null
        )?.use { cursor ->
            cursor.moveToFirst() &&
                cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_PENDING)) != 0
        } ?: false

    private fun android.content.SharedPreferences.registeredUris(): Set<String> =
        getStringSet(KEY_URIS, emptySet()).orEmpty().toSet()

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences(
        STORE_NAME,
        Context.MODE_PRIVATE
    )

    private const val STORE_NAME = "pending_media_store_rows"
    private const val KEY_URIS = "uris"
}
