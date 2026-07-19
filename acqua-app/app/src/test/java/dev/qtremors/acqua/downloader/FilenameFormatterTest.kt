package dev.qtremors.acqua.downloader

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class FilenameFormatterTest {
    @Test
    fun `preserves the detected file extension`() {
        val name = FilenameFormatter.format(
            pattern = "acqua_{username}_{index}",
            username = "owner",
            width = 100,
            height = 100,
            index = 0,
            fileExtension = "png",
            now = Date(0)
        )

        assertEquals("acqua_owner_1.png", name)
    }

    @Test
    fun `removes path characters from filenames`() {
        val name = FilenameFormatter.format(
            pattern = "../{username}",
            username = "a/b",
            width = 0,
            height = 0,
            index = 0,
            fileExtension = "webp",
            now = Date(0)
        )

        assertEquals("a_b.webp", name)
    }

    @Test
    fun `supports titles for processed media`() {
        val name = FilenameFormatter.format(
            pattern = "{title}_{username}",
            username = "artist",
            width = 0,
            height = 0,
            index = 0,
            fileExtension = "m4a",
            title = "Track: One",
            now = Date(0)
        )

        assertEquals("Track_ One_artist.m4a", name)
    }
}
