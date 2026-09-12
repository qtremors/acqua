package dev.qtremors.acqua.feature.history

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

    @Test fun `grouping is optional and never hides entries when disabled`() {
        val entries = listOf(entry("1"), entry("2"))
        val rows = historyListItems(entries, HistorySort.NEWEST, false, emptySet(), now, zone)
        assertEquals(entries, rows.filterIsInstance<HistoryListItem.Entry>().map { it.entry })
        assertTrue(rows.none { it is HistoryListItem.Group })
    }

    @Test fun `carousel query variants collapse into one expandable group`() {
        val entries = listOf(entry("1"), entry("2"))
        val collapsed = historyListItems(entries, HistorySort.NEWEST, true, emptySet(), now, zone)
        val group = collapsed.filterIsInstance<HistoryListItem.Group>().single()
        assertEquals(entries, group.entries)
        assertTrue(collapsed.none { it is HistoryListItem.Entry })
        val expanded = historyListItems(entries, HistorySort.NEWEST, true, setOf(group.key), now, zone)
        assertEquals(entries, expanded.filterIsInstance<HistoryListItem.Entry>().map { it.entry })
        assertEquals(expanded.size, expanded.map { it.key }.distinct().size)
    }

    @Test fun `different posts days and link records are not combined`() {
        val entries = listOf(entry("1"), entry("2", time = now - 86_400_000L), entry("3", url = "https://www.instagram.com/p/other/"), entry("link").copy(isDownloaded = false))
        val rows = historyListItems(entries, HistorySort.NEWEST, true, emptySet(), now, zone)
        assertTrue(rows.none { it is HistoryListItem.Group })
        assertEquals(4, rows.filterIsInstance<HistoryListItem.Entry>().size)
    }

    @Test fun `search grouping contains only matching files`() {
        val entries = listOf(entry("cat"), entry("dog"), entry("cat2"))
        val matches = entries.applyHistoryQuery(HistoryQuery(text = "cat"))
        val rows = historyListItems(matches, HistorySort.NEWEST, true, emptySet(), now, zone)
        assertEquals(listOf("cat", "cat2"), rows.filterIsInstance<HistoryListItem.Group>().single().entries.map { it.id })
    }

    @Test fun `largest sorting has no misleading date headings`() {
        val rows = historyListItems(listOf(entry("1")), HistorySort.LARGEST, false, emptySet(), now, zone)
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
