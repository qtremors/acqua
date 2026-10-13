package dev.qtremors.acqua.data.history

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryMetadataTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clearHistory() { HistoryRepository(context).clear() }

    @Test fun `version four migration preserves records and adds source metadata`() {
        val repository = HistoryRepository(context)
        val helperField = HistoryRepository::class.java.getDeclaredField("databaseHelper").apply { isAccessible = true }
        val helper = helperField.get(repository) as SQLiteOpenHelper
        val path = java.io.File(helper.readableDatabase.path)
        helper.close()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { database ->
            database.execSQL("DROP TABLE history")
            database.execSQL("CREATE TABLE history (id TEXT PRIMARY KEY, timestamp INTEGER, url TEXT, file_name TEXT, file_uri TEXT, is_video INTEGER, mime_type TEXT, size_bytes INTEGER, is_downloaded INTEGER, thumbnail_url TEXT, file_status TEXT)")
            database.execSQL("INSERT INTO history VALUES ('old', 123, 'https://example.com/post', 'Renamed.mp4', 'content://media/old', 1, 'video/mp4', 456, 1, NULL, 'MISSING')")
            database.version = 4
        }
        val migrated = repository.load().single()
        assertEquals("old", migrated.id)
        assertEquals(123L, migrated.timestamp)
        assertEquals("Renamed.mp4", migrated.fileName)
        assertEquals(456L, migrated.sizeBytes)
        assertEquals("example.com", migrated.sourceHost)
        assertNull(migrated.title)
        assertEquals(listOf(migrated), repository.loadPage(HistoryPageRequest(category = HistoryCategory.UNAVAILABLE)).entries)
        SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            assertEquals(5, database.version)
            database.rawQuery("SELECT name FROM sqlite_master WHERE name = 'history_source_timestamp_idx'", null).use { assertTrue(it.moveToFirst()) }
        }
    }

    @Test fun `title author and identity search combine with source missing filter sorting and paging`() {
        val repository = HistoryRepository(context)
        repeat(125) { index ->
            repository.add(entry(index.toString()).copy(
                url = "https://${if (index % 2 == 0) "one" else "two"}.example/post/$index",
                title = "Blue sky", author = "Aster", sourceIdentity = "extractor:$index", sizeBytes = index.toLong()))
        }
        repository.updateFileStatuses((0 until 125).associate { it.toString() to HistoryFileStatus.MISSING })
        val request = HistoryPageRequest(category = HistoryCategory.UNAVAILABLE, sourceHost = "one.example",
            terms = listOf("Blue", "Aster"), limit = 25, order = HistoryOrder.LARGEST)
        val pages = listOf(0, 25, 50).flatMap { repository.loadPage(request.copy(offset = it)).entries }
        assertEquals(63, pages.size)
        assertEquals(63, pages.map { it.id }.distinct().size)
        assertEquals("124", pages.first().id)
        assertTrue(pages.all { it.fileName == "Renamed.mp4" && it.sourceHost == "one.example" })
        assertEquals("123", repository.loadPage(HistoryPageRequest(terms = listOf("extractor:123"))).entries.single().id)
        assertEquals(listOf("one.example", "two.example"), repository.sources())
        assertEquals(125, repository.load().size)
    }

    @Test fun `revisiting a link without fresh metadata keeps its title author and source identity`() {
        val repository = HistoryRepository(context)
        val original = entry("first").copy(isDownloaded = false, title = "Blue sky", author = "Aster", sourceIdentity = "source:42")
        repository.add(original)
        repository.add(original.copy(id = "second", title = null, author = null, sourceIdentity = null, timestamp = 20))
        val saved = repository.load().single()
        assertEquals("first", saved.id)
        assertEquals("Blue sky", saved.title)
        assertEquals("Aster", saved.author)
        assertEquals("source:42", saved.sourceIdentity)
        assertEquals(20L, saved.timestamp)
        assertEquals(saved, repository.loadPage(HistoryPageRequest(category = HistoryCategory.LINKS, terms = listOf("Aster"))).entries.single())
    }

    private fun entry(id: String) = HistoryEntry(id, 10, "https://one.example/post", "Renamed.mp4", "content://media/$id",
        true, "video/mp4", 1, true)
}
