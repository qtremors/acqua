package dev.qtremors.acqua.data.history

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import android.content.Context
import java.io.File
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryRepositoryTest {
    private val repository = HistoryRepository(ApplicationProvider.getApplicationContext())

    @Before
    fun setUp() {
        repository.clear()
    }

    @After
    fun tearDown() {
        repository.clear()
    }

    @Test
    fun downloadedEntryRoundTrips() {
        val entry = HistoryEntry("one", 123, "https://example.com", "photo.png", "content://photo", false, "image/png", 42, true)
        repository.add(entry)
        assertEquals(listOf(entry), repository.load())
    }

    @Test
    fun repeatedLinkReplacesExistingRecord() {
        repository.add(link("one", 1))
        repository.add(link("two", 2))
        assertEquals(1, repository.load().size)
        assertEquals(2, repository.load().single().timestamp)
    }

    @Test
    fun removalAndUndoKeepFilesAndNewerRecords() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("history-test", ".jpg", context.cacheDir)
        try {
            val download = HistoryEntry("download", 5, "https://example.com/post", "photo.jpg", file.toURI().toString(), false, "image/jpeg", 1, true)
            repository.add(download)
            val removed = repository.remove(setOf(download.id))
            assertTrue(repository.load().isEmpty())
            assertTrue(file.isFile)
            repository.add(link("new", 10))
            repository.restore(removed)
            assertEquals(setOf("download", "new"), repository.load().map { it.id }.toSet())
            assertTrue(file.isFile)
        } finally { file.delete() }
    }

    @Test
    fun undoDoesNotOverwriteANewerVisitToTheSameLink() {
        repository.add(link("old", 1))
        val removed = repository.remove(null)
        repository.add(link("new", 2))
        repository.restore(removed)
        assertEquals(listOf(link("new", 2)), repository.load())
    }

    @Test
    fun changesAreVisibleAcrossRepositoryInstances() {
        val other = HistoryRepository(ApplicationProvider.getApplicationContext())
        val before = other.changes.value
        repository.add(link("new", 1))
        assertTrue(other.changes.value > before)
        val afterAdd = other.changes.value
        repository.remove(null)
        assertTrue(other.changes.value > afterAdd)
    }

    @Test
    fun providerFailureIsUnavailableRatherThanConfirmedMissing() {
        val entry = HistoryEntry("one", 1, "https://example.com", "file.jpg", "content://no.such.provider/item", false, "image/jpeg", 1, true)
        assertEquals(HistoryFileStatus.UNAVAILABLE, repository.fileStatus(entry))
        assertEquals(HistoryFileStatus.MISSING, repository.fileStatus(entry.copy(fileUri = "file:///does-not-exist/acqua.jpg")))
    }

    private fun link(id: String, timestamp: Long) = HistoryEntry(
        id, timestamp, "https://example.com/page", "", "", false, "", 0, false
    )
}
