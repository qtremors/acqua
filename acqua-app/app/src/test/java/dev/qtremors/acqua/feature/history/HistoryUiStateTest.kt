package dev.qtremors.acqua.feature.history

import dev.qtremors.acqua.data.history.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryUiStateTest {
    private val photo = entry("photo", downloaded = true, video = false)
    private val video = entry("video", downloaded = true, video = true)
    private val link = entry("link", downloaded = false, video = false)

    @Test fun `media filter excludes unresolved links`() = assertEquals(
        listOf(photo, video), HistoryUiState(listOf(photo, video, link), HistoryFilter.MEDIA).filteredEntries
    )

    @Test fun `photo filter returns photos only`() = assertEquals(
        listOf(photo), HistoryUiState(listOf(photo, video, link), HistoryFilter.PHOTOS).filteredEntries
    )

    @Test fun `video filter returns videos only`() = assertEquals(
        listOf(video), HistoryUiState(listOf(photo, video, link), HistoryFilter.VIDEOS).filteredEntries
    )

    @Test fun `link filter returns unresolved links only`() = assertEquals(
        listOf(link), HistoryUiState(listOf(photo, video, link), HistoryFilter.LINKS).filteredEntries
    )

    private fun entry(id: String, downloaded: Boolean, video: Boolean) = HistoryEntry(
        id, 1, "https://example.com/$id", "$id.jpg", "content://$id", video,
        if (video) "video/mp4" else "image/jpeg", 1, downloaded
    )
}
