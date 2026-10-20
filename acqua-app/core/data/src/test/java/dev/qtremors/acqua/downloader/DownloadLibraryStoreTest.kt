package dev.qtremors.acqua.downloader

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadLibraryStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun store(): DownloadLibraryStore {
        context.getSharedPreferences("download_library_v1", Context.MODE_PRIVATE).edit().clear().commit()
        return DownloadLibraryStore(context)
    }
    private fun request(id: String = "source:42", options: YtDlpDownloadOptions = YtDlpDownloadOptions()): DownloadWorkRequest {
        val media = ResolvedMedia("https://example.com/42", MediaKind.VIDEO, backend = MediaBackend.YT_DLP, sourceIdentity = id)
        return DownloadWorkRequest(media, media, media.url, 0, options)
    }
    @Test fun `archive skips only matching accessible complete output sets`() {
        val store = store()
        val media = temporary.newFile("video.mp4").apply { writeText("media") }
        val caption = temporary.newFile("video.en.srt").apply { writeText("caption") }
        val request = request(options = YtDlpDownloadOptions(captionMode = CaptionMode.BOTH))
        store.record(request, DownloadExecutionResult(Uri.fromFile(media), "Video", "video/mp4", MediaKind.VIDEO,
            listOf(Uri.fromFile(media), Uri.fromFile(caption))))
        assertEquals(2, DownloadLibraryStore(context).existing(request.copy(options = request.options.copy(skipArchived = true)))?.uris?.size)
        assertNull(store.existing(request("different:42", request.options)))
        assertNull(store.existing(request.copy(options = request.options.copy(contentType = DownloadContentType.AUDIO))))
        assertNull(store.existing(request.copy(options = request.options.copy(captionMode = CaptionMode.NONE))))
        assertNull(store.existing(request.copy(options = request.options.copy(clipStartSeconds = 1.0, clipEndSeconds = 2.0))))
        caption.delete()
        assertNull(store.existing(request))
        assertEquals(1, store.archive().size)
        store.removeArchive(store.archive().single().key)
        assertTrue(media.exists())
        assertTrue(store.archive().isEmpty())
    }
    @Test fun `thumbnail extras have distinct archive identities and missing extras prevent skipping`() {
        val store = store()
        for (backend in MediaBackend.entries) {
            val ordinary = request(backend.name).let { it.copy(media = it.media.copy(backend = backend)) }
            val alongside = ordinary.copy(options = ordinary.options.copy(downloadThumbnail = true))
            assertNotEquals(DownloadLibraryStore.identity(ordinary), DownloadLibraryStore.identity(alongside))
            val file = temporary.newFile("${backend.name}.mp4").apply { writeText("media") }
            val image = temporary.newFile("${backend.name}.png").apply { writeText("thumbnail") }
            store.record(alongside, DownloadExecutionResult(Uri.fromFile(file), "Video", "video/mp4", MediaKind.VIDEO,
                listOf(Uri.fromFile(file), Uri.fromFile(image))))
            assertEquals(2, store.existing(alongside)?.uris?.size)
            assertNull(store.existing(ordinary))
            image.delete()
            assertNull(store.existing(alongside))
        }
    }

    @Test fun `older options keep thumbnail extras off and a saved toggle can turn them off again`() {
        val enabled = YtDlpDownloadOptions(downloadThumbnail = true, embedThumbnail = false, cropArtwork = false)
        assertEquals(enabled, DownloadOptionsCodec.decode(DownloadOptionsCodec.encode(enabled), YtDlpDownloadOptions()))
        assertFalse(DownloadOptionsCodec.decode("{}", enabled).downloadThumbnail)
        assertFalse(DownloadOptionsCodec.decode(DownloadOptionsCodec.encode(enabled.copy(downloadThumbnail = false)), enabled).downloadThumbnail)
    }

    @Test fun `presets retain reusable policy while omitting media specific choices`() {
        val store = store()
        val options = YtDlpDownloadOptions(contentType = DownloadContentType.AUDIO, audioFormat = AudioOutputFormat.MP3,
            audioBitrateKbps = 192, maximumVideoHeight = 720, videoFormatId = "137", audioFormatId = "140",
            clipStartSeconds = 10.0, clipEndSeconds = 20.0, skipArchived = true,
            filenamePatternOverride = "{title}-{index}", audioFilenamePatternOverride = "{artist}-{title}")
        store.savePreset(DownloadPreset(" Podcast ", options))
        val preset = DownloadLibraryStore(context).presets().single()
        assertEquals("Podcast", preset.name)
        assertEquals(options.copy(videoFormatId = null, audioFormatId = null, clipStartSeconds = null, clipEndSeconds = null), preset.options)
        store.savePreset(preset.copy(options = preset.options.copy(audioBitrateKbps = 256)))
        assertEquals(1, store.presets().size)
        assertEquals(256, store.presets().single().options.audioBitrateKbps)
        store.removePreset(preset.name)
        assertTrue(store.presets().isEmpty())
    }
    @Test fun `terminal tasks survive reload with accepted options and partial publication counts`() {
        context.getSharedPreferences("download_tasks_v1", Context.MODE_PRIVATE).edit().clear().commit()
        val store = DownloadTaskStore(context)
        val item = DownloadQueueItem(UUID.randomUUID().toString(), DownloadQueueState.FAILED,
            title = "Same title", sourceUrl = "https://example.com/one", createdAt = System.currentTimeMillis(),
            artifactCount = 2, acceptedOptions = YtDlpDownloadOptions(clipStartSeconds = 3.0, clipEndSeconds = 9.0))
        store.record(listOf(item))
        val restored = DownloadTaskStore(context).read().single()
        assertEquals(item.acceptedOptions, restored.acceptedOptions)
        assertEquals(2, restored.artifactCount)
        assertEquals(item.sourceUrl, restored.sourceUrl)
        store.dismiss(item.id)
        store.record(listOf(item))
        assertTrue(store.read().isEmpty())
    }
    @Test fun `retention keeps the latest terminal tasks and ignores active or corrupt records`() {
        val prefs = context.getSharedPreferences("download_tasks_v1", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("tasks", "broken").commit()
        val store = DownloadTaskStore(context)
        assertTrue(store.read().isEmpty())
        val now = System.currentTimeMillis()
        val items = (1..205).map { DownloadQueueItem(UUID.randomUUID().toString(), DownloadQueueState.SUCCEEDED, createdAt = now + it) }
        val expired = DownloadQueueItem(UUID.randomUUID().toString(), DownloadQueueState.FAILED, createdAt = now - DownloadTaskStore.RETENTION_MILLIS - 1)
        val active = expired.copy(id = UUID.randomUUID().toString(), state = DownloadQueueState.RUNNING)
        val discarded = store.record(items + expired + active)
        assertEquals(200, store.read().size)
        assertTrue(expired.id in discarded)
        assertFalse(active.id in discarded)
        assertEquals(items.takeLast(200).map { it.id }, store.read().map { it.id })
    }
}
