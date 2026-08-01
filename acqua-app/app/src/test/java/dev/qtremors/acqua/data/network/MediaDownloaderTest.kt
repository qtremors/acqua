package dev.qtremors.acqua.data.network

import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertThrows
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class MediaDownloaderTest {
    @Test
    fun `partial HTTP response is rejected`() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Type", "video/mp4")
                    .setBody("partial")
            )
            val item = ResolvedMedia(server.url("media.mp4").toString(), MediaKind.VIDEO)

            assertThrows(IllegalStateException::class.java) {
                MediaDownloader().downloadToStream(item, ByteArrayOutputStream())
            }
        }
    }

    @Test
    fun `preview response within limit is returned`() {
        MockWebServer().use { server ->
            val payload = byteArrayOf(1, 2, 3, 4)
            server.enqueue(MockResponse().setBody(okio.Buffer().write(payload)))

            val actual = MediaDownloader().fetchBytes(
                ResolvedMedia(server.url("preview.jpg").toString(), MediaKind.IMAGE)
            )

            assertArrayEquals(payload, actual)
        }
    }

    @Test
    fun `oversized preview declared by server is rejected`() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setBody(okio.Buffer().write(ByteArray(16 * 1024 * 1024 + 1)))
            )
            val item = ResolvedMedia(server.url("preview.jpg").toString(), MediaKind.IMAGE)

            assertThrows(IllegalStateException::class.java) {
                MediaDownloader().fetchBytes(item)
            }
        }
    }

    @Test
    fun `direct download reports final byte progress`() {
        MockWebServer().use { server ->
            val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(100)
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "image/jpeg")
                    .setBody(okio.Buffer().write(jpeg))
            )
            var downloaded = 0L
            var total = 0L

            MediaDownloader().downloadToStream(
                ResolvedMedia(server.url("photo.jpg").toString(), MediaKind.IMAGE),
                ByteArrayOutputStream()
            ) { bytesWritten, totalBytes ->
                downloaded = bytesWritten
                total = totalBytes
            }

            assertEquals(jpeg.size.toLong(), downloaded)
            assertEquals(jpeg.size.toLong(), total)
        }
    }
}
