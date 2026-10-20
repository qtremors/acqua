package dev.qtremors.acqua.downloader

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.data.network.HttpMediaClient
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadThumbnailTest {
    @get:Rule val temporary = TemporaryFolder()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val image = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+cK4YAAAAASUVORK5CYII=")
    private val media = ResolvedMedia("https://example.com/video", MediaKind.VIDEO, backend = MediaBackend.YT_DLP,
        thumbnailUrl = "https://images.example/cover-with-no-extension")

    @Test fun `thumbnail adds an original image without changing media and retries reuse it`() = runBlocking {
        var transfers = 0
        val helper = DownloadThumbnail(context, client { transfers++; image })
        val primary = temporary.newFile("movie.mp4").apply { writeText("unchanged media") }
        val result = DownloadExecutionResult(Uri.fromFile(primary), "Movie", "video/mp4", MediaKind.VIDEO)
        val request = request()
        var publications = 0
        val publish: suspend (File) -> Uri = { source ->
            publications++
            assertEquals("png", source.extension)
            assertArrayEquals(image, source.readBytes())
            Uri.fromFile(source.copyTo(File(temporary.root, "movie.thumbnail.png")))
        }
        val completed = helper.save(request, result, request.publicationId!!, false, publish)
        assertEquals(result.uri, completed.uri)
        assertEquals(result.mediaKind, completed.mediaKind)
        assertEquals(result.mimeType, completed.mimeType)
        assertEquals("unchanged media", primary.readText())
        assertEquals(2, completed.artifactUris.size)
        assertEquals(completed, helper.save(request, result, "retry", false, publish))
        assertEquals(1, publications)
        assertEquals(1, transfers)
        assertFalse(File(context.cacheDir, "yt-dlp/thumbnail-${request.publicationId}").exists())
    }

    @Test fun `audio without a thumbnail URL saves embedded artwork bytes separately`() = runBlocking {
        val audio = media.copy(kind = MediaKind.AUDIO, thumbnailUrl = null, backend = MediaBackend.DIRECT)
        val file = temporary.newFile("song.mp3").apply { writeText("unchanged audio") }
        val result = DownloadExecutionResult(Uri.fromFile(file), "Song", "audio/mpeg", MediaKind.AUDIO)
        val helper = DownloadThumbnail(context, client { error("Artwork should come from the saved audio") }) { uri ->
            assertEquals(result.uri, uri)
            image
        }
        val completed = helper.save(request(audio), result, "audio", false) { source ->
            assertArrayEquals(image, source.readBytes())
            Uri.fromFile(source.copyTo(File(temporary.root, "song.artwork.png")))
        }
        assertEquals(2, completed.artifactUris.size)
        assertEquals("unchanged audio", file.readText())
    }

    @Test fun `disabled extra image and unavailable artwork leave the media result alone`() = runBlocking {
        val helper = DownloadThumbnail(context, client { error("No image transfer expected") }) { null }
        val result = DownloadExecutionResult(Uri.parse("content://downloads/song"), "Song", "audio/mpeg", MediaKind.AUDIO)
        assertSame(result, helper.save(request().copy(options = YtDlpDownloadOptions()), result, "off", false) { error("No publication expected") })
        assertSame(result, helper.save(request(media.copy(thumbnailUrl = null)), result, "missing", false) { error("No publication expected") })
    }

    @Test fun `thumbnail failure preserves the primary file and can be retried`() = runBlocking {
        var fail = true
        val helper = DownloadThumbnail(context, client { if (fail) throw IOException("HTTP 503") else image })
        val file = temporary.newFile("video.mp4").apply { writeText("media") }
        val result = DownloadExecutionResult(Uri.fromFile(file), "Video", "video/mp4", MediaKind.VIDEO)
        val request = request()
        val publish: suspend (File) -> Uri = { Uri.fromFile(it.copyTo(File(temporary.root, "video.thumbnail.png"))) }
        assertTrue(runCatching { helper.save(request, result, "failure", false, publish) }.isFailure)
        assertEquals("media", file.readText())
        fail = false
        assertEquals(2, helper.save(request, result, "retry", false, publish).artifactUris.size)
    }

    private fun request(source: ResolvedMedia = media) = DownloadWorkRequest(source, source, source.url, 0,
        YtDlpDownloadOptions(downloadThumbnail = true), publicationId = UUID.randomUUID().toString())

    private fun client(bytes: () -> ByteArray): HttpMediaClient = HttpMediaClient(OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(bytes().toResponseBody()).build()
    }.build())
}
