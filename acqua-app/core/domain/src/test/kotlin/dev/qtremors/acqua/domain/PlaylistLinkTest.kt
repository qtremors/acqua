package dev.qtremors.acqua.domain

import org.junit.Assert.*
import org.junit.Test

class PlaylistLinkTest {
    @Test fun `video scope removes playlist parameters and retains timestamps`() {
        val url = "https://www.youtube.com/watch?v=abcdefghijk&list=PL123&index=2&t=60&start_radio=1"
        assertTrue(PlaylistLink.isPlaylist(url))
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk&t=60", PlaylistLink.videoUrl(url))
        assertEquals("https://youtu.be/abcdefghijk?t=2", PlaylistLink.videoUrl("https://youtu.be/abcdefghijk?list=PL123&t=2"))
        assertEquals("https://music.youtube.com/watch?v=abcdefghijk", PlaylistLink.videoUrl("https://music.youtube.com/watch?v=abcdefghijk&list=PL123"))
    }
    @Test fun `playlist only links cannot turn into an individual video and unrelated hosts are ignored`() {
        assertTrue(PlaylistLink.isPlaylist("https://www.youtube.com/playlist?list=PL123"))
        assertNull(PlaylistLink.videoUrl("https://www.youtube.com/playlist?list=PL123"))
        assertFalse(PlaylistLink.isPlaylist("https://youtube.com.example.org/watch?v=abc&list=123"))
        assertFalse(PlaylistLink.isPlaylist("https://www.youtube.com/watch?v=abc&list="))
    }
}
