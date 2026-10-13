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
    val offset: Int = 0,
    val sourceHost: String? = null
)

data class HistoryPage(val entries: List<HistoryEntry>, val hasMore: Boolean)

data class HistoryFileStatusCursor(val timestamp: Long, val id: String)

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
    val thumbnailUrl: String? = null,
    val title: String? = null,
    val author: String? = null,
    val sourceIdentity: String? = null,
    val sourceHost: String? = null
) {
    val type: HistoryEntryType
        get() = if (isDownloaded) HistoryEntryType.DOWNLOAD else HistoryEntryType.LINK

    val isAudio: Boolean
        get() = isDownloaded && mimeType.startsWith("audio/")

    val isCaption: Boolean
        get() = isDownloaded && (mimeType.startsWith("text/") || mimeType == "application/x-subrip")
}

fun interface HistoryWriter {
    fun add(entry: HistoryEntry): Long
}

class HistoryRepository(context: Context) : HistoryWriter {
    private val appContext = context.applicationContext
    private val databaseHelper = DatabaseHelper.instance(appContext)

    val changes = revision.asStateFlow()

    fun load(): List<HistoryEntry> = readEntries(databaseHelper.readableDatabase)

    fun find(id: String): HistoryEntry? = databaseHelper.readableDatabase.query(
        TABLE_HISTORY, null, "id = ?", arrayOf(id), null, null, null, "1"
    ).use(::readCursor).firstOrNull()

    fun fileDeletionGateway(): HistoryFileDeletionGateway = HistoryFileDeleter(appContext)
    fun transfer(): HistoryTransfer = HistoryTransfer(appContext, this)

    fun exportEntries(ids: Set<String>?): List<HistoryEntry> = databaseHelper.readableDatabase.transaction(exclusive = false) {
        buildList {
            var offset = 0
            do {
                val page = loadPage(HistoryPageRequest(limit = MAX_PAGE_SIZE, offset = offset))
                addAll(page.entries.filter { ids == null || it.id in ids })
                if (size > HistoryTransfer.MAX_RECORDS) throw InvalidHistoryDocumentException()
                offset += page.entries.size
            } while (page.hasMore)
        }
    }

