package dev.qtremors.acqua.feature.downloader.history

import dev.qtremors.acqua.data.history.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryQueryTest {
    private val photo = entry(
        id = "photo",
        fileName = "Sunset Beach.jpg",
        url = "https://photos.example/summer",
        mimeType = "image/jpeg",
        timestamp = 20,
        size = 200
    )
    private val video = entry(
        id = "video",
        fileName = "Morning Ride.mp4",
        url = "https://video.example/cycling",
        mimeType = "video/mp4",
        timestamp = 30,
        size = 900,
        video = true
    )
    private val audio = entry(
        id = "audio",
        fileName = "Live Session.m4a",
        url = "https://music.example/session",
        mimeType = "audio/mp4",
        timestamp = 10,
        size = 500
    )
    private val link = entry(
        id = "link",
        fileName = "",
        url = "https://social.example/saved-post",
        mimeType = "text/uri-list",
        timestamp = 40,
        size = 0,
        downloaded = false
    )
    private val entries = listOf(photo, video, audio, link)

    @Test
    fun `history query ignores repeated whitespace`() {
        val query = HistoryQuery(text = "  sunset   beach  ")

        assertEquals(listOf("sunset", "beach"), query.normalizedTerms)
    }

    @Test
    fun `history query removes duplicate terms`() {
        val query = HistoryQuery(text = "ride ride video")

        assertEquals(listOf("ride", "video"), query.normalizedTerms)
    }

    @Test
    fun `blank query is not searching`() {
        assertFalse(HistoryQuery(text = " \n\t ").isSearching)
    }

    @Test
    fun `nonblank query is searching`() {
        assertTrue(HistoryQuery(text = "photo").isSearching)
    }

    @Test
    fun `media filter includes every downloaded media type`() {
        val result = entries.applyHistoryQuery(HistoryQuery(filter = HistoryFilter.MEDIA))

        assertEquals(listOf(video, photo, audio), result)
    }

    @Test
    fun `photo filter excludes videos audio and links`() {
        val result = entries.applyHistoryQuery(HistoryQuery(filter = HistoryFilter.PHOTOS))

        assertEquals(listOf(photo), result)
    }

    @Test
    fun `video filter excludes audio even if legacy video bit is set`() {
        val legacyAudio = audio.copy(isVideo = true)
        val result = (entries + legacyAudio).applyHistoryQuery(
            HistoryQuery(filter = HistoryFilter.VIDEOS)
        )

        assertEquals(listOf(video), result)
    }

    @Test
    fun `audio filter uses MIME type`() {
        val result = entries.applyHistoryQuery(HistoryQuery(filter = HistoryFilter.AUDIO))

        assertEquals(listOf(audio), result)
    }

    @Test
    fun `links filter excludes downloaded records`() {
        val result = entries.applyHistoryQuery(HistoryQuery(filter = HistoryFilter.LINKS))

        assertEquals(listOf(link), result)
    }

    @Test
    fun `search matches filename case insensitively`() {
        val result = entries.applyHistoryQuery(HistoryQuery(text = "MORNING"))

        assertEquals(listOf(video), result)
    }

    @Test
    fun `search matches source host`() {
        val result = entries.applyHistoryQuery(HistoryQuery(text = "photos.example"))

        assertEquals(listOf(photo), result)
    }

    @Test
    fun `search matches MIME type`() {
        val result = entries.applyHistoryQuery(HistoryQuery(text = "image/jpeg"))

        assertEquals(listOf(photo), result)
    }

    @Test
    fun `search understands friendly photo term`() {
        val result = entries.applyHistoryQuery(HistoryQuery(text = "picture"))

        assertEquals(listOf(photo), result)
    }

    @Test
    fun `search understands friendly video term`() {
        val result = entries.applyHistoryQuery(HistoryQuery(text = "clip"))

        assertEquals(listOf(video), result)
    }

    @Test
    fun `search understands friendly audio term`() {
        val result = entries.applyHistoryQuery(HistoryQuery(text = "music"))

        assertEquals(listOf(audio), result)
    }

    @Test
    fun `search understands friendly link term`() {
        val result = entries.applyHistoryQuery(
            HistoryQuery(filter = HistoryFilter.LINKS, text = "source")
        )

        assertEquals(listOf(link), result)
    }

    @Test
    fun `all search terms must match the same record`() {
        val matching = entries.applyHistoryQuery(HistoryQuery(text = "morning cycling"))
        val splitAcrossRecords = entries.applyHistoryQuery(HistoryQuery(text = "sunset cycling"))

        assertEquals(listOf(video), matching)
        assertTrue(splitAcrossRecords.isEmpty())
    }

    @Test
    fun `search is applied after selected media filter`() {
        val result = entries.applyHistoryQuery(
            HistoryQuery(filter = HistoryFilter.PHOTOS, text = "video")
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `newest sorting orders descending and preserves ties`() {
        val first = photo.copy(id = "first", timestamp = 50)
        val second = video.copy(id = "second", timestamp = 50)

        val result = listOf(first, audio, second).applyHistoryQuery(
            HistoryQuery(sort = HistorySort.NEWEST)
        )

        assertEquals(listOf(first, second, audio), result)
    }

    @Test
    fun `oldest sorting orders ascending`() {
        val result = entries.applyHistoryQuery(HistoryQuery(sort = HistorySort.OLDEST))

        assertEquals(listOf(audio, photo, video), result)
    }

    @Test
    fun `largest sorting uses recency when sizes match`() {
        val older = photo.copy(id = "older", timestamp = 1, sizeBytes = 500)
        val newer = video.copy(id = "newer", timestamp = 2, sizeBytes = 500)

        val result = listOf(older, newer, audio).applyHistoryQuery(
            HistoryQuery(sort = HistorySort.LARGEST)
        )

        assertEquals(listOf(audio, newer, older), result)
    }

    @Test
    fun `summary counts every history category`() {
        val summary = entries.summarizeHistory(emptySet())

        assertEquals(3, summary.downloads)
        assertEquals(1, summary.links)
        assertEquals(1, summary.photos)
        assertEquals(1, summary.videos)
        assertEquals(1, summary.audio)
        assertEquals(4, summary.totalEntries)
        assertEquals(3, summary.availableDownloads)
    }

    @Test
    fun `summary returns counts for filter badges`() {
        val summary = entries.summarizeHistory(emptySet())

        assertEquals(3, summary.countFor(HistoryFilter.MEDIA))
        assertEquals(1, summary.countFor(HistoryFilter.PHOTOS))
        assertEquals(1, summary.countFor(HistoryFilter.VIDEOS))
        assertEquals(1, summary.countFor(HistoryFilter.AUDIO))
        assertEquals(1, summary.countFor(HistoryFilter.LINKS))
    }

    @Test
    fun `summary excludes missing file sizes`() {
        val summary = entries.summarizeHistory(setOf(video.id))

        assertEquals(1, summary.missing)
        assertEquals(photo.sizeBytes + audio.sizeBytes, summary.storedBytes)
        assertEquals(2, summary.availableDownloads)
    }

    @Test
    fun `summary ignores missing ids that are no longer in history`() {
        val summary = entries.summarizeHistory(setOf("already-deleted"))

        assertEquals(0, summary.missing)
        assertEquals(3, summary.availableDownloads)
        assertEquals(photo.sizeBytes + video.sizeBytes + audio.sizeBytes, summary.storedBytes)
    }

    @Test
    fun `summary ignores negative legacy sizes`() {
        val summary = listOf(photo.copy(sizeBytes = -200)).summarizeHistory(emptySet())

        assertEquals(0L, summary.storedBytes)
    }

    @Test
    fun `summary saturates size overflow`() {
        val huge = photo.copy(id = "huge", sizeBytes = Long.MAX_VALUE)
        val extra = video.copy(id = "extra", sizeBytes = 10)

        assertEquals(Long.MAX_VALUE, listOf(huge, extra).summarizeHistory(emptySet()).storedBytes)
    }

    @Test
    fun `empty history reports initial empty reason`() {
        assertEquals(
            HistoryEmptyReason.NO_HISTORY,
            historyEmptyReason(emptyList(), HistoryQuery(text = "anything"))
        )
    }

    @Test
    fun `empty search results report search reason`() {
        assertEquals(
            HistoryEmptyReason.NO_SEARCH_RESULTS,
            historyEmptyReason(entries, HistoryQuery(text = "not-found"))
        )
    }

    @Test
    fun `empty category reports filter reason`() {
        assertEquals(
            HistoryEmptyReason.NO_FILTER_RESULTS,
            historyEmptyReason(entries, HistoryQuery(filter = HistoryFilter.PHOTOS))
        )
    }

    private fun entry(
        id: String,
        fileName: String,
        url: String,
        mimeType: String,
        timestamp: Long,
        size: Long,
        video: Boolean = false,
        downloaded: Boolean = true
    ) = HistoryEntry(
        id = id,
        timestamp = timestamp,
        url = url,
        fileName = fileName,
        fileUri = if (downloaded) "content://downloads/$id" else "",
        isVideo = video,
        mimeType = mimeType,
        sizeBytes = size,
        isDownloaded = downloaded
    )
}
