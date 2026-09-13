package dev.qtremors.acqua.data.history

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import androidx.core.net.toUri
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class HistoryFileStatus { AVAILABLE, MISSING, UNAVAILABLE }

enum class HistoryEntryType { DOWNLOAD, LINK }

enum class HistoryCategory { ALL, MEDIA, PHOTOS, VIDEOS, AUDIO, LINKS, UNAVAILABLE }

enum class HistoryOrder { NEWEST, OLDEST, LARGEST }

data class HistoryPageRequest(
    val category: HistoryCategory = HistoryCategory.ALL,
    val order: HistoryOrder = HistoryOrder.NEWEST,
    val terms: List<String> = emptyList(),
    val limit: Int = 100,
    val offset: Int = 0
)

data class HistoryPage(val entries: List<HistoryEntry>, val hasMore: Boolean)

data class HistoryDatabaseSummary(
    val downloads: Int,
    val links: Int,
    val photos: Int,
    val videos: Int,
    val audio: Int,
    val unavailable: Int,
    val storedBytes: Long
)

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
    private val databaseHelper = DatabaseHelper.instance(appContext)

    val changes = revision.asStateFlow()

    fun load(): List<HistoryEntry> = readEntries(databaseHelper.readableDatabase)

    fun loadPage(request: HistoryPageRequest): HistoryPage {
        val safeLimit = request.limit.coerceIn(1, MAX_PAGE_SIZE)
        val selection = mutableListOf<String>()
        val arguments = mutableListOf<String>()
        selection += when (request.category) {
            HistoryCategory.ALL -> "1"
            HistoryCategory.MEDIA -> "is_downloaded = 1"
            HistoryCategory.PHOTOS -> "is_downloaded = 1 AND is_video = 0 AND mime_type NOT LIKE 'audio/%'"
            HistoryCategory.VIDEOS -> "is_downloaded = 1 AND is_video = 1 AND mime_type NOT LIKE 'audio/%'"
            HistoryCategory.AUDIO -> "is_downloaded = 1 AND mime_type LIKE 'audio/%'"
            HistoryCategory.LINKS -> "is_downloaded = 0"
            HistoryCategory.UNAVAILABLE -> "is_downloaded = 1 AND file_status IN ('MISSING', 'UNAVAILABLE')"
        }
        request.terms.map(String::trim).filter(String::isNotEmpty).forEach { term ->
            selection += "(file_name LIKE ? ESCAPE '\\' OR url LIKE ? ESCAPE '\\' OR mime_type LIKE ? ESCAPE '\\')"
            val pattern = "%${escapeLike(term)}%"
            repeat(3) { arguments += pattern }
        }
        val orderBy = when (request.order) {
            HistoryOrder.NEWEST -> "timestamp DESC"
            HistoryOrder.OLDEST -> "timestamp ASC"
            HistoryOrder.LARGEST -> "size_bytes DESC, timestamp DESC"
        }
        val entries = databaseHelper.readableDatabase.query(
            TABLE_HISTORY,
            null,
            selection.joinToString(" AND "),
            arguments.toTypedArray(),
            null,
            null,
            orderBy,
            "${safeLimit + 1} OFFSET ${request.offset.coerceAtLeast(0)}"
        ).use(::readCursor)
        return HistoryPage(entries.take(safeLimit), entries.size > safeLimit)
    }

    fun loadUnknownFileStatusEntries(limit: Int = STATUS_REFRESH_PAGE_SIZE): List<HistoryEntry> =
        databaseHelper.readableDatabase.query(
            TABLE_HISTORY,
            null,
            "is_downloaded = 1 AND file_status IS NULL",
            null,
            null,
            null,
            "timestamp DESC",
            limit.coerceIn(1, MAX_PAGE_SIZE).toString()
        ).use(::readCursor)

    fun hasUnknownFileStatuses(): Boolean = databaseHelper.readableDatabase.rawQuery(
        "SELECT 1 FROM $TABLE_HISTORY WHERE is_downloaded = 1 AND file_status IS NULL LIMIT 1",
        null
    ).use { it.moveToFirst() }

    fun updateFileStatuses(statuses: Map<String, HistoryFileStatus>) {
        if (statuses.isEmpty()) return
        databaseHelper.writableDatabase.transaction {
            statuses.forEach { (id, status) ->
                val values = ContentValues().apply { put("file_status", status.name) }
                update(TABLE_HISTORY, values, "id = ?", arrayOf(id))
            }
        }
    }

    fun summary(): HistoryDatabaseSummary = databaseHelper.readableDatabase.rawQuery(
        """
        SELECT
            SUM(CASE WHEN is_downloaded = 1 THEN 1 ELSE 0 END),
            SUM(CASE WHEN is_downloaded = 0 THEN 1 ELSE 0 END),
            SUM(CASE WHEN is_downloaded = 1 AND is_video = 0 AND mime_type NOT LIKE 'audio/%' THEN 1 ELSE 0 END),
            SUM(CASE WHEN is_downloaded = 1 AND is_video = 1 AND mime_type NOT LIKE 'audio/%' THEN 1 ELSE 0 END),
            SUM(CASE WHEN is_downloaded = 1 AND mime_type LIKE 'audio/%' THEN 1 ELSE 0 END),
            SUM(CASE WHEN is_downloaded = 1 AND file_status IN ('MISSING', 'UNAVAILABLE') THEN 1 ELSE 0 END),
            SUM(CASE WHEN is_downloaded = 1 AND (file_status IS NULL OR file_status = 'AVAILABLE') THEN MAX(size_bytes, 0) ELSE 0 END)
        FROM $TABLE_HISTORY
        """.trimIndent(),
        null
    ).use { cursor ->
        cursor.moveToFirst()
        HistoryDatabaseSummary(
            downloads = cursor.getInt(0),
            links = cursor.getInt(1),
            photos = cursor.getInt(2),
            videos = cursor.getInt(3),
            audio = cursor.getInt(4),
            unavailable = cursor.getInt(5),
            storedBytes = cursor.getLong(6)
        )
    }

    private fun readEntries(database: SQLiteDatabase): List<HistoryEntry> =
        database.query(
            TABLE_HISTORY,
            null,
            null,
            null,
            null,
            null,
            "timestamp DESC"
        ).use(::readCursor)

    private fun readCursor(cursor: android.database.Cursor): List<HistoryEntry> = buildList {
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

    fun add(entry: HistoryEntry): Long {
        val database = databaseHelper.writableDatabase
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
        return rowId
    }

    fun delete(id: String) { remove(setOf(id)) }

    fun clear() { remove(null) }

    // A null selection means all records. Files are never deleted here.
    fun remove(ids: Set<String>?): List<HistoryEntry> {
        val database = databaseHelper.writableDatabase
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
        return removed
    }

    fun restore(entries: List<HistoryEntry>) {
        databaseHelper.writableDatabase.transaction {
            val current = readEntries(this)
            val existingIds = current.mapTo(mutableSetOf(), HistoryEntry::id)
            val existingLinks = current.filterNot(HistoryEntry::isDownloaded).mapTo(mutableSetOf(), HistoryEntry::url)
            entries.forEach { entry ->
                if (existingIds.add(entry.id) && (entry.isDownloaded || existingLinks.add(entry.url))) {
                    check(insertWithOnConflict(TABLE_HISTORY, null, values(entry), SQLiteDatabase.CONFLICT_IGNORE) != -1L)
                }
            }
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
        // Remote thumbnail URLs are deliberately not retained. History previews use local files only.
        putNull("thumbnail_url")
        putNull("file_status")
    }

    private class DatabaseHelper private constructor(context: Context) :
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
                    thumbnail_url TEXT,
                    file_status TEXT
                )
                """.trimIndent()
            )
            createIndexes(database)
        }

        override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                runCatching { database.execSQL("ALTER TABLE $TABLE_HISTORY ADD COLUMN thumbnail_url TEXT") }
            }
            if (oldVersion < 3) {
                database.execSQL("UPDATE $TABLE_HISTORY SET thumbnail_url = NULL")
            }
            if (oldVersion < 4) {
                database.execSQL("ALTER TABLE $TABLE_HISTORY ADD COLUMN file_status TEXT")
                createIndexes(database)
            }
        }

        private fun createIndexes(database: SQLiteDatabase) {
            database.execSQL("CREATE INDEX IF NOT EXISTS history_timestamp_idx ON $TABLE_HISTORY(timestamp DESC)")
            database.execSQL("CREATE INDEX IF NOT EXISTS history_downloaded_timestamp_idx ON $TABLE_HISTORY(is_downloaded, timestamp DESC)")
            database.execSQL("CREATE INDEX IF NOT EXISTS history_mime_timestamp_idx ON $TABLE_HISTORY(mime_type, timestamp DESC)")
            database.execSQL("CREATE INDEX IF NOT EXISTS history_url_idx ON $TABLE_HISTORY(url)")
            database.execSQL("CREATE INDEX IF NOT EXISTS history_file_name_idx ON $TABLE_HISTORY(file_name)")
            database.execSQL("CREATE INDEX IF NOT EXISTS history_file_status_timestamp_idx ON $TABLE_HISTORY(file_status, timestamp DESC)")
        }

        companion object {
            @Volatile
            private var shared: DatabaseHelper? = null

            fun instance(context: Context): DatabaseHelper = shared ?: synchronized(this) {
                shared ?: DatabaseHelper(context.applicationContext).also { shared = it }
            }
        }
    }

    private companion object {
        val revision = MutableStateFlow(0L)
        const val TABLE_HISTORY = "history"
        const val DATABASE_NAME = "acqua_history.db"
        const val DATABASE_VERSION = 4
        const val STATUS_REFRESH_PAGE_SIZE = 50
        const val MAX_PAGE_SIZE = 250

        fun escapeLike(value: String): String = value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }
}
