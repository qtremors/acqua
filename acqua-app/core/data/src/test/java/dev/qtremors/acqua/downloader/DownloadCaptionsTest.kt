package dev.qtremors.acqua.downloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadCaptionsTest {
    private val media = ResolvedMedia("https://example.com/video", MediaKind.VIDEO)

    @Test fun `audio exports every caption format while embedding requires a compatible container`() {
        for (audio in AudioOutputFormat.entries) for (format in CaptionFormat.entries) {
            val options = YtDlpDownloadOptions(contentType = DownloadContentType.AUDIO, audioFormat = audio, captionMode = CaptionMode.SEPARATE, captionFormat = format)
            val args = DownloadAdvancedOptions.arguments(media, options)
            assertTrue(args.contains("--write-subs" to null))
            assertFalse(args.any { it.first == "--embed-subs" })
            assertEquals(options, DownloadOptionsCodec.decode(DownloadOptionsCodec.encode(options), YtDlpDownloadOptions()))
        }
        val embed = YtDlpDownloadOptions(contentType = DownloadContentType.AUDIO, audioFormat = AudioOutputFormat.M4A, captionMode = CaptionMode.BOTH, captionFormat = CaptionFormat.SRT)
        assertTrue(DownloadAdvancedOptions.arguments(media, embed).contains("--embed-subs" to null))
        assertThrows(IllegalArgumentException::class.java) { DownloadAdvancedOptions.arguments(media, embed.copy(audioFormat = AudioOutputFormat.MP3)) }
        assertThrows(IllegalArgumentException::class.java) { DownloadAdvancedOptions.arguments(media, embed.copy(captionFormat = CaptionFormat.LRC)) }
    }

    @Test fun `container validation and normalized choices never drop requested exports`() {
        val ass = YtDlpDownloadOptions(captionMode = CaptionMode.BOTH, captionFormat = CaptionFormat.ASS)
        assertTrue(DownloadAdvancedOptions.arguments(media, ass).contains("--remux-video" to "mkv"))
        assertThrows(IllegalArgumentException::class.java) { DownloadAdvancedOptions.arguments(media, ass.copy(videoContainer = VideoContainer.MP4)) }
        assertEquals(CaptionFormat.SRT, DownloadCaptions.normalized(ass.copy(videoContainer = VideoContainer.MP4)).captionFormat)
        assertEquals(CaptionFormat.VTT, DownloadCaptions.normalized(ass.copy(videoContainer = VideoContainer.WEBM)).captionFormat)
        val audio = DownloadCaptions.normalized(ass.copy(contentType = DownloadContentType.AUDIO))
        assertEquals(CaptionMode.SEPARATE, audio.captionMode)
        assertEquals(CaptionFormat.ASS, audio.captionFormat)
    }

    @Test fun `manual automatic and translated source tracks remain distinguishable`() {
        val engine = YtDlpEngine(ApplicationProvider.getApplicationContext<Context>())
        val tracks = engine.parseInspection("""{"id":"id","title":"Fixture","url":"https://example.com/file.mp4",
            "subtitles":{"en":[{"name":"English","url":"https://example.com/en"}]},
            "automatic_captions":{"en-orig":[{"name":"English original","url":"https://example.com/en"}],
            "hi":[{"name":"Hindi","url":"https://example.com/sub?tlang=hi"}]}}""", media.url, false).single().captions
        assertEquals(3, tracks.size)
        assertFalse(tracks.first { it.language == "en" }.automatic)
        assertTrue(tracks.first { it.language == "en-orig" }.automatic)
        assertFalse(tracks.first { it.language == "en-orig" }.translated)
        assertTrue(tracks.first { it.language == "hi" }.translated)
        val args = DownloadAdvancedOptions.arguments(media, YtDlpDownloadOptions(captionMode = CaptionMode.SEPARATE, captionLanguages = "hi,en-orig", automaticCaptions = true))
        assertTrue(args.contains("--sub-langs" to "hi,en-orig"))
        assertTrue(args.contains("--write-auto-subs" to null))
    }

    @Test fun `ASS and LRC exports are accounted alongside the audio artifact exactly once`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "caption-formats").apply { mkdirs() }
        try {
            val audio = File(directory, "Audio.m4a").apply { writeText("audio") }
            File(directory, "Audio.hi.ass").writeText("captions")
            File(directory, "Audio.en.lrc").writeText("lyrics")
            val manifest = File(directory, "manifest").apply { writeText(org.json.JSONArray().put("id").put(audio.path).toString()) }
            val outputs = DownloadOutputSet.read(directory, manifest, CaptionMode.BOTH)
            assertEquals(3, outputs.artifacts.size)
            assertEquals(setOf(".hi", ".en"), outputs.artifacts.filter { it.caption }.map { it.suffix }.toSet())
            assertEquals(1, DownloadOutputSet.read(directory, manifest, CaptionMode.EMBED).artifacts.size)
        } finally { directory.deleteRecursively() }
    }
}
