package dev.qtremors.acqua.data.history

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
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

    private fun link(id: String, timestamp: Long) = HistoryEntry(
        id, timestamp, "https://example.com/page", "", "", false, "", 0, false
    )
}
