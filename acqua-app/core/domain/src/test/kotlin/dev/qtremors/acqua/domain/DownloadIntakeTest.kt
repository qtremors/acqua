package dev.qtremors.acqua.domain

import org.junit.Assert.*
import org.junit.Test

class DownloadIntakeTest {
    @Test fun `multiple pasted links keep order and remove duplicates`() {
        assertEquals(listOf("https://a.example/video", "https://b.example/audio"),
            WebLink.extractAll("First https://a.example/video, then https://b.example/audio. https://a.example/video"))
    }
    @Test fun `batch extraction rejects embedded credentials`() {
        assertEquals(listOf("https://safe.example/file"), WebLink.extractAll("https://user:secret@private.example/file https://safe.example/file"))
    }
    @Test fun `unsupported schemes never turn into web download links`() {
        assertTrue(WebLink.extractAll("ftp://files.example/video").isEmpty())
        assertTrue(WebLink.extractAll("file:///private/video").isEmpty())
    }
    @Test fun `inclusive entry ranges preserve all requested entries`() {
        assertEquals(setOf(0, 2, 3, 4), CollectionSelection.parse("1, 3-5, 3", 6))
    }
    @Test fun `invalid ranges never become a partial selection`() {
        listOf("", "0", "7", "5-3", "1,abc", "1-7", "-1", "1-2-3", "1,").forEach {
            assertNull(it, CollectionSelection.parse(it, 6))
        }
    }
}
