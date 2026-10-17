package dev.qtremors.acqua.downloader

import dev.qtremors.acqua.domain.MediaFormatOption
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadStreamsTest {
    private val video = MediaFormatOption("137", "mp4", "avc1.640028", "none", 1080, 1920, 30)
    private val english = MediaFormatOption("140", "m4a", "none", "mp4a.40.2", audioBitrateKbps = 128, language = "en", audioChannels = 2)
    private val hindi = english.copy(id = "141", language = "hi")
    private val media = ResolvedMedia("https://example.com/video", MediaKind.VIDEO, width = 1080, height = 1920,
        sourceIdentity = "source:id", formats = listOf(video, english, hindi))
    private val choices = YtDlpDownloadOptions(videoFormatId = "137", videoFormatIncludesAudio = false, audioFormatId = "140", additionalAudioFormatIds = setOf("141"))

    @Test fun `separate and combined selections retain every chosen audio track in MKV`() {
        val args = DownloadAdvancedOptions.arguments(media, choices)
        assertEquals("137+140+141", args.first { it.first == "-f" }.second)
        assertTrue(args.contains("--audio-multistreams" to null))
        assertTrue(args.contains("--remux-video" to "mkv"))
        assertEquals("18+141", DownloadAdvancedOptions.arguments(media, choices.copy(videoFormatId = "18", videoFormatIncludesAudio = true))
            .first { it.first == "-f" }.second)
        assertEquals("bv[width<=?720]+140+141", DownloadAdvancedOptions.arguments(media, choices.copy(videoFormatId = null, maximumVideoHeight = 720))
            .first { it.first == "-f" }.second)
        assertThrows(IllegalArgumentException::class.java) { DownloadAdvancedOptions.arguments(media, choices.copy(videoContainer = VideoContainer.WEBM)) }
    }

    @Test fun `silent output selects only a video-only source and rejects contradictory audio choices`() {
        val silent = YtDlpDownloadOptions(silentVideo = true, maximumVideoHeight = 720)
        assertEquals("bv[width<=?720]", DownloadAdvancedOptions.arguments(media, silent).first { it.first == "-f" }.second)
        assertFalse(DownloadAdvancedOptions.arguments(media, silent).any { it.first == "--audio-multistreams" })
        assertThrows(IllegalArgumentException::class.java) { DownloadAdvancedOptions.arguments(media, choices.copy(silentVideo = true)) }
        assertThrows(IllegalArgumentException::class.java) { DownloadAdvancedOptions.arguments(media, silent.copy(videoFormatId = "18")) }
        assertThrows(IllegalArgumentException::class.java) { DownloadAdvancedOptions.arguments(media, silent.copy(contentType = DownloadContentType.AUDIO)) }
    }

    @Test fun `accepted signatures and selected tracks round trip without sensitive stream URLs`() {
        val accepted = DownloadStreams.accepted(media, choices)
        assertEquals(media.formats, accepted.acceptedStreams)
        val restored = DownloadOptionsCodec.decode(DownloadOptionsCodec.encode(accepted), YtDlpDownloadOptions())
        assertEquals(accepted, restored)
        assertEquals("en", restored.acceptedStreams[1].language)
        assertEquals(2, restored.acceptedStreams[1].audioChannels)
        assertTrue(DownloadOptionsCodec.encode(accepted).contains("avc1.640028"))
        for (id in listOf("18+140", "best/18", "--exec", "x".repeat(101))) {
            assertThrows(IllegalArgumentException::class.java) { DownloadStreams.validate(choices.copy(additionalAudioFormatIds = setOf(id))) }
        }
    }

    @Test fun `remembered media defaults clear source-specific tracks and signatures`() {
        val settings = dev.qtremors.acqua.data.settings.AppSettingsRepository(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        settings.rememberDownloadSelection(MediaKind.VIDEO, dev.qtremors.acqua.data.settings.LastDownloadSelection(
            dev.qtremors.acqua.domain.DownloadEngine.YT_DLP, DownloadStreams.accepted(media, choices)))
        val remembered = settings.lastDownloadSelection(MediaKind.VIDEO)!!.options
        assertNull(remembered.videoFormatId)
        assertNull(remembered.audioFormatId)
        assertTrue(remembered.additionalAudioFormatIds.isEmpty())
        assertTrue(remembered.acceptedStreams.isEmpty())
    }

    @Test fun `reinspection rebinds only unique equivalent codec frame-rate language and channel choices`() {
        val accepted = DownloadStreams.accepted(media, choices)
        val fresh = media.copy(formats = listOf(video.copy(id = "vNew"), english.copy(id = "aNew"), hindi.copy(id = "bNew")))
        val rebound = DownloadStreams.rebound(fresh, accepted)
        assertEquals("vNew", rebound.videoFormatId)
        assertEquals("aNew", rebound.audioFormatId)
        assertEquals(setOf("bNew"), rebound.additionalAudioFormatIds)
        val invalid = listOf(video.copy(id = "vNew", fps = 60), video.copy(id = "vNew", videoCodec = "av01"))
        invalid.forEach { changed -> assertThrows(SourceStreamsChangedException::class.java) {
            DownloadStreams.rebound(fresh.copy(formats = listOf(changed, fresh.formats[1], fresh.formats[2])), accepted)
        } }
        assertThrows(SourceStreamsChangedException::class.java) { DownloadStreams.rebound(fresh.copy(formats = fresh.formats + video.copy(id = "ambiguous")), accepted) }
        assertThrows(SourceStreamsChangedException::class.java) { DownloadStreams.rebound(fresh.copy(formats = fresh.formats.map { it.copy(audioChannels = 6) }), accepted) }
        assertThrows(SourceStreamsChangedException::class.java) { DownloadStreams.rebound(fresh, choices) }
    }

    @Test fun `expired stream recovery reinspects once while retaining accepted processing choices`() {
        val accepted = DownloadStreams.accepted(media, choices.copy(captionMode = CaptionMode.BOTH, recordHistory = false))
        var inspections = 0
        var attempts = 0
        val result = DownloadStreams.recover(media, accepted, inspect = { inspections++; media.copy(formats = listOf(video, english.copy(id = "fresh"), hindi)) }) { _, options ->
            if (++attempts == 1) throw java.io.IOException("HTTP Error 403: expired")
            assertEquals("fresh", options.audioFormatId)
            assertEquals(CaptionMode.BOTH, options.captionMode)
            assertFalse(options.recordHistory)
            "complete"
        }
        assertEquals("complete", result)
        assertEquals(1, inspections)
        assertEquals(2, attempts)
        assertThrows(SourceStreamsChangedException::class.java) {
            DownloadStreams.recover(media, accepted, inspect = { media.copy(sourceIdentity = "another:id") }) { _, _ -> throw java.io.IOException("HTTP Error 410") }
        }
    }

    @Test fun `cancellation unrelated errors and a second expiration never cause repeated inspections`() {
        var inspections = 0
        for (failure in listOf(java.util.concurrent.CancellationException(), java.io.IOException("Storage is full"))) {
            assertThrows(failure.javaClass) { DownloadStreams.recover(media, choices, inspect = { inspections++; media }) { _, _ -> throw failure } }
        }
        assertEquals(0, inspections)
        var attempts = 0
        assertThrows(java.io.IOException::class.java) {
            DownloadStreams.recover(media, DownloadStreams.accepted(media, choices), inspect = { inspections++; media }) { _, _ -> attempts++; throw java.io.IOException("requested format is not available") }
        }
        assertEquals(1, inspections)
        assertEquals(2, attempts)
    }
}
