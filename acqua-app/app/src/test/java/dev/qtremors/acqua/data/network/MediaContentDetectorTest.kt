package dev.qtremors.acqua.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaContentDetectorTest {
    @Test
    fun `complete mp4 is detected as video`() {
        val prefix = byteArrayOf(
            0, 0, 0, 24,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte()
        )

        assertEquals(
            DetectedMediaFormat.MP4,
            MediaContentDetector.detect("video/mp4", prefix, expectedVideo = true)
        )
    }

    @Test
    fun `poster jpeg stays an image even when video was expected`() {
        val prefix = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())

        assertEquals(
            DetectedMediaFormat.JPEG,
            MediaContentDetector.detect("image/jpeg", prefix, expectedVideo = true)
        )
    }

    @Test
    fun `partial mp4 transport segment is rejected`() {
        val middleOfFile = "moof fragment without a file header".toByteArray()

        assertNull(MediaContentDetector.detect("video/mp4", middleOfFile, expectedVideo = true))
    }

    @Test
    fun `audio mp4 is rejected as downloadable video`() {
        val prefix = byteArrayOf(
            0, 0, 0, 24,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte()
        )

        assertNull(MediaContentDetector.detect("audio/mp4", prefix, expectedVideo = true))
    }

    @Test
    fun `png keeps its file format`() {
        val prefix = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )

        assertEquals(
            DetectedMediaFormat.PNG,
            MediaContentDetector.detect("image/png", prefix, expectedVideo = false)
        )
    }

    @Test
    fun `webp keeps its file format`() {
        val prefix = "RIFF0000WEBP".toByteArray()

        assertEquals(
            DetectedMediaFormat.WEBP,
            MediaContentDetector.detect("image/webp", prefix, expectedVideo = false)
        )
    }
}
