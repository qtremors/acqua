package dev.qtremors.acqua.feature.downloader.history

import dev.qtremors.acqua.data.history.HistoryEntry
import java.util.Calendar
import java.util.TimeZone

internal enum class HistoryDay { TODAY, YESTERDAY, EARLIER }

internal sealed interface HistoryListItem {
    val key: String
    data class Day(val day: HistoryDay, override val key: String) : HistoryListItem
    data class Entry(val entry: HistoryEntry) : HistoryListItem { override val key = "entry:${entry.id}" }
}

internal fun historyDay(timestamp: Long, now: Long, zone: TimeZone): HistoryDay {
    val today = Calendar.getInstance(zone).apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DATE, 1) }
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DATE, -1) }
    return when (timestamp) {
        in today.timeInMillis until tomorrow.timeInMillis -> HistoryDay.TODAY
        in yesterday.timeInMillis until today.timeInMillis -> HistoryDay.YESTERDAY
        else -> HistoryDay.EARLIER
    }
}

internal fun historyListItems(
    entries: List<HistoryEntry>,
    sort: HistorySort,
    now: Long = System.currentTimeMillis(),
    zone: TimeZone = TimeZone.getDefault()
): List<HistoryListItem> {
    return buildList {
        var previousDay: HistoryDay? = null
        entries.forEach { entry ->
            val day = historyDay(entry.timestamp, now, zone)
            if (sort != HistorySort.LARGEST && day != previousDay) {
                add(HistoryListItem.Day(day, "day:${entry.id}"))
                previousDay = day
            }
            add(HistoryListItem.Entry(entry))
        }
    }
}
