package dev.qtremors.acqua.downloader

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yausername.youtubedl_android.YoutubeDLRequest
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadSectionsDeviceTest {
    @Test fun clipAndChapterOutputsHaveCorrectDurationsAndNames() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        YtDlpRuntime.initialize(context)
        val directory = File(context.cacheDir, "sections-fixture-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val source = File(directory, "source.mp4")
            val native = context.applicationInfo.nativeLibraryDir
            val generator = ProcessBuilder(File(native, "libffmpeg.so").absolutePath,
                "-nostdin", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "color=black:s=64x64:r=10",
                "-t", "6", "-c:v", "mpeg4", "-q:v", "5", source.absolutePath).redirectErrorStream(true)
            generator.environment()["LD_LIBRARY_PATH"] = "$native:${File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib")}:${File(context.noBackupFilesDir, "youtubedl-android/packages/python/usr/lib")}"
            val process = generator.start()
            val log = process.inputStream.bufferedReader().readText()
            assertEquals(log, 0, process.waitFor())
            MockWebServer().use { server ->
                server.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest) = MockResponse().setHeader("Content-Type", "video/mp4")
                        .setBody(Buffer().write(source.readBytes()))
                }
                val url = server.url("/source.mp4").toString()
                val media = ResolvedMedia(url, MediaKind.VIDEO, backend = MediaBackend.YT_DLP, durationSeconds = 6)
                val tagged = File(directory, "tagged.mp4")
                DirectMediaMetadata.write(context, source, tagged, url, 1_000L)
                assertDuration(tagged, 6_000)
                val engine = YtDlpEngine(context)
                val clip = engine.download(media, YtDlpDownloadOptions(clipStartSeconds = 1.0, clipEndSeconds = 3.0,
                    embedMetadata = false, embedThumbnail = false), {})
                try {
                    assertEquals(1, clip.artifacts.size)
                    assertDuration(clip.artifacts.single().file, 2_000)
                } finally { engine.cleanup(clip) }

                val chapters = JSONArray().put(JSONObject().put("title", "First").put("start_time", 0).put("end_time", 2))
                    .put(JSONObject().put("title", "Second").put("start_time", 2).put("end_time", 6))
                val info = File(directory, "source.info.json").apply {
                    writeText(JSONObject().put("id", "fixture").put("title", "Fixture").put("extractor", "generic")
                        .put("webpage_url", url).put("original_url", url).put("duration", 6).put("chapters", chapters)
                        .put("formats", JSONArray().put(JSONObject().put("format_id", "http").put("url", url)
                            .put("ext", "mp4").put("protocol", "http").put("vcodec", "mpeg4").put("acodec", "none"))).toString())
                }
                val options = YtDlpDownloadOptions(splitChapters = true, embedMetadata = false, embedThumbnail = false)
                val request = YoutubeDLRequest(emptyList()).apply {
                    addOption("--load-info-json", info.absolutePath)
                    addOption("-P", directory.absolutePath)
                    addOption("-o", "full.%(ext)s")
                    addOption("--no-quiet")
                    DownloadChapterManifest.requestOptions().forEach { (name, value) -> addOption(name, value) }
                    DownloadAdvancedOptions.arguments(media, options).forEach { (name, value) ->
                        if (value == null) addOption(name) else addOption(name, value)
                    }
                }
                val response = YtDlpRuntime.execute(context, request, "chapter-fixture-${UUID.randomUUID()}")
                assertEquals(response.err, 0, response.exitCode)
                val manifest = File(directory, "outputs.jsonl").apply {
                    writeText(DownloadChapterManifest.rows(response.out, true).joinToString("\n"))
                }
                val outputs = DownloadOutputSet.read(directory, manifest, CaptionMode.NONE)
                assertEquals(listOf("First", "Second"), outputs.artifacts.map { it.title })
                assertTrue(outputs.artifacts[0].file.name.contains("First"))
                assertTrue(outputs.artifacts[1].file.name.contains("Second"))
                assertDuration(outputs.artifacts[0].file, 2_000)
                assertDuration(outputs.artifacts[1].file, 4_000)
            }
        } finally { directory.deleteRecursively() }
    }

    private fun assertDuration(file: File, expected: Long) {
        val duration = MediaMetadataRetriever().use {
            it.setDataSource(file.absolutePath)
            it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        }
        assertTrue("Expected $expected ms, found $duration ms", kotlin.math.abs(duration - expected) <= 300)
    }
}
