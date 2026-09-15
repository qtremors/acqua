package dev.qtremors.acqua.feature.downloader.history

import dev.qtremors.acqua.data.history.HistoryEntry
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class HistoryListItemsTest {
    private val zone = TimeZone.getTimeZone("UTC")
    private val now = 1_800_000_000_000L
    private fun entry(id: String, url: String = "https://www.instagram.com/p/post/?img_index=$id", time: Long = now) =
        HistoryEntry(id, time, url, "$id.jpg", "content://downloads/$id", false, "image/jpeg", 10L, true)

    @Test fun `entries remain visible and in order`() {
        val entries = listOf(entry("1"), entry("2"))
        val rows = historyListItems(entries, HistorySort.NEWEST, now, zone)
        assertEquals(entries, rows.filterIsInstance<HistoryListItem.Entry>().map { it.entry })
        assertEquals(rows.size, rows.map { it.key }.distinct().size)
    }

    @Test fun `largest sorting has no misleading date headings`() {
        val rows = historyListItems(listOf(entry("1")), HistorySort.LARGEST, now, zone)
        assertTrue(rows.none { it is HistoryListItem.Day })
    }

    @Test fun `yesterday uses calendar boundaries across daylight saving`() {
        val dstZone = TimeZone.getTimeZone("America/New_York")
        val today = Calendar.getInstance(dstZone).apply { clear(); set(2026, Calendar.MARCH, 9, 0, 30) }.timeInMillis
        val yesterday = Calendar.getInstance(dstZone).apply { clear(); set(2026, Calendar.MARCH, 8, 0, 15) }.timeInMillis
        assertEquals(HistoryDay.YESTERDAY, historyDay(yesterday, today, dstZone))
        assertEquals(HistoryDay.TODAY, historyDay(today, today, dstZone))
    }

    @Test fun `unavailable filter respects search and excludes accessible files`() {
        val entries = listOf(entry("cat"), entry("dog"), entry("cat2"))
        val result = entries.applyHistoryQuery(HistoryQuery(filter = HistoryFilter.UNAVAILABLE, text = "cat"), setOf("cat", "dog"))
        assertEquals(listOf("cat"), result.map { it.id })
    }
}
