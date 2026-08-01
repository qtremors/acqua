package dev.qtremors.acqua.downloader

import androidx.work.Data
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadWorkDataTest {
    @Test
    fun `processed request round trips media output and options`() {
        val media = completeMedia(backend = MediaBackend.YT_DLP)
        val output = media.copy(width = 1280, height = 720)
        val options = YtDlpDownloadOptions(
            contentType = DownloadContentType.AUDIO,
            maximumVideoHeight = 1080,
            audioFormat = AudioOutputFormat.MP3,
            embedMetadata = false,
            embedThumbnail = false
        )

        val decoded = DownloadWorkData.decode(
            DownloadWorkData.processedRequest(media, output, options, SOURCE_URL)
        )!!

        assertEquals(media.url, decoded.media.url)
        assertEquals(media.kind, decoded.media.kind)
        assertEquals(media.backend, decoded.media.backend)
        assertEquals(media.thumbnailUrl, decoded.media.thumbnailUrl)
        assertEquals(media.username, decoded.media.username)
        assertEquals(media.title, decoded.media.title)
        assertEquals(media.sourceTimestampMillis, decoded.media.sourceTimestampMillis)
        assertEquals(1280, decoded.outputMedia.width)
        assertEquals(720, decoded.outputMedia.height)
        assertEquals(options, decoded.options)
        assertEquals(SOURCE_URL, decoded.sourceUrl)
    }

    @Test
    fun `direct request preserves transport metadata`() {
        val media = completeMedia(backend = MediaBackend.DIRECT)

        val decoded = DownloadWorkData.decode(
            DownloadWorkData.directRequest(media, itemIndex = 4, sourceUrl = SOURCE_URL)
        )!!

        assertEquals(MediaBackend.DIRECT, decoded.media.backend)
        assertEquals(media.fileSize, decoded.media.fileSize)
        assertEquals(media.referer, decoded.media.referer)
        assertEquals(media.mimeType, decoded.media.mimeType)
        assertEquals(media.fileExtension, decoded.media.fileExtension)
        assertTrue(decoded.media.explicitBrowserSessionAuthorized)
        assertEquals(4, decoded.itemIndex)
    }

    @Test
    fun `direct request defaults to original video settings`() {
        val decoded = DownloadWorkData.decode(
            DownloadWorkData.directRequest(
                completeMedia(backend = MediaBackend.DIRECT),
                itemIndex = 0,
                sourceUrl = SOURCE_URL
            )
        )!!

        assertEquals(DownloadContentType.VIDEO, decoded.options.contentType)
        assertEquals(AudioOutputFormat.ORIGINAL, decoded.options.audioFormat)
        assertEquals(0, decoded.options.maximumVideoHeight)
        assertTrue(decoded.options.embedMetadata)
        assertTrue(decoded.options.embedThumbnail)
    }

    @Test
    fun `missing media URL rejects stale work`() {
        val data = Data.Builder().putString(DownloadWorkData.KEY_SOURCE_URL, SOURCE_URL).build()

        assertNull(DownloadWorkData.decode(data))
    }

    @Test
    fun `blank media URL rejects malformed work`() {
        val data = Data.Builder()
            .putString(DownloadWorkData.KEY_MEDIA_URL, "  ")
            .putString(DownloadWorkData.KEY_SOURCE_URL, SOURCE_URL)
            .build()

        assertNull(DownloadWorkData.decode(data))
    }

    @Test
    fun `missing source URL rejects stale work`() {
        val data = Data.Builder().putString(DownloadWorkData.KEY_MEDIA_URL, MEDIA_URL).build()

        assertNull(DownloadWorkData.decode(data))
    }

    @Test
    fun `unknown enum values fall back safely`() {
        val data = minimumData()
            .putString(DownloadWorkData.KEY_BACKEND, "REMOVED_BACKEND")
            .putString(DownloadWorkData.KEY_CONTENT_TYPE, "REMOVED_CONTENT_TYPE")
            .putString(DownloadWorkData.KEY_AUDIO_FORMAT, "REMOVED_AUDIO_FORMAT")
            .build()

        val decoded = DownloadWorkData.decode(data)!!

        assertEquals(MediaBackend.YT_DLP, decoded.media.backend)
        assertEquals(DownloadContentType.VIDEO, decoded.options.contentType)
        assertEquals(AudioOutputFormat.ORIGINAL, decoded.options.audioFormat)
    }

    @Test
    fun `negative dimensions indexes and quality are sanitized`() {
        val data = minimumData()
            .putInt(DownloadWorkData.KEY_MEDIA_WIDTH, -1920)
            .putInt(DownloadWorkData.KEY_MEDIA_HEIGHT, -1080)
            .putInt(DownloadWorkData.KEY_OUTPUT_WIDTH, -640)
            .putInt(DownloadWorkData.KEY_OUTPUT_HEIGHT, -360)
            .putInt(DownloadWorkData.KEY_ITEM_INDEX, -7)
            .putInt(DownloadWorkData.KEY_MAXIMUM_VIDEO_QUALITY, -1)
            .build()

        val decoded = DownloadWorkData.decode(data)!!

        assertEquals(0, decoded.media.width)
        assertEquals(0, decoded.media.height)
        assertEquals(0, decoded.outputMedia.width)
        assertEquals(0, decoded.outputMedia.height)
        assertEquals(0, decoded.itemIndex)
        assertEquals(0, decoded.options.maximumVideoHeight)
    }

    @Test
    fun `non-positive optional longs are discarded`() {
        val data = minimumData()
            .putLong(DownloadWorkData.KEY_FILE_SIZE, -5L)
            .putLong(DownloadWorkData.KEY_SOURCE_TIMESTAMP_MILLIS, 0L)
            .build()

        val decoded = DownloadWorkData.decode(data)!!

        assertNull(decoded.media.fileSize)
        assertNull(decoded.media.sourceTimestampMillis)
    }

    @Test
    fun `blank optional strings are discarded`() {
        val data = minimumData()
            .putString(DownloadWorkData.KEY_TITLE, "")
            .putString(DownloadWorkData.KEY_USERNAME, "   ")
            .putString(DownloadWorkData.KEY_REFERER, "")
            .putString(DownloadWorkData.KEY_MIME_TYPE, " ")
            .putString(DownloadWorkData.KEY_FILE_EXTENSION, "")
            .build()

        val decoded = DownloadWorkData.decode(data)!!

        assertNull(decoded.media.title)
        assertNull(decoded.media.username)
        assertNull(decoded.media.referer)
        assertNull(decoded.media.mimeType)
        assertNull(decoded.media.fileExtension)
    }

    @Test
    fun `progress data clamps percentage and eta`() {
        val below = DownloadWorkData.progress(-10f, -5L)
        val above = DownloadWorkData.progress(140f, 12L)

        assertEquals(0f, below.getFloat(DownloadWorkData.KEY_PROGRESS, -1f), 0.001f)
        assertEquals(0L, below.getLong(DownloadWorkData.KEY_ETA_SECONDS, -1L))
        assertEquals(100f, above.getFloat(DownloadWorkData.KEY_PROGRESS, -1f), 0.001f)
        assertEquals(12L, above.getLong(DownloadWorkData.KEY_ETA_SECONDS, -1L))
    }

    @Test
    fun `error data uses message when available`() {
        val data = DownloadWorkData.error("Network unavailable")

        assertEquals("Network unavailable", data.getString(DownloadWorkData.KEY_ERROR))
    }

    @Test
    fun `error data supplies fallback for blank exception message`() {
        val empty = DownloadWorkData.error("")
        val missing = DownloadWorkData.error(null)

        assertEquals(
            "The background download failed.",
            empty.getString(DownloadWorkData.KEY_ERROR)
        )
        assertEquals(
            "The background download failed.",
            missing.getString(DownloadWorkData.KEY_ERROR)
        )
    }

    @Test
    fun `image kind round trips through direct request`() {
        val media = completeMedia(MediaBackend.DIRECT).copy(kind = MediaKind.IMAGE)

        val decoded = DownloadWorkData.decode(
            DownloadWorkData.directRequest(media, 0, SOURCE_URL)
        )!!

        assertEquals(MediaKind.IMAGE, decoded.media.kind)
        assertFalse(decoded.media.isVideo)
    }

    private fun minimumData(): Data.Builder = Data.Builder()
        .putString(DownloadWorkData.KEY_MEDIA_URL, MEDIA_URL)
        .putString(DownloadWorkData.KEY_SOURCE_URL, SOURCE_URL)

    private fun completeMedia(backend: MediaBackend) = ResolvedMedia(
        url = MEDIA_URL,
        kind = MediaKind.VIDEO,
        thumbnailUrl = "https://cdn.example/thumb.jpg",
        width = 1920,
        height = 1080,
        fileSize = 1_234_567L,
        username = "creator",
        referer = SOURCE_URL,
        explicitBrowserSessionAuthorized = true,
        mimeType = "video/mp4",
        fileExtension = "mp4",
        backend = backend,
        title = "Example clip",
        sourceTimestampMillis = 1_700_000_000_000L
    )

    private companion object {
        const val MEDIA_URL = "https://cdn.example/media.mp4"
        const val SOURCE_URL = "https://example.com/post/1"
    }
}
