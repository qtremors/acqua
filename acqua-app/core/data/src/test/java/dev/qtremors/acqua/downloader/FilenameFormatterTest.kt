package dev.qtremors.acqua.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.util.Date

class FilenameFormatterTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `source collection chapter and UTC upload date expressions support literal braces and fallbacks`() {
        val context = FilenameContext("abc", "Source", "Playlist", 3, 4, uploadTimestampMillis = 1_704_067_200_000L)
        val name = FilenameFormatter.format(
            "{{literal}}_{source_id}_{extractor}_{collection}_{collection_index}_{collection_count}_{chapter|Whole}_{upload_date}",
            null, 0, 0, 0, "mp4", context = context)
        assertEquals("{literal}_abc_Source_Playlist_3_4_Whole_20240101.mp4", name)
        assertEquals("{date}_Unknown.mp3", FilenameFormatter.format("{title}_{artist|Unknown}", null, 0, 0, 0, "mp3", title = "{date}"))
        assertEquals("{unknown}_{unfinished.mp4", FilenameFormatter.format("{unknown}_{unfinished", null, 0, 0, 0, "mp4"))
    }

    @Test fun `long ASCII multibyte and emoji names reserve space for sidecars extensions and collisions`() {
        for (title in listOf("a".repeat(1000), "測試".repeat(1000), "😀".repeat(1000))) {
            val name = FilenameFormatter.format("{title}", null, 0, 0, 0, "mp4", title = title)
            val sidecar = FilenameFormatter.withArtifactSuffix(name, "." + "language".repeat(20), "srt")
            val collision = FilenameFormatter.withCollisionSuffix(sidecar, Int.MAX_VALUE)
            for (result in listOf(name, sidecar, collision)) {
                assertTrue(result.toByteArray(Charsets.UTF_8).size <= FilenameFormatter.MAX_FILENAME_BYTES)
                assertEquals(result, String(result.toByteArray(Charsets.UTF_8), Charsets.UTF_8))
                assertFalse(result.contains('\uFFFD'))
                java.io.File(temporary.root, result).writeText("fixture")
            }
            assertTrue(name.endsWith(".mp4"))
            assertTrue(collision.endsWith(" (${Int.MAX_VALUE}).srt"))
            assertTrue(sidecar.startsWith(name.substringBeforeLast('.')))
        }
    }

    @Test fun `collisions keep every original file and output names sanitize malicious context`() {
        val name = FilenameFormatter.format("{source_id}_{collection}_{chapter}", null, 0, 0, 0, "mp4",
            context = FilenameContext(sourceId = "../id", collection = "a/b", chapter = "bad\u0000:chapter"))
        assertFalse(name.contains('/'))
        assertFalse(name.contains('\u0000'))
        val first = java.io.File(temporary.root, name).apply { writeText("first") }
        val second = java.io.File(temporary.root, FilenameFormatter.withCollisionSuffix(name, 1)).apply { writeText("second") }
        assertEquals("first", first.readText())
        assertEquals("second", second.readText())
        assertFalse(first == second)
        assertTrue(FilenameFormatter.withCollisionSuffix("name." + "x".repeat(1000), 1).toByteArray().size <= FilenameFormatter.MAX_FILENAME_BYTES)
    }
    @Test
    fun `only collections include filename indexes`() {
        fun filename(count: Int, index: Int) = FilenameFormatter.format(
            pattern = "{title}_{index}", username = null, width = 0, height = 0,
            index = index, fileExtension = "jpg", title = "Photo", itemCount = count
        )
        assertEquals("Photo.jpg", filename(1, 0))
        assertEquals("Photo_1.jpg", filename(3, 0))
        assertEquals("Photo_3.jpg", filename(3, 2))
    }

    @Test
    fun `default filename does not add an Acqua prefix`() {
        val name = FilenameFormatter.format(
            pattern = FilenameFormatter.DEFAULT_PATTERN,
            username = "creator",
            width = 1080,
            height = 1920,
            index = 0,
            fileExtension = "jpg",
            now = Date(0)
        )

        assertEquals(false, name.startsWith("acqua_", ignoreCase = true))
        assertTrue(name.startsWith("creator_1080x1920_"))
    }

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

        assertEquals("acqua_owner.png", name)
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

    @Test
    fun `preview renders a stable representative filename`() {
        val preview = FilenameFormatter.preview("{title}_{username}_{resolution}_{date}_{time}_{index}")
        assertTrue(preview.startsWith("Sample video_creator_1080x1920_20240101_"))
        assertTrue(preview.endsWith("_1.mp4"))
    }

    @Test
    fun `collision suffix is inserted before extension`() {
        assertEquals("photo (2).jpg", FilenameFormatter.withCollisionSuffix("photo.jpg", 2))
        assertEquals("download (1)", FilenameFormatter.withCollisionSuffix("download", 1))
    }

    @Test
    fun `default audio filename formats with title only`() {
        val name = FilenameFormatter.format(
            pattern = FilenameFormatter.DEFAULT_AUDIO_PATTERN,
            username = "band_channel",
            width = 0,
            height = 0,
            index = 0,
            fileExtension = "mp3",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera",
            now = Date(0)
        )

        assertEquals("Bohemian Rhapsody.mp3", name)
    }

    @Test
    fun `audio filename supports artist and album tokens`() {
        val name = FilenameFormatter.format(
            pattern = "{artist} - {album} - {title}",
            username = "band_channel",
            width = 0,
            height = 0,
            index = 0,
            fileExtension = "flac",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera",
            now = Date(0)
        )

        assertEquals("Queen - A Night at the Opera - Bohemian Rhapsody.flac", name)
    }

    @Test
    fun `audio filename falls back when title is missing or blank`() {
        val name = FilenameFormatter.format(
            pattern = FilenameFormatter.DEFAULT_AUDIO_PATTERN,
            username = "band_channel",
            width = 0,
            height = 0,
            index = 0,
            fileExtension = "mp3",
            title = "",
            artist = "Queen",
            now = Date(0)
        )

        assertEquals("Queen.mp3", name)
    }

    @Test
    fun `previewAudio renders stable audio filename`() {
        val preview = FilenameFormatter.previewAudio("{artist} - {title}")
        assertEquals("Sample artist - Sample song.mp3", preview)
    }

    @Test
    fun `toggleAudioVariable toggles variables in pattern`() {
        val pattern = "{title}"
        val withArtist = FilenameFormatter.toggleAudioVariable(pattern, "{artist}")
        assertEquals("{title}_{artist}", withArtist)
        val withoutArtist = FilenameFormatter.toggleAudioVariable(withArtist, "{artist}")
        assertEquals("{title}", withoutArtist)
    }

    @Test
    fun `sanitized empty title falls back to artist then uploader`() {
        fun filename(title: String, artist: String?, username: String?) = FilenameFormatter.format(
            pattern = "{title}", username = username, width = 0, height = 0,
            index = 0, fileExtension = "mp3", title = title, artist = artist, now = Date(0)
        )
        for (title in listOf("...", "/:*?", "\u0001\u200B")) {
            assertEquals("Artist.mp3", filename(title, "Artist", "Uploader"))
            assertEquals("Uploader.mp3", filename(title, "...", "Uploader"))
            val generated = filename(title, "...", "/")
            assertTrue(generated.startsWith("download_"))
            assertTrue(Regex("download_[0-9]{8}_[0-9]{6}\\.mp3").matches(generated))
        }
    }
}
