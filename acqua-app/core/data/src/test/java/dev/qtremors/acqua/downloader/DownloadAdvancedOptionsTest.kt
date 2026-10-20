package dev.qtremors.acqua.downloader

import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import org.junit.Assert.*
import org.junit.Test

class DownloadAdvancedOptionsTest {
    @Test fun `artwork commands apply only to enabled audio output and retain square crop encoding`() {
        for (type in DownloadContentType.entries) for (embed in listOf(false, true)) for (crop in listOf(false, true)) {
            val args = YtDlpPostProcessing.options(YtDlpDownloadOptions(contentType = type, embedThumbnail = embed, cropArtwork = crop))
            val enabled = type == DownloadContentType.AUDIO && embed
            assertEquals(enabled, args.any { it.first == "--embed-thumbnail" })
            assertEquals(if (enabled) { if (crop) "png" else "jpg" } else null,
                args.firstOrNull { it.first == "--convert-thumbnails" }?.second)
        }
    }

    private val media = ResolvedMedia("https://example.com/video", MediaKind.VIDEO, width = 1920, height = 1080)
    @Test fun `separate thumbnails never change audio or video processing arguments`() {
        for (type in DownloadContentType.entries) for (embed in listOf(false, true)) for (crop in listOf(false, true)) {
            val options = YtDlpDownloadOptions(contentType = type, embedThumbnail = embed, cropArtwork = crop)
            val alongside = options.copy(downloadThumbnail = true)
            assertEquals(DownloadAdvancedOptions.arguments(media, options), DownloadAdvancedOptions.arguments(media, alongside))
            assertEquals(YtDlpPostProcessing.options(options), YtDlpPostProcessing.options(alongside))
        }
    }
    @Test fun `exact separate streams are used without quality fallback`() {
        val args = DownloadAdvancedOptions.arguments(media, YtDlpDownloadOptions(videoFormatId = "137", videoFormatIncludesAudio = false, audioFormatId = "140"))
        assertEquals("137+140", args.first { it.first == "-f" }.second)
    }
    @Test fun `exact combined stream does not gain another audio track`() {
        val args = DownloadAdvancedOptions.arguments(media, YtDlpDownloadOptions(videoFormatId = "18"))
        assertEquals("18", args.first().second)
    }
    @Test fun `video only choice selects accompanying audio when automatic`() {
        assertEquals("137+bestaudio", DownloadAdvancedOptions.arguments(media,
            YtDlpDownloadOptions(videoFormatId = "137", videoFormatIncludesAudio = false)).first().second)
    }
    @Test fun `audio stream and portrait quality remain explicit`() {
        assertEquals("bestvideo[width<=?720]+140", DownloadAdvancedOptions.arguments(media.copy(width = 1080, height = 1920),
            YtDlpDownloadOptions(audioFormatId = "140", maximumVideoHeight = 720)).first().second)
    }
    @Test fun `automatic captions conversion and embedding reach the processing arguments`() {
        val args = DownloadAdvancedOptions.arguments(media, YtDlpDownloadOptions(captionMode = CaptionMode.BOTH,
            captionFormat = CaptionFormat.SRT, captionLanguages = "en,hi,en", automaticCaptions = true, videoContainer = VideoContainer.MKV))
        assertTrue(args.contains("--sub-langs" to "en,hi"))
        assertTrue(args.contains("--write-subs" to null))
        assertTrue(args.contains("--write-auto-subs" to null))
        assertTrue(args.contains("--embed-subs" to null))
        assertTrue(args.contains("--convert-subs" to "srt"))
        assertTrue(args.contains("--remux-video" to "mkv"))
    }
    @Test fun `separate captions never embed`() {
        assertFalse(DownloadAdvancedOptions.arguments(media, YtDlpDownloadOptions(captionMode = CaptionMode.SEPARATE))
            .any { it.first == "--embed-subs" })
    }
    @Test fun `automatic container supports embedding without dropping captions`() {
        assertTrue(DownloadAdvancedOptions.arguments(media, YtDlpDownloadOptions(captionMode = CaptionMode.EMBED))
            .contains("--remux-video" to "mkv"))
    }
    @Test fun `incompatible caption embedding is rejected before execution`() {
        assertTrue(runCatching { DownloadAdvancedOptions.arguments(media,
            YtDlpDownloadOptions(captionMode = CaptionMode.BOTH, captionFormat = CaptionFormat.SRT, videoContainer = VideoContainer.WEBM)) }.isFailure)
        assertTrue(DownloadAdvancedOptions.arguments(media,
            YtDlpDownloadOptions(captionMode = CaptionMode.BOTH, captionFormat = CaptionFormat.VTT, videoContainer = VideoContainer.WEBM))
            .contains("--convert-subs" to "vtt"))
    }
    @Test fun `collection selection uses a stable entry URL when available`() {
        val selected = DownloadSourceSelection.from(media.copy(collectionUrl = "https://example.com/list", collectionIndex = 4))
        assertEquals(media.url, selected.url)
        assertNull(selected.collectionIndex)
        val fallback = DownloadSourceSelection.from(media.copy(url = "https://example.com/list",
            collectionUrl = "https://example.com/list", collectionIndex = 4))
        assertEquals(4, fallback.collectionIndex)
    }
    @Test fun `invalid stream expressions and language expressions are rejected`() {
        listOf("best/18", "18+140", "--help", "").forEach { id ->
            assertTrue(runCatching { DownloadAdvancedOptions.arguments(media, YtDlpDownloadOptions(videoFormatId = id)) }.isFailure)
        }
        assertTrue(runCatching { DownloadAdvancedOptions.arguments(media, YtDlpDownloadOptions(captionMode = CaptionMode.BOTH, captionLanguages = "en;echo")) }.isFailure)
    }
}
