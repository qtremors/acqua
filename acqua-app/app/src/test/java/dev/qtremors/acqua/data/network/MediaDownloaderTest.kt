package dev.qtremors.acqua.data.network

import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertThrows
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
}
