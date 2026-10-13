package dev.qtremors.acqua.downloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import java.io.File
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadSectionsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val media = ResolvedMedia("https://example.com/video", MediaKind.VIDEO, durationSeconds = 60)
    @Test fun `chapter cuts avoid reordered frames and retain selected audio encoding`() {
        val source = File("source.mp4"); val target = File("clip.mp4"); val range = ClipRange(1.0, 3.0)
        val video = MediaTimelineProcessor.cutArguments(source, target, range, DownloadContentType.VIDEO)
        assertTrue(video.windowed(2).contains(listOf("-bf", "0")))
        val audio = MediaTimelineProcessor.cutArguments(source, target, range, DownloadContentType.AUDIO)
        assertTrue(audio.windowed(2).contains(listOf("-c", "copy")))
        assertFalse(audio.contains("-c:v"))
    }
    @Test fun `generic sources with matching file identifiers keep distinct archive identities`() {
        val engine = YtDlpEngine(ApplicationProvider.getApplicationContext<Context>())
        val info = """{"id":"video","extractor_key":"Generic"}"""
        val first = engine.parseInspection(info, "https://example.com/one/video.mp4", false).single()
        val second = engine.parseInspection(info, "https://example.com/two/video.mp4", false).single()
        assertNotEquals(first.sourceIdentity, second.sourceIdentity)
        val collection = engine.parseInspection("""{"entries":[{"id":"42","ie_key":"Source","url":"https://example.com/42","_type":"url"}]}""",
            "https://example.com/list", false).single()
        assertEquals("Source:42", collection.sourceIdentity)
    }
    @Test fun `clips preserve exact boundaries and remove original chapter timelines`() {
        val options = YtDlpDownloadOptions(clipStartSeconds = 3.125, clipEndSeconds = 9.5)
        val args = DownloadAdvancedOptions.arguments(media, options) + YtDlpPostProcessing.options(options)
        val cut = MediaTimelineProcessor.cutArguments(File("source.mp4"), File("clip.mp4"), MediaTimeline.plan(media, options).single(), options.contentType)
        assertTrue(cut.windowed(2).contains(listOf("-ss", "3.125")))
        assertTrue(cut.windowed(2).contains(listOf("-t", "6.375")))
        assertFalse(args.any { it.first == "--download-sections" })
        assertFalse(args.any { it.first == "--embed-chapters" })
        assertEquals(options, DownloadOptionsCodec.decode(DownloadOptionsCodec.encode(options), YtDlpDownloadOptions()))
        listOf(options.copy(clipStartSeconds = -1.0), options.copy(clipEndSeconds = 61.0),
            options.copy(clipEndSeconds = 3.125), options.copy(clipEndSeconds = null),
            options.copy(clipStartSeconds = Double.NaN), options.copy(splitChapters = true)).forEach {
            assertTrue(runCatching { DownloadAdvancedOptions.arguments(media, it) }.isFailure)
        }
    }
    @Test fun `chapter manifests retain every chapter with distinct stable publication identities`() {
        val directory = temporary.newFolder("chapters")
        val chapters = (1..3).map { File(directory, "$it.mp4").apply { writeText("chapter") } }
        val catalog = JSONArray(chapters.mapIndexed { index, file -> org.json.JSONObject()
            .put("filepath", file.absolutePath).put("title", "Part ${index + 1}") })
        val output = "ACQUA_OUTPUT:[\"42\",\"full.mp4\"]\nACQUA_CHAPTERS:" + JSONArray().put("42").put(catalog)
        val manifest = File(directory, "outputs.jsonl").apply { writeText(DownloadChapterManifest.rows(output, true).joinToString("\n")) }
        val result = DownloadOutputSet.read(directory, manifest, CaptionMode.NONE)
        assertEquals(chapters.map(File::getCanonicalFile), result.artifacts.map { it.file })
        assertEquals(listOf("Part 1", "Part 2", "Part 3"), result.artifacts.map { it.title })
        assertEquals(3, result.artifacts.map { it.checkpointKey }.distinct().size)
        assertTrue(runCatching { DownloadChapterManifest.rows("ACQUA_CHAPTERS:[\"42\",[]]", true) }.isFailure)
        assertEquals(DownloadFailure.NO_CHAPTERS, runCatching {
            DownloadChapterManifest.rows("ACQUA_CHAPTERS:[\"42\",[]]", true)
        }.exceptionOrNull()!!.toDownloadFailure())
        assertTrue(runCatching { DownloadChapterManifest.rows("ACQUA_CHAPTERS:[\"42\",null]", true) }.exceptionOrNull()?.message?.contains("no downloadable chapters") == true)
    }
    @Test fun `collection inspection retains unavailable and nested positions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val items = YtDlpEngine(context).parseInspection("""{"entries":[null,{"id":"42","_type":"url","url":"https://example.com/42"},{"title":"Nested","webpage_url":"https://example.com/nested","entries":[]}]}""",
            "https://example.com/list", false)
        assertEquals(listOf(1, 2, 3), items.map { it.collectionIndex })
        assertNotNull(items[0].unavailableReason)
        assertNull(items[1].unavailableReason)
        assertNotNull(items[2].unavailableReason)
        assertEquals("https://example.com/nested", items[2].url)
        assertTrue(runCatching { DownloadAdvancedOptions.arguments(items[0], YtDlpDownloadOptions()) }.isFailure)
    }
}
