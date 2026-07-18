package dev.qtremors.acqua.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MediaResolutionServiceTest {
    @Test
    fun `known source is resolved and inspected`() = runBlocking {
        val raw = ResolvedMedia("https://cdn.test/photo", MediaKind.IMAGE)
        val service = MediaResolutionService(MediaResolver { listOf(raw) }) {
            it.copy(mimeType = "image/png", fileExtension = "png")
        }

        val result = service.resolve("https://instagram.com/p/abc", false) { emptyList() }

        assertEquals(
            listOf(raw.copy(referer = "https://instagram.com/p/abc", mimeType = "image/png", fileExtension = "png")),
            result
        )
    }

    @Test
    fun `generic pages require browser extraction`() {
        val service = MediaResolutionService(MediaResolver { emptyList() }) { it }

        assertThrows(IllegalStateException::class.java) {
            runBlocking { service.resolve("https://example.com/page", false) { emptyList() } }
        }
    }

    @Test
    fun `browser resolver is used after known source failure`() = runBlocking {
        val browserItem = ResolvedMedia("https://cdn.test/video.mp4", MediaKind.VIDEO)
        val service = MediaResolutionService(MediaResolver { error("network unavailable") }) { it }

        val result = service.resolve("https://instagram.com/p/abc", true) { listOf(browserItem) }

        assertEquals(listOf(browserItem), result)
    }

    @Test
    fun `largest complete reel variant is selected`() = runBlocking {
        val small = ResolvedMedia("https://cdn.test/small.mp4", MediaKind.VIDEO, width = 480, height = 854, fileSize = 1_000)
        val large = ResolvedMedia("https://cdn.test/large.mp4", MediaKind.VIDEO, width = 1080, height = 1920, fileSize = 2_000)
        val service = MediaResolutionService(MediaResolver { listOf(small, large) }) { it }

        val result = service.resolve("https://instagram.com/reel/abc", false) { emptyList() }

        assertEquals(listOf(large.copy(referer = "https://instagram.com/reel/abc")), result)
    }
}