    fun loadPage(request: HistoryPageRequest): HistoryPage {
        val safeLimit = request.limit.coerceIn(1, MAX_PAGE_SIZE)
        val selection = mutableListOf<String>()
        val arguments = mutableListOf<String>()
        selection += when (request.category) {
            HistoryCategory.ALL -> "1"
            HistoryCategory.MEDIA -> "is_downloaded = 1"
            HistoryCategory.PHOTOS -> "is_downloaded = 1 AND is_video = 0 AND (mime_type LIKE 'image/%' OR mime_type = '')"
            HistoryCategory.VIDEOS -> "is_downloaded = 1 AND is_video = 1 AND mime_type NOT LIKE 'audio/%'"
            HistoryCategory.AUDIO -> "is_downloaded = 1 AND mime_type LIKE 'audio/%'"
            HistoryCategory.LINKS -> "is_downloaded = 0"
            HistoryCategory.UNAVAILABLE -> "is_downloaded = 1 AND file_status IN ('MISSING', 'UNAVAILABLE')"
        }
        request.sourceHost?.let {
            selection += "source_host = ?"
            arguments += it
        }
        request.terms.map(String::trim).filter(String::isNotEmpty).forEach { term ->
            selection += "(file_name LIKE ? ESCAPE '\\' OR url LIKE ? ESCAPE '\\' OR mime_type LIKE ? ESCAPE '\\' OR title LIKE ? ESCAPE '\\' OR author LIKE ? ESCAPE '\\' OR source_identity LIKE ? ESCAPE '\\')"
            val pattern = "%${escapeLike(term)}%"
            repeat(6) { arguments += pattern }
        }
        val orderBy = when (request.order) {
            HistoryOrder.NEWEST -> "timestamp DESC, id DESC"
            HistoryOrder.OLDEST -> "timestamp ASC, id ASC"
            HistoryOrder.LARGEST -> "size_bytes DESC, timestamp DESC, id DESC"
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

    fun loadFileStatusEntries(after: HistoryFileStatusCursor? = null, limit: Int = STATUS_REFRESH_PAGE_SIZE): List<HistoryEntry> =
        databaseHelper.readableDatabase.query(
            TABLE_HISTORY,
            null,
            "is_downloaded = 1" + if (after != null) " AND (timestamp < ? OR (timestamp = ? AND id < ?))" else "",
            after?.let { arrayOf(it.timestamp.toString(), it.timestamp.toString(), it.id) },
            null,
            null,
            "timestamp DESC, id DESC",
            limit.coerceIn(1, MAX_PAGE_SIZE).toString()
        ).use(::readCursor)

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

    fun sources(): List<String> = databaseHelper.readableDatabase.rawQuery(
        "SELECT DISTINCT source_host FROM $TABLE_HISTORY WHERE source_host IS NOT NULL AND source_host != '' ORDER BY source_host", null
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

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
            SUM(CASE WHEN is_downloaded = 1 AND is_video = 0 AND (mime_type LIKE 'image/%' OR mime_type = '') THEN 1 ELSE 0 END),
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
                    thumbnailUrl = cursor.getString(cursor.getColumnIndexOrThrow("thumbnail_url")),
                    title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    author = cursor.getString(cursor.getColumnIndexOrThrow("author")),
                    sourceIdentity = cursor.getString(cursor.getColumnIndexOrThrow("source_identity")),
                    sourceHost = cursor.getString(cursor.getColumnIndexOrThrow("source_host"))
                )
            )
        }
    }

    override fun add(entry: HistoryEntry): Long {
        val database = databaseHelper.writableDatabase
        database.beginTransaction()
        val rowId: Long
        try {
            val previous = if (entry.type == HistoryEntryType.LINK) {
                database.query(
                    TABLE_HISTORY, null, "url = ? AND is_downloaded = 0",
                    arrayOf(entry.url), null, null, null
                ).use(::readCursor).firstOrNull()
            } else null
            rowId = database.insertWithOnConflict(
                TABLE_HISTORY, null, values(entry.copy(id = previous?.id ?: entry.id,
                    title = entry.title ?: previous?.title, author = entry.author ?: previous?.author,
                    sourceIdentity = entry.sourceIdentity ?: previous?.sourceIdentity)), SQLiteDatabase.CONFLICT_REPLACE
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

    fun importEntries(entries: List<HistoryEntry>): Int {
        var count = 0
        databaseHelper.writableDatabase.transaction {
            val existing = readEntries(this)
            val ids = existing.mapTo(mutableSetOf(), HistoryEntry::id)
            val identities = existing.mapTo(mutableSetOf(), HistoryTransfer::identity)
            entries.forEach { entry ->
                if (entry.id !in ids && HistoryTransfer.identity(entry) !in identities) {
                    check(insertWithOnConflict(TABLE_HISTORY, null, values(entry), SQLiteDatabase.CONFLICT_IGNORE) != -1L)
                    ids += entry.id
                    identities += HistoryTransfer.identity(entry)
                    count++
                }
            }
        }
        if (count > 0) revision.update { it + 1 }
        return count
    }

    fun relink(id: String, uri: android.net.Uri) {
        require(uri.scheme == "content")
        val entry = find(id)?.takeIf(HistoryEntry::isDownloaded) ?: return
        require(fileExists(entry.copy(fileUri = uri.toString())))
        val updated = databaseHelper.writableDatabase.update(TABLE_HISTORY, ContentValues().apply {
            put("file_uri", uri.toString()); put("file_status", HistoryFileStatus.AVAILABLE.name)
        }, "id = ?", arrayOf(id))
        if (updated > 0) revision.update { it + 1 }
    }

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
        put("title", entry.title)
        put("author", entry.author)
        put("source_identity", entry.sourceIdentity)
        put("source_host", entry.sourceHost ?: dev.qtremors.acqua.domain.WebLink.host(entry.url))
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
                    file_status TEXT,
                    title TEXT,
                    author TEXT,
                    source_identity TEXT,
                    source_host TEXT
                )
                """.trimIndent()
            )
            createIndexes(database)
            database.execSQL("CREATE INDEX IF NOT EXISTS history_source_timestamp_idx ON $TABLE_HISTORY(source_host, timestamp DESC)")
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
            if (oldVersion < 5) {
                listOf("title", "author", "source_identity", "source_host").forEach { column ->
                    database.execSQL("ALTER TABLE $TABLE_HISTORY ADD COLUMN $column TEXT")
                }
                database.query(TABLE_HISTORY, arrayOf("id", "url"), null, null, null, null, null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val host = dev.qtremors.acqua.domain.WebLink.host(cursor.getString(1).orEmpty())
                        database.update(TABLE_HISTORY, ContentValues().apply { put("source_host", host) }, "id = ?", arrayOf(cursor.getString(0)))
                    }
                }
                createIndexes(database)
                database.execSQL("CREATE INDEX IF NOT EXISTS history_source_timestamp_idx ON $TABLE_HISTORY(source_host, timestamp DESC)")
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
        const val DATABASE_VERSION = 5
        const val STATUS_REFRESH_PAGE_SIZE = 50
        const val MAX_PAGE_SIZE = 250

        fun escapeLike(value: String): String = value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }
}
