package dev.qtremors.acqua.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaContentDetectorTest {
    @Test
    fun `complete mp4 is detected as video`() {
        val prefix = byteArrayOf(
            0, 0, 0, 16,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0, 0, 0, 0,
            0, 0, 0, 8,
            'm'.code.toByte(), 'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte()
        )

        assertEquals(
            DetectedMediaFormat.MP4,
            MediaContentDetector.detect("video/mp4", prefix, expectedVideo = true)
        )
    }

    @Test
    fun `truncated ftyp box is rejected`() {
        val prefix = byteArrayOf(
            0, 0, 0, 32,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte()
        )

        assertNull(MediaContentDetector.detect("video/mp4", prefix, expectedVideo = true))
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

    @Test
    fun `audio mp4 is detected as audio when video is not expected`() {
        val prefix = byteArrayOf(
            0, 0, 0, 16,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'M'.code.toByte(), '4'.code.toByte(), 'A'.code.toByte(), ' '.code.toByte(),
            0, 0, 0, 0,
            0, 0, 0, 8,
            'm'.code.toByte(), 'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte()
        )

        assertEquals(
            DetectedMediaFormat.M4A,
            MediaContentDetector.detect("audio/mp4", prefix, expectedVideo = false)
        )
    }

    @Test
    fun `mp3 with id3 is detected as audio`() {
        val prefix = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0)

        assertEquals(
            DetectedMediaFormat.MP3,
            MediaContentDetector.detect("audio/mpeg", prefix, expectedVideo = false)
        )
    }

    @Test
    fun `generic mp4 with video mime stays video when inspecting an image candidate`() {
        for (brand in listOf("isom", "mp42")) {
            val prefix = byteArrayOf(0, 0, 0, 16) + "ftyp$brand".toByteArray() +
                byteArrayOf(0, 0, 0, 0, 0, 0, 0, 8) + "mdat".toByteArray()
            assertEquals(DetectedMediaFormat.MP4, MediaContentDetector.detect("video/mp4", prefix, false))
            assertNull(MediaContentDetector.detect("application/octet-stream", prefix, false))
            assertEquals(DetectedMediaFormat.M4A, MediaContentDetector.detect("audio/mp4", prefix, false))
        }
    }

    @Test
    fun `aac adts is not confused with mpeg frames`() {
        for (secondByte in listOf(0xF1, 0xF9)) {
            val prefix = byteArrayOf(0xFF.toByte(), secondByte.toByte(), 0x50, 0x80.toByte(), 0, 0xFF.toByte(), 0xFC.toByte())
            assertEquals(DetectedMediaFormat.AAC, MediaContentDetector.detect("audio/aac", prefix, false))
            assertEquals(DetectedMediaFormat.AAC, MediaContentDetector.detect(null, prefix, false))
        }
        val mp3 = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0)
        assertEquals(DetectedMediaFormat.MP3, MediaContentDetector.detect("audio/mpeg", mp3, false))
    }

    @Test
    fun `audio mime alone cannot validate an error page or truncated container`() {
        for (mime in listOf("audio/mpeg", "audio/mp4", "audio/aac", "audio/unknown")) {
            assertNull(MediaContentDetector.detect(mime, "<html>error</html>".toByteArray(), false))
        }
        val truncated = byteArrayOf(0, 0, 0, 32) + "ftypM4A ".toByteArray()
        assertNull(MediaContentDetector.detect("audio/mp4", truncated, false))
    }
}
