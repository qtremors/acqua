package dev.qtremors.acqua.data.history

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.net.toUri
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class HistoryFileStatus { AVAILABLE, MISSING, UNAVAILABLE }

enum class HistoryEntryType { DOWNLOAD, LINK }

data class HistoryEntry(
    val id: String,
    val timestamp: Long,
    val url: String,
    val fileName: String,
    val fileUri: String,
    val isVideo: Boolean,
    val mimeType: String,
    val sizeBytes: Long,
    val isDownloaded: Boolean,
    val thumbnailUrl: String? = null
) {
    val type: HistoryEntryType
        get() = if (isDownloaded) HistoryEntryType.DOWNLOAD else HistoryEntryType.LINK

    val isAudio: Boolean
        get() = isDownloaded && mimeType.startsWith("audio/")
}

class HistoryRepository(context: Context) {
    private val appContext = context.applicationContext

    val changes = revision.asStateFlow()

    fun load(): List<HistoryEntry> = DatabaseHelper(appContext).use { readEntries(it.readableDatabase) }

    private fun readEntries(database: SQLiteDatabase): List<HistoryEntry> =
        database.query(
            TABLE_HISTORY,
            null,
            null,
            null,
            null,
            null,
            "timestamp DESC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        HistoryEntry(
                            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                            url = cursor.getString(cursor.getColumnIndexOrThrow("url")),
                            fileName = cursor.getString(cursor.getColumnIndexOrThrow("file_name")),
                            fileUri = cursor.getString(cursor.getColumnIndexOrThrow("file_uri")),
                            isVideo = cursor.getInt(cursor.getColumnIndexOrThrow("is_video")) == 1,
                            mimeType = cursor.getString(cursor.getColumnIndexOrThrow("mime_type")),
                            sizeBytes = cursor.getLong(cursor.getColumnIndexOrThrow("size_bytes")),
                            isDownloaded = cursor.getInt(cursor.getColumnIndexOrThrow("is_downloaded")) == 1,
                            thumbnailUrl = cursor.getString(cursor.getColumnIndexOrThrow("thumbnail_url"))
                        )
                    )
                }
            }
        }

    fun add(entry: HistoryEntry) = DatabaseHelper(appContext).use { helper ->
        val database = helper.writableDatabase
        database.beginTransaction()
        val rowId: Long
        try {
            val id = if (entry.type == HistoryEntryType.LINK) {
                database.query(
                    TABLE_HISTORY, arrayOf("id"), "url = ? AND is_downloaded = 0",
                    arrayOf(entry.url), null, null, null
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else entry.id }
            } else entry.id
            rowId = database.insertWithOnConflict(
                TABLE_HISTORY, null, values(entry.copy(id = id)), SQLiteDatabase.CONFLICT_REPLACE
            )
            check(rowId != -1L) { "Could not save history" }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        revision.update { it + 1 }
        rowId
    }

    fun delete(id: String) { remove(setOf(id)) }

    fun clear() { remove(null) }

    // A null selection means all records. Files are never deleted here.
    fun remove(ids: Set<String>?): List<HistoryEntry> = DatabaseHelper(appContext).use { helper ->
        val database = helper.writableDatabase
        database.beginTransaction()
        val removed: List<HistoryEntry>
        try {
            removed = readEntries(database).filter { ids == null || it.id in ids }
            removed.forEach { database.delete(TABLE_HISTORY, "id = ?", arrayOf(it.id)) }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        revision.update { it + 1 }
        removed
    }

    fun restore(entries: List<HistoryEntry>) = DatabaseHelper(appContext).use { helper ->
        val database = helper.writableDatabase
        database.beginTransaction()
        try {
            val current = readEntries(database)
            val existingIds = current.mapTo(mutableSetOf(), HistoryEntry::id)
            val existingLinks = current.filterNot(HistoryEntry::isDownloaded).mapTo(mutableSetOf(), HistoryEntry::url)
            entries.forEach { entry ->
                if (existingIds.add(entry.id) && (entry.isDownloaded || existingLinks.add(entry.url))) {
                    check(database.insertWithOnConflict(TABLE_HISTORY, null, values(entry), SQLiteDatabase.CONFLICT_IGNORE) != -1L)
                }
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        revision.update { it + 1 }
    }

    fun fileExists(entry: HistoryEntry): Boolean = fileStatus(entry) == HistoryFileStatus.AVAILABLE

    fun fileStatus(entry: HistoryEntry): HistoryFileStatus {
        if (!entry.isDownloaded || entry.fileUri.isBlank()) return HistoryFileStatus.UNAVAILABLE
        return try {
            val uri = entry.fileUri.toUri()
            when (uri.scheme) {
                "file" -> if (File(uri.path.orEmpty()).isFile) HistoryFileStatus.AVAILABLE else HistoryFileStatus.MISSING
                // Providers may be offline or deny access. An open failure alone is not proof of deletion.
                "content" -> appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                    HistoryFileStatus.AVAILABLE
                } ?: HistoryFileStatus.UNAVAILABLE
                else -> HistoryFileStatus.UNAVAILABLE
            }
        } catch (_: Exception) {
            HistoryFileStatus.UNAVAILABLE
        }
    }

    private fun values(entry: HistoryEntry) = ContentValues().apply {
        put("id", entry.id)
        put("timestamp", entry.timestamp)
        put("url", entry.url)
        put("file_name", entry.fileName)
        put("file_uri", entry.fileUri)
        put("is_video", if (entry.isVideo) 1 else 0)
        put("mime_type", entry.mimeType)
        put("size_bytes", entry.sizeBytes)
        put("is_downloaded", if (entry.isDownloaded) 1 else 0)
        put("thumbnail_url", entry.thumbnailUrl)
    }

    private class DatabaseHelper(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

        override fun onCreate(database: SQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE $TABLE_HISTORY (
                    id TEXT PRIMARY KEY,
                    timestamp INTEGER,
                    url TEXT,
                    file_name TEXT,
                    file_uri TEXT,
                    is_video INTEGER,
                    mime_type TEXT,
                    size_bytes INTEGER,
                    is_downloaded INTEGER,
                    thumbnail_url TEXT
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                runCatching { database.execSQL("ALTER TABLE $TABLE_HISTORY ADD COLUMN thumbnail_url TEXT") }
            }
        }
    }

    private companion object {
        val revision = MutableStateFlow(0L)
        const val TABLE_HISTORY = "history"
        const val DATABASE_NAME = "acqua_history.db"
        const val DATABASE_VERSION = 2
    }
}
