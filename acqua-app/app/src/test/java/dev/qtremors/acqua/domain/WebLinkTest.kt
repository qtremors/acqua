package dev.qtremors.acqua.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebLinkTest {
    @Test
    fun acceptsAnyHttpOrHttpsWebsite() {
        assertEquals("https://example.com/media", WebLink.normalize("example.com/media"))
        assertEquals("http://example.com/photo", WebLink.normalize("http://example.com/photo"))
    }

    @Test
    fun extractsFirstSharedWebLink() {
        assertEquals(
            "https://example.com/post/1",
            WebLink.extractFirst("Take a look https://example.com/post/1, thanks")
        )
    }

    @Test
    fun rejectsUnsafeOrIncompleteLinks() {
        assertNull(WebLink.normalize("javascript:alert(1)"))
        assertNull(WebLink.normalize("https://"))
        assertNull(WebLink.normalize("https://user@example.com/private"))
    }

    @Test
    fun recognizesInstagramWithoutBlockingOtherWebsites() {
        assertTrue(WebLink.isInstagramMediaUrl("https://www.instagram.com/reel/ABC123/"))
        assertFalse(WebLink.isInstagramMediaUrl("https://example.com/reel/ABC123/"))
        assertEquals("example.com", WebLink.host("https://example.com/reel/ABC123/"))
    }

    @Test
    fun recognizesYouTubeAndYouTubeMusicHosts() {
        assertTrue(WebLink.isYouTubeUrl("https://youtu.be/abc"))
        assertTrue(WebLink.isYouTubeUrl("https://www.youtube.com/watch?v=abc"))
        assertTrue(WebLink.isYouTubeMusicUrl("https://music.youtube.com/watch?v=abc"))
        assertFalse(WebLink.isYouTubeUrl("https://example.com/watch?v=abc"))
    }
}
