package dev.qtremors.acqua.feature.downloader.history

import dev.qtremors.acqua.data.history.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryUiStateTest {
    private val photo = entry("photo", downloaded = true, video = false)
    private val video = entry("video", downloaded = true, video = true)
    private val audio = entry("audio", downloaded = true, video = false, mimeType = "audio/mp4")
    private val link = entry("link", downloaded = false, video = false)

    @Test fun `media filter excludes unresolved links`() = assertEquals(
        listOf(photo, video, audio),
        HistoryUiState(listOf(photo, video, audio, link), HistoryFilter.MEDIA).filteredEntries
    )

    @Test fun `photo filter returns photos only`() = assertEquals(
        listOf(photo), HistoryUiState(listOf(photo, video, audio, link), HistoryFilter.PHOTOS).filteredEntries
    )

    @Test fun `video filter returns videos only`() = assertEquals(
        listOf(video), HistoryUiState(listOf(photo, video, audio, link), HistoryFilter.VIDEOS).filteredEntries
    )

    @Test fun `audio filter returns audio only`() = assertEquals(
        listOf(audio), HistoryUiState(listOf(photo, video, audio, link), HistoryFilter.AUDIO).filteredEntries
    )

    @Test fun `link filter returns unresolved links only`() = assertEquals(
        listOf(link), HistoryUiState(listOf(photo, video, link), HistoryFilter.LINKS).filteredEntries
    )

    @Test fun `search matches filenames and source links without case sensitivity`() = assertEquals(
        listOf(video),
        HistoryUiState(
            entries = listOf(photo, video, audio),
            query = "VIDEO"
        ).filteredEntries
    )

    @Test fun `largest sort orders media by saved size`() = assertEquals(
        listOf(video.copy(sizeBytes = 30), photo.copy(sizeBytes = 20), audio.copy(sizeBytes = 10)),
        HistoryUiState(
            entries = listOf(photo.copy(sizeBytes = 20), audio.copy(sizeBytes = 10), video.copy(sizeBytes = 30)),
            sort = HistorySort.LARGEST
        ).filteredEntries
    )

    @Test fun `oldest sort orders entries chronologically`() = assertEquals(
        listOf(photo.copy(timestamp = 1), video.copy(timestamp = 2)),
        HistoryUiState(
            entries = listOf(video.copy(timestamp = 2), photo.copy(timestamp = 1)),
            sort = HistorySort.OLDEST
        ).filteredEntries
    )

    private fun entry(
        id: String,
        downloaded: Boolean,
        video: Boolean,
        mimeType: String = if (video) "video/mp4" else "image/jpeg"
    ) = HistoryEntry(
        id, 1, "https://example.com/$id", "$id.jpg", "content://$id", video,
        mimeType, 1, downloaded
    )
}
