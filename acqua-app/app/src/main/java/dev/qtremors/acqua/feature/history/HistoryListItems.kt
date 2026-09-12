package dev.qtremors.acqua.feature.history

import dev.qtremors.acqua.data.history.HistoryEntry
import dev.qtremors.acqua.domain.WebLink
import java.util.Calendar
import java.util.TimeZone

internal enum class HistoryDay { TODAY, YESTERDAY, EARLIER }

internal sealed interface HistoryListItem {
    val key: String
    data class Day(val day: HistoryDay, override val key: String) : HistoryListItem
    data class Group(val entries: List<HistoryEntry>, override val key: String) : HistoryListItem
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
    entries: List<HistoryEntry>, sort: HistorySort, groupBySource: Boolean,
    expandedGroups: Set<String>, now: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()
): List<HistoryListItem> {
    val grouped = linkedMapOf<String, MutableList<HistoryEntry>>()
    entries.forEach { entry ->
        val source = WebLink.mediaPageIdentity(entry.url)
        val calendar = Calendar.getInstance(zone).apply { timeInMillis = entry.timestamp }
        val day = "${calendar.get(Calendar.YEAR)}:${calendar.get(Calendar.DAY_OF_YEAR)}"
        val key = if (groupBySource && entry.isDownloaded && source != null) "source:$day:$source" else "single:${entry.id}"
        grouped.getOrPut(key) { mutableListOf() }.add(entry)
    }
    return buildList {
        var previousDay: HistoryDay? = null
        grouped.forEach { (key, group) ->
            val day = historyDay(group.first().timestamp, now, zone)
            if (sort != HistorySort.LARGEST && day != previousDay) {
                add(HistoryListItem.Day(day, "day:$key"))
                previousDay = day
            }
            if (group.size > 1) {
                add(HistoryListItem.Group(group, key))
                if (key in expandedGroups) group.forEach { add(HistoryListItem.Entry(it)) }
            } else add(HistoryListItem.Entry(group.single()))
        }
    }
}
