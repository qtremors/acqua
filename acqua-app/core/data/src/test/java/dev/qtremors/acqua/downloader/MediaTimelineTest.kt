package dev.qtremors.acqua.downloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import dev.qtremors.acqua.domain.MediaChapter
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaTimelineTest {
    @get:Rule val temporary = TemporaryFolder()
    private val media = ResolvedMedia("https://example.com/video", MediaKind.VIDEO, durationSeconds = 10,
        chapters = listOf(MediaChapter("First", 0.0, 4.0), MediaChapter("Second", 4.0, 7.0), MediaChapter("Third", 7.0, 10.0)))
    private val ranges = listOf(ClipRange(1.25, 3.75, "Opening"), ClipRange(6.0, 9.0, "Ending"))
    private val cues = listOf(CaptionCue(0.5, 2.5, "First cue"), CaptionCue(3.0, 5.0, "Second cue"), CaptionCue(7.0, 9.5, "Third cue"))

    @Test fun `range validation covers bounds adjacent overlap order duplicates and excessive cuts`() {
        MediaTimeline.validateRanges(ranges, 10.0)
        MediaTimeline.validateRanges(listOf(ClipRange(0.0, 5.0), ClipRange(5.0, 10.0)), 10.0)
        listOf(listOf(ClipRange(-1.0, 2.0)), listOf(ClipRange(1.0, 11.0)), listOf(ClipRange(2.0, 2.0)),
            listOf(ClipRange(0.0, 0.0001)), listOf(ClipRange(Double.NaN, 2.0)), ranges.reversed(), ranges + ranges,
            listOf(ClipRange(0.0, 2.0), ClipRange(1.0, 3.0)), (0..20).map { ClipRange(it.toDouble(), it + 0.5) },
            listOf(ClipRange(0.0, 1.0, "bad\nname"))).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { MediaTimeline.validateRanges(invalid, 10.0) }
        }
        assertThrows(IllegalArgumentException::class.java) { DownloadSections.arguments(media, YtDlpDownloadOptions(clipRanges = ranges, splitChapters = true)) }
        assertThrows(NoDownloadableChaptersException::class.java) { MediaTimeline.plan(media.copy(chapters = emptyList()), YtDlpDownloadOptions(splitChapters = true)) }
    }

    @Test fun `caption timelines stretch shift clip drop empty intersections and preserve text`() {
        val timing = CaptionTiming(0.25, 1.1)
        val adjusted = MediaTimeline.cues(cues, ranges.first(), timing)
        assertEquals(2, adjusted.size)
        assertEquals(0.0, adjusted.first().startSeconds, 0.001)
        assertEquals(1.75, adjusted.first().endSeconds, 0.001)
        assertEquals(2.3, adjusted.last().startSeconds, 0.001)
        assertEquals(2.5, adjusted.last().endSeconds, 0.001)
        assertEquals(MediaTimeline.srt(adjusted), MediaTimeline.srt(MediaTimeline.parseSrt(MediaTimeline.srt(adjusted))))
        assertTrue(MediaTimeline.cues(cues, ClipRange(9.6, 10.0), CaptionTiming()).isEmpty())
        assertEquals("First cue", MediaTimeline.cues(cues, ClipRange(0.0, 1.0), CaptionTiming(-1.0)).single().text)
        for (timingError in listOf(CaptionTiming(Double.NaN), CaptionTiming(3601.0), CaptionTiming(scale = 0.0)))
            assertThrows(IllegalArgumentException::class.java) { timingError.validated() }
        for (invalid in listOf("garbage", "1\n00:90:00,000 --> 00:00:01,000\nBad", "x".repeat(MediaTimeline.MAX_CAPTION_BYTES + 1)))
            assertThrows(IllegalArgumentException::class.java) { MediaTimeline.parseSrt(invalid) }
    }

    @Test fun `chapter metadata is rebased bounded and safely escaped for each output`() {
        val chapters = MediaTimeline.chapters(media.chapters, ranges.last())
        assertEquals(listOf(MediaChapter("Second", 0.0, 1.0), MediaChapter("Third", 1.0, 3.0)), chapters)
        val metadata = MediaTimeline.chapterMetadata(listOf(MediaChapter("Unsafe=;#\nName", 0.0, 2.0)))
        assertTrue(metadata.contains("title=Unsafe\\=\\;\\# Name"))
        assertTrue(metadata.contains("END=2000"))
    }

    @Test fun `accepted clip and caption choices survive durable storage but new links never inherit ranges`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val options = YtDlpDownloadOptions(clipRanges = ranges, captionTiming = CaptionTiming(-0.5, 1.1), captionMode = CaptionMode.BOTH)
        val id = java.util.UUID.randomUUID()
        val precise = media.copy(preciseDurationSeconds = 10.875)
        DownloadRequestStore(context).put(id, DownloadWorkData.processedRequest(precise, precise, options, media.url), MediaTimelineSource.encode(precise))
        val restored = DownloadRequestStore(context).resolve(Data.Builder().putString(DownloadWorkData.KEY_REQUEST_REF, id.toString()).build())!!
        assertEquals(options, restored.options)
        assertEquals(10.875, MediaTimeline.duration(restored.media), 0.0)
        assertEquals(media.chapters, restored.media.chapters)
        val settings = dev.qtremors.acqua.data.settings.AppSettingsRepository(context)
        settings.rememberDownloadSelection(MediaKind.VIDEO, dev.qtremors.acqua.data.settings.LastDownloadSelection(dev.qtremors.acqua.domain.DownloadEngine.YT_DLP, options))
        assertTrue(settings.lastDownloadSelection(MediaKind.VIDEO)!!.options.clipRanges.isEmpty())
        assertFalse(settings.lastDownloadSelection(MediaKind.VIDEO)!!.options.captionTiming.adjusted)
        val download = MediaTimeline.sourceOptions(options)
        assertFalse(MediaTimeline.active(download))
        assertEquals(CaptionMode.SEPARATE, download.captionMode)
        assertEquals(CaptionFormat.SRT, download.captionFormat)
    }

    @Test fun `processing cancellation and unavailable captions stop before returning a publishable output set`() {
        val directory = temporary.newFolder("cancel")
        val source = File(directory, "source.mp4").apply { writeText("source") }
        var commands = 0
        val engine = MediaTimelineProcessor({ commands++ }, { throw java.util.concurrent.CancellationException() })
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            engine.process(DownloadOutputSet(directory, listOf(DownloadArtifact(source))), media, YtDlpDownloadOptions(clipRanges = ranges))
        }
        assertEquals(0, commands)
        assertThrows(MediaTimelineException::class.java) { MediaTimelineProcessor({ commands++ }).process(
            DownloadOutputSet(directory, listOf(DownloadArtifact(source))), media, YtDlpDownloadOptions(clipRanges = ranges, captionMode = CaptionMode.BOTH)) }
        assertEquals(DownloadFailure.MEDIA_TIMELINE, MediaTimelineException().toDownloadFailure())
    }

    @Test fun `named media sidecars and chapter commands generate host fixture plans with stable checkpoints`() {
        val cases = listOf(
            "video" to YtDlpDownloadOptions(clipRanges = ranges, captionMode = CaptionMode.BOTH, captionFormat = CaptionFormat.SRT, videoContainer = VideoContainer.MKV, captionTiming = CaptionTiming(0.25, 1.1)),
            "audio" to YtDlpDownloadOptions(contentType = DownloadContentType.AUDIO, audioFormat = AudioOutputFormat.M4A, clipRanges = ranges, captionMode = CaptionMode.BOTH, captionFormat = CaptionFormat.SRT),
            "chapters" to YtDlpDownloadOptions(splitChapters = true, captionMode = CaptionMode.BOTH, captionFormat = CaptionFormat.VTT),
            "timing" to YtDlpDownloadOptions(captionMode = CaptionMode.BOTH, captionFormat = CaptionFormat.ASS, captionTiming = CaptionTiming(-0.25)),
            "lyrics" to YtDlpDownloadOptions(contentType = DownloadContentType.AUDIO, audioFormat = AudioOutputFormat.MP3, clipRanges = ranges, captionMode = CaptionMode.SEPARATE, captionFormat = CaptionFormat.LRC)
        )
        val plans = cases.map { (name, options) -> fixturePlan(name, options) }
        File("build/media-timeline-plans.json").apply { parentFile.mkdirs(); writeText(JSONArray(plans).toString()) }
    }

    private fun fixturePlan(name: String, options: YtDlpDownloadOptions): JSONObject {
        val directory = temporary.newFolder(name)
        val ext = when (name) { "video", "timing" -> "mkv"; "audio" -> "m4a"; "lyrics" -> "mp3"; else -> "mp4" }
        val source = File(directory, "source.$ext").apply { writeText("source") }
        val caption = File(directory, "source.en.srt").apply { writeText(MediaTimeline.srt(cues)) }
        val commands = mutableListOf<List<String>>()
        val runner: (List<String>) -> Unit = { arguments -> commands += arguments; File(arguments.last()).writeText("fake") }
        val sourceSet = DownloadOutputSet(directory, listOf(DownloadArtifact(source, sourceId = "fixture"), DownloadArtifact(caption, true, ".en", "fixture")))
        val output = MediaTimelineProcessor(runner).process(sourceSet, media, options)
        assertEquals(MediaTimeline.plan(media, options).size, output.artifacts.count { !it.caption })
        assertEquals(output.artifacts.size, output.artifacts.map(DownloadArtifact::checkpointKey).distinct().size)
        val repeated = MediaTimelineProcessor(runner).process(sourceSet, media, options)
        assertEquals(output.artifacts.map(DownloadArtifact::checkpointKey), repeated.artifacts.map(DownloadArtifact::checkpointKey))
        val uniqueCommands = commands.take(commands.size / 2)
        fun relative(file: File) = file.relativeTo(directory).invariantSeparatorsPath
        val files = JSONObject()
        directory.walkTopDown().filter { it.isFile && it.readText() !in listOf("source", "fake") }.forEach { files.put(relative(it), it.readText()) }
        val ranges = MediaTimeline.plan(media, options)
        return JSONObject().put("name", name).put("source", source.name).put("audio", options.contentType == DownloadContentType.AUDIO).put("files", files)
            .put("commands", JSONArray(uniqueCommands.map { arguments -> JSONArray(arguments.map { if (it.startsWith(directory.absolutePath)) "@/" + relative(File(it)) else it }) }))
            .put("outputs", JSONArray(output.artifacts.filterNot(DownloadArtifact::caption).mapIndexed { index, artifact -> JSONObject().put("file", relative(artifact.file))
                .put("duration", ranges[index].endSeconds - ranges[index].startSeconds)
                .put("chapters", JSONArray(MediaTimeline.chapters(media.chapters, ranges[index]).map { JSONObject().put("start", it.startSeconds).put("end", it.endSeconds) }))
                .put("cues", JSONArray(MediaTimeline.cues(cues, ranges[index], options.captionTiming).map { JSONObject().put("start", it.startSeconds).put("end", it.endSeconds) }))
                .put("subtitles", options.captionMode in listOf(CaptionMode.EMBED, CaptionMode.BOTH)) }))
            .put("sidecars", JSONArray(output.artifacts.filter(DownloadArtifact::caption).map { relative(it.file) }))
    }
}
