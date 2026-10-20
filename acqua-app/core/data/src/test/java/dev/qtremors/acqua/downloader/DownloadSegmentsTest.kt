package dev.qtremors.acqua.downloader

import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadSegmentsTest {
    private val media = ResolvedMedia("https://example.com/video", MediaKind.VIDEO, durationSeconds = 10)
    private val selected = setOf(SegmentCategory.SPONSOR, SegmentCategory.INTRO)

    @Test fun `disabled settings disable the service and only selected categories reach enabled requests`() {
        assertEquals(listOf("--no-sponsorblock" to null), DownloadSegments.arguments(YtDlpDownloadOptions()))
        val args = DownloadSegments.arguments(YtDlpDownloadOptions(removeSegments = selected))
        assertEquals("intro,sponsor", args.first { it.first == "--sponsorblock-remove" }.second)
        assertFalse(args.any { it.second == "all" })
        assertThrows(IllegalArgumentException::class.java) { DownloadSegments.arguments(YtDlpDownloadOptions(removeSegments = selected, splitChapters = true)) }
    }

    @Test fun `categories round trip as accepted choices and unknown imported categories are rejected`() {
        val settings = dev.qtremors.acqua.data.settings.AppSettingsRepository(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        for (categories in listOf(emptySet(), setOf(SegmentCategory.SPONSOR), selected, SegmentCategory.entries.toSet())) {
            val options = YtDlpDownloadOptions(removeSegments = categories)
            assertEquals(options, DownloadOptionsCodec.decode(DownloadOptionsCodec.encode(options), YtDlpDownloadOptions()))
            settings.setDownloadDefaults(options)
            assertEquals(categories, settings.downloadDefaults().removeSegments)
        }
        val malformed = JSONObject(DownloadOptionsCodec.encode(YtDlpDownloadOptions())).put("removeSegments", JSONArray().put("all;command"))
        assertThrows(IllegalArgumentException::class.java) { DownloadOptionsCodec.decode(malformed.toString(), YtDlpDownloadOptions()) }
    }

    @Test fun `verified ranges merge overlaps and compare the final duration`() {
        val segments = JSONArray().put(segment("sponsor", 2.0, 3.0)).put(segment("sponsor", 2.5, 4.0)).put(segment("intro", 6.0, 7.0))
        DownloadSegments.verify(report(7, segments), "", media, selected)
        assertThrows(NoRemovableSegmentsException::class.java) { DownloadSegments.verify(report(10, segments), "", media, selected) }
        DownloadSegments.verify("", "", media, emptySet())
    }

    @Test fun `absent unselected entire-file and invalid segments never permit publication`() {
        assertThrows(NoRemovableSegmentsException::class.java) { DownloadSegments.verify(report(10, JSONArray()), "", media, selected) }
        assertThrows(NoRemovableSegmentsException::class.java) { DownloadSegments.verify(report(10, JSONArray().put(segment("outro", 2.0, 3.0))), "", media, selected) }
        assertThrows(NoRemovableSegmentsException::class.java) { DownloadSegments.verify(report(10, JSONArray().put(segment("sponsor", 0.0, 10.0))), "", media, selected) }
        assertThrows(SegmentServiceException::class.java) { DownloadSegments.verify(report(8, JSONArray().put(segment("sponsor", -1.0, 1.0))), "", media, selected) }
    }

    @Test fun `service failure and malformed output have a retryable redacted error distinct from absent segments`() {
        assertThrows(SegmentServiceException::class.java) { DownloadSegments.verify("", "WARNING: [SponsorBlock] Unable to communicate with API", media, selected) }
        assertThrows(SegmentServiceException::class.java) { DownloadSegments.verify("ACQUA_SEGMENTS:broken", "", media, selected) }
        assertEquals(DownloadFailure.SEGMENT_SERVICE, SegmentServiceException().toDownloadFailure())
        assertEquals(DownloadFailure.NO_SEGMENTS, NoRemovableSegmentsException().toDownloadFailure())
    }

    private fun segment(category: String, start: Double, end: Double) = JSONObject().put("category", category).put("start_time", start).put("end_time", end)
    private fun report(duration: Int, segments: JSONArray) = "ACQUA_SEGMENTS:" + JSONArray().put(duration).put(segments)
}
