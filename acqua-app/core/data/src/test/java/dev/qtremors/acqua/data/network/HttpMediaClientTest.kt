package dev.qtremors.acqua.data.network

import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertThrows
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files

class HttpMediaClientTest {
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
                HttpMediaClient().downloadToStream(item, ByteArrayOutputStream())
            }
        }
    }

    @Test
    fun `preview response within limit is returned`() {
        MockWebServer().use { server ->
            val payload = byteArrayOf(1, 2, 3, 4)
            server.enqueue(MockResponse().setBody(okio.Buffer().write(payload)))

            val actual = HttpMediaClient().fetchBytes(
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
                HttpMediaClient().fetchBytes(item)
            }
        }
    }

    @Test
    fun `preview is streamed to a bounded cache file`() {
        MockWebServer().use { server ->
            val payload = byteArrayOf(1, 2, 3, 4)
            server.enqueue(MockResponse().setBody(okio.Buffer().write(payload)))
            val directory = Files.createTempDirectory("acqua-preview-test").toFile()
            val target = java.io.File(directory, "preview")
            try {
                HttpMediaClient().fetchPreviewToFile(
                    ResolvedMedia(server.url("preview.jpg").toString(), MediaKind.IMAGE),
                    target,
                    maxBytes = payload.size.toLong()
                )

                assertArrayEquals(payload, target.readBytes())
            } finally {
                target.delete()
                directory.delete()
            }
        }
    }

    @Test
    fun `cache stream rejects a response beyond its configured limit`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("12345"))
            val directory = Files.createTempDirectory("acqua-preview-limit-test").toFile()
            val target = java.io.File(directory, "preview")
            try {
                assertThrows(IllegalStateException::class.java) {
                    HttpMediaClient().fetchPreviewToFile(
                        ResolvedMedia(server.url("preview.jpg").toString(), MediaKind.IMAGE),
                        target,
                        maxBytes = 4L
                    )
                }
                assertEquals(false, target.exists())
            } finally {
                directory.listFiles()?.forEach(java.io.File::delete)
                directory.delete()
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

            HttpMediaClient().downloadToStream(
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

    @Test
    fun `partial inspection does not mistake range length for full file size`() {
        assertNull(resolvedContentLength(206, "bytes 0-65535/*", 65_536L))
    }

    @Test
    fun `partial inspection uses total length from content range`() {
        assertEquals(7_654_321L, resolvedContentLength(206, "bytes 0-65535/7654321", 65_536L))
    }
}
