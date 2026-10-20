package dev.qtremors.acqua.downloader

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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadFormatPolicyTest {
    private val media = ResolvedMedia("https://example.com/video", MediaKind.VIDEO, width = 1080, height = 1920)

    @Test fun `automatic retains selector and portrait caps with exact choices taking precedence`() {
        assertEquals("bv*[width<=?720]+ba/b[width<=?720]", selector(YtDlpDownloadOptions(maximumVideoHeight = 720)))
        val options = YtDlpDownloadOptions(formatPolicy = DownloadFormatPolicy(FormatPolicyMode.COMPATIBLE))
        assertEquals("137+140", selector(options.copy(videoFormatId = "137", videoFormatIncludesAudio = false, audioFormatId = "140")))
        assertEquals("18", selector(options.copy(videoFormatId = "18")))
        assertEquals("140", selector(options.copy(contentType = DownloadContentType.AUDIO, audioFormatId = "140")))
    }

    @Test fun `compatible and device filters cover every automatic branch`() {
        val compatible = selector(YtDlpDownloadOptions(formatPolicy = DownloadFormatPolicy(FormatPolicyMode.COMPATIBLE)))
        assertEquals(2, Regex("vcodec").findAll(compatible).count())
        assertEquals(2, Regex("acodec").findAll(compatible).count())
        val device = DownloadFormatPolicy(FormatPolicyMode.DEVICE).accepted { setOf(VideoCodecPreference.AV1, VideoCodecPreference.HEVC) }
        assertEquals(setOf(VideoCodecPreference.AV1, VideoCodecPreference.HEVC), device.deviceCodecs)
        val selector = selector(YtDlpDownloadOptions(formatPolicy = device))
        assertTrue(selector.contains("av01|av1"))
        assertTrue(selector.contains("hev|hvc|h265"))
        assertFalse(selector.contains("avc"))
        assertEquals(setOf(VideoCodecPreference.H264), device.accepted { emptySet() }.deviceCodecs)
    }

    @Test fun `custom sorting rejects commands expressions empty duplicate overflow and unknown fields`() {
        for (value in listOf("", "res;--exec", "res:720", "unknown", "res,", "res,res", "res,".repeat(11), "x".repeat(161))) {
            assertThrows(IllegalArgumentException::class.java) { DownloadFormatPolicy(customSort = value).validated() }
        }
        assertEquals("+size,res,fps", DownloadFormatPolicy(customSort = "+size, res, fps").validated().customSort)
        assertEquals(listOf("--format-sort" to "+res,+br,+fps", "--format-sort-force" to null), DownloadFormatPolicy(FormatPolicyMode.LOWEST).sortingArguments())
    }

    @Test fun `policies codecs and accepted decoder snapshots survive storage round trips`() {
        val settings = dev.qtremors.acqua.data.settings.AppSettingsRepository(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        for (mode in FormatPolicyMode.entries) for (video in VideoCodecPreference.entries) for (audio in AudioCodecPreference.entries) {
            val policy = DownloadFormatPolicy(mode, video, audio).accepted { setOf(VideoCodecPreference.VP9) }
            val options = YtDlpDownloadOptions(formatPolicy = policy)
            assertEquals(options, DownloadOptionsCodec.decode(DownloadOptionsCodec.encode(options), YtDlpDownloadOptions()))
            settings.setDownloadDefaults(options)
            assertEquals(policy.copy(deviceCodecs = emptySet()), settings.downloadDefaults().formatPolicy)
        }
    }

    @Test fun `source audio keeps source codec and remux does not add a video converter`() {
        val options = YtDlpDownloadOptions(contentType = DownloadContentType.AUDIO, formatPolicy = DownloadFormatPolicy(audioCodec = AudioCodecPreference.OPUS))
        assertEquals("ba[acodec~='^(?:opus)']/b[acodec~='^(?:opus)']", selector(options))
        assertFalse(YtDlpPostProcessing.options(options).any { it.first == "--audio-format" && it.second != "best" })
        val args = DownloadAdvancedOptions.arguments(media, options.copy(contentType = DownloadContentType.VIDEO, videoContainer = VideoContainer.MKV))
        assertTrue(args.contains("--remux-video" to "mkv"))
        assertFalse(args.any { it.first == "--recode-video" })
    }

    private fun selector(options: YtDlpDownloadOptions) = requireNotNull(DownloadAdvancedOptions.arguments(media, options).first { it.first == "-f" }.second)
}
