package dev.qtremors.acqua.data.history

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

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

    fun load(): List<HistoryEntry> = DatabaseHelper(appContext).use { helper ->
        helper.readableDatabase.query(
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
    }

    fun add(entry: HistoryEntry) = DatabaseHelper(appContext).use { helper ->
        val database = helper.writableDatabase
        val id = if (entry.type == HistoryEntryType.LINK) {
            database.query(
                TABLE_HISTORY,
                arrayOf("id"),
                "url = ? AND is_downloaded = 0",
                arrayOf(entry.url),
                null,
                null,
                null
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else entry.id }
        } else {
            entry.id
        }

        database.insertWithOnConflict(
            TABLE_HISTORY,
            null,
            ContentValues().apply {
                put("id", id)
                put("timestamp", entry.timestamp)
                put("url", entry.url)
                put("file_name", entry.fileName)
                put("file_uri", entry.fileUri)
                put("is_video", if (entry.isVideo) 1 else 0)
                put("mime_type", entry.mimeType)
                put("size_bytes", entry.sizeBytes)
                put("is_downloaded", if (entry.isDownloaded) 1 else 0)
                put("thumbnail_url", entry.thumbnailUrl)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun delete(id: String) = DatabaseHelper(appContext).use { helper ->
        helper.writableDatabase.delete(TABLE_HISTORY, "id = ?", arrayOf(id))
    }

    fun clear() = DatabaseHelper(appContext).use { helper ->
        helper.writableDatabase.delete(TABLE_HISTORY, null, null)
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
        const val TABLE_HISTORY = "history"
        const val DATABASE_NAME = "acqua_history.db"
        const val DATABASE_VERSION = 2
    }
}
