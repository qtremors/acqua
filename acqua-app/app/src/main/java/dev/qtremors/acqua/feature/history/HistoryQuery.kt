package dev.qtremors.acqua.feature.history

import dev.qtremors.acqua.data.history.HistoryEntry

enum class HistoryFilter { MEDIA, PHOTOS, VIDEOS, AUDIO, LINKS, UNAVAILABLE }

enum class HistorySort { NEWEST, OLDEST, LARGEST }

enum class HistoryEmptyReason {
    NO_HISTORY,
    NO_SEARCH_RESULTS,
    NO_FILTER_RESULTS
}

data class HistoryQuery(
    val filter: HistoryFilter = HistoryFilter.MEDIA,
    val sort: HistorySort = HistorySort.NEWEST,
    val text: String = ""
) {
    val normalizedTerms: List<String>
        get() = text.trim()
            .split(WHITESPACE)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()

    val isSearching: Boolean
        get() = normalizedTerms.isNotEmpty()

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}

data class HistorySummary(
    val downloads: Int = 0,
    val links: Int = 0,
    val photos: Int = 0,
    val videos: Int = 0,
    val audio: Int = 0,
    val missing: Int = 0,
    val storedBytes: Long = 0L
) {
    val totalEntries: Int
        get() = downloads + links

    val availableDownloads: Int
        get() = (downloads - missing).coerceAtLeast(0)

    fun countFor(filter: HistoryFilter): Int = when (filter) {
        HistoryFilter.MEDIA -> downloads
        HistoryFilter.PHOTOS -> photos
        HistoryFilter.VIDEOS -> videos
        HistoryFilter.AUDIO -> audio
        HistoryFilter.LINKS -> links
        HistoryFilter.UNAVAILABLE -> missing
    }
}

internal fun List<HistoryEntry>.applyHistoryQuery(query: HistoryQuery, unavailableIds: Set<String> = emptySet()): List<HistoryEntry> {
    val terms = query.normalizedTerms
    val filtered = asSequence()
        .filter { entry -> if (query.filter == HistoryFilter.UNAVAILABLE) entry.id in unavailableIds else entry.matches(query.filter) }
        .filter { entry -> terms.all(entry::matchesTerm) }

    val comparator = when (query.sort) {
        HistorySort.NEWEST -> compareByDescending(HistoryEntry::timestamp)
        HistorySort.OLDEST -> compareBy(HistoryEntry::timestamp)
        HistorySort.LARGEST -> compareByDescending<HistoryEntry>(HistoryEntry::sizeBytes)
            .thenByDescending(HistoryEntry::timestamp)
    }
    return filtered.sortedWith(comparator).toList()
}

internal fun List<HistoryEntry>.summarizeHistory(missingEntryIds: Set<String>): HistorySummary {
    var downloads = 0
    var links = 0
    var photos = 0
    var videos = 0
    var audio = 0
    var missing = 0
    var storedBytes = 0L

    forEach { entry ->
        if (!entry.isDownloaded) {
            links += 1
            return@forEach
        }
        downloads += 1
        when {
            entry.isAudio -> audio += 1
            entry.isVideo -> videos += 1
            else -> photos += 1
        }
        if (entry.id in missingEntryIds) {
            missing += 1
        } else {
            storedBytes = saturatedAdd(storedBytes, entry.sizeBytes.coerceAtLeast(0L))
        }
    }

    return HistorySummary(
        downloads = downloads,
        links = links,
        photos = photos,
        videos = videos,
        audio = audio,
        missing = missing,
        storedBytes = storedBytes
    )
}

internal fun historyEmptyReason(
    entries: List<HistoryEntry>,
    query: HistoryQuery
): HistoryEmptyReason = when {
    entries.isEmpty() -> HistoryEmptyReason.NO_HISTORY
    query.isSearching -> HistoryEmptyReason.NO_SEARCH_RESULTS
    else -> HistoryEmptyReason.NO_FILTER_RESULTS
}

private fun HistoryEntry.matches(filter: HistoryFilter): Boolean = when (filter) {
    HistoryFilter.MEDIA -> isDownloaded
    HistoryFilter.PHOTOS -> isDownloaded && !isVideo && !isAudio
    HistoryFilter.VIDEOS -> isDownloaded && isVideo && !isAudio
    HistoryFilter.AUDIO -> isAudio
    HistoryFilter.LINKS -> !isDownloaded
    HistoryFilter.UNAVAILABLE -> false
}

private fun HistoryEntry.matchesTerm(term: String): Boolean {
    if (fileName.contains(term, ignoreCase = true)) return true
    if (url.contains(term, ignoreCase = true)) return true
    if (mimeType.contains(term, ignoreCase = true)) return true
    return searchableTypeNames().any { it.contains(term, ignoreCase = true) }
}

private fun HistoryEntry.searchableTypeNames(): List<String> = when {
    !isDownloaded -> listOf("link", "source")
    isAudio -> listOf("audio", "music", "sound")
    isVideo -> listOf("video", "movie", "clip")
    else -> listOf("photo", "image", "picture")
}

private fun saturatedAdd(first: Long, second: Long): Long =
    if (Long.MAX_VALUE - first < second) Long.MAX_VALUE else first + second
