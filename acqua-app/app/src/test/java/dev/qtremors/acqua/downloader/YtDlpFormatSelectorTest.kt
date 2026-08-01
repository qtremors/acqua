package dev.qtremors.acqua.downloader

import dev.qtremors.acqua.domain.MediaFormatOption
import org.junit.Assert.assertEquals
import org.junit.Test

class YtDlpFormatSelectorTest {
    @Test
    fun `best video selector allows separate video and audio streams`() {
        assertEquals("bv*+ba/b", YtDlpFormatSelector.video(0, portrait = false))
    }

    @Test
    fun `height limited selector caps separate and progressive video streams`() {
        assertEquals(
            "bv*[height<=?1080]+ba/b[height<=?1080]",
            YtDlpFormatSelector.video(1080, portrait = false)
        )
    }

    @Test
    fun `portrait selector caps width as the quality dimension`() {
        assertEquals(
            "bv*[width<=?1080]+ba/b[width<=?1080]",
            YtDlpFormatSelector.video(1080, portrait = true)
        )
    }

    @Test
    fun `audio selector chooses the best available audio`() {
        assertEquals("ba/b", YtDlpFormatSelector.AUDIO)
    }

    @Test
    fun `progress parses exact and estimated download sizes`() {
        val exact = YtDlpProgress.fromCallback(
            25f, 12L, "[download]  25.0% of 20.00MiB at 1.00MiB/s ETA 00:12"
        )
        val estimated = YtDlpProgress.fromCallback(
            50f, 5L, "[download]  50.0% of ~ 2.00GiB at 5.00MiB/s ETA 00:05"
        )

        assertEquals(5L shl 20, exact.downloadedBytes)
        assertEquals(20L shl 20, exact.totalBytes)
        assertEquals(1L shl 30, estimated.downloadedBytes)
        assertEquals(2L shl 30, estimated.totalBytes)
    }

    @Test
    fun `selected format metadata follows the configured height cap`() {
        val formats = listOf(
            MediaFormatOption("720", "mp4", videoCodec = "avc1", width = 1280, height = 720),
            MediaFormatOption("1080", "mp4", videoCodec = "avc1", width = 1920, height = 1080),
            MediaFormatOption("2160", "webm", videoCodec = "vp9", width = 3840, height = 2160)
        )

        assertEquals("1080", YtDlpFormatSelector.selectedVideoFormat(formats, 1080)?.id)
        assertEquals("2160", YtDlpFormatSelector.selectedVideoFormat(formats, 0)?.id)
    }

    @Test
    fun `portrait formats use their short edge as the quality label`() {
        val portrait = listOf(
            MediaFormatOption("720", "mp4", videoCodec = "avc1", width = 720, height = 1280),
            MediaFormatOption("1080", "mp4", videoCodec = "avc1", width = 1080, height = 1920),
            MediaFormatOption("2160", "webm", videoCodec = "vp9", width = 2160, height = 3840)
        )

        assertEquals("1080", YtDlpFormatSelector.selectedVideoFormat(portrait, 1080)?.id)
    }
}
