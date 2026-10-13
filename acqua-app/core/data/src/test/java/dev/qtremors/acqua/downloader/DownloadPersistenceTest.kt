package dev.qtremors.acqua.downloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import dev.qtremors.acqua.data.settings.DownloadSettings
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun `retention preserves publication checkpoints used by retained retries`() {
        val store = DownloadRequestStore(context)
        val original = UUID.randomUUID()
        val retry = UUID.randomUUID()
        val request = Data.Builder().putAll(DownloadWorkData.directRequest(
            ResolvedMedia("https://example.com/file.mp4", MediaKind.VIDEO), 0, "https://example.com/post"))
            .putString(DownloadWorkData.KEY_PUBLICATION_ID, original.toString()).build()
        store.put(original, request)
        store.put(retry, request)
        store.recordPublished(original.toString(), "first", android.net.Uri.parse("content://downloads/first"))
        store.discard(listOf(original.toString()))
        assertNull(store.input(original))
        assertNotNull(store.input(retry))
        assertEquals(1, store.publishedCount(original.toString()))
        store.discard(listOf(retry.toString()))
        assertEquals(0, store.publishedCount(original.toString()))
    }
    @Test fun `request identity survives reload with exact choices collection and output policy`() {
        val store = DownloadRequestStore(context)
        val id = UUID.randomUUID()
        val media = ResolvedMedia("https://example.com/entry", MediaKind.VIDEO, backend = MediaBackend.YT_DLP,
            collectionUrl = "https://example.com/list", collectionTitle = "Playlist", collectionIndex = 30, collectionSize = 45,
            sourceTimestampMillis = 1234567890123L, title = "Entry 30", durationSeconds = 60)
        val options = YtDlpDownloadOptions(videoFormatId = "137", videoFormatIncludesAudio = false, audioFormatId = "140",
            captionMode = CaptionMode.BOTH, captionLanguages = "en,hi", captionFormat = CaptionFormat.SRT,
            automaticCaptions = true, videoContainer = VideoContainer.MKV, recordHistory = false, verboseDiagnostics = true,
            network = dev.qtremors.acqua.data.network.TransferNetworkOptions(1024, "http://localhost:8080", true, 3),
            removeSegments = setOf(SegmentCategory.SPONSOR, SegmentCategory.INTRO), additionalAudioFormatIds = setOf("141"),
            acceptedStreams = listOf(dev.qtremors.acqua.domain.MediaFormatOption("137", "mp4", "avc1", "none", 1920, 1080, 30),
                dev.qtremors.acqua.domain.MediaFormatOption("140", "m4a", "none", "aac", language = "en", audioChannels = 2),
                dev.qtremors.acqua.domain.MediaFormatOption("141", "m4a", "none", "aac", language = "hi", audioChannels = 2)))
        val storage = DownloadSettings("Accepted Folder", true, "{title}-{index}", "{artist}-{title}")
        store.put(id, Data.Builder().putAll(DownloadWorkData.processedRequest(media, media, options, media.collectionUrl!!))
            .putString(DownloadWorkData.KEY_STORAGE, DownloadOptionsCodec.encodeStorage(storage)).build())
        val reference = Data.Builder().putString(DownloadWorkData.KEY_REQUEST_REF, id.toString()).build()
        val restored = DownloadRequestStore(context).resolve(DownloadWorkData.fromPersistableBundle(DownloadWorkData.toPersistableBundle(reference)))!!
        assertEquals(options, restored.options)
        assertEquals(storage, restored.storageSettings)
        assertEquals(29, restored.itemIndex)
        assertEquals(45, restored.itemCount)
        assertEquals(media.collectionUrl, restored.media.collectionUrl)
        assertEquals(30, restored.media.collectionIndex)
        assertEquals("Playlist", restored.media.collectionTitle)
        assertEquals(45, restored.media.collectionSize)
        assertEquals(media.sourceTimestampMillis, restored.media.sourceTimestampMillis)
        assertEquals(60, restored.media.durationSeconds)
    }
    @Test fun `malformed durable references do not create downloads`() {
        assertNull(DownloadRequestStore(context).resolve(Data.Builder().putString(DownloadWorkData.KEY_REQUEST_REF, "broken").build()))
        assertNull(DownloadRequestStore(context).resolve(Data.Builder().putString(DownloadWorkData.KEY_REQUEST_REF, UUID.randomUUID().toString()).build()))
    }

    @Test fun `malformed network policies cannot restore a request with a direct fallback`() {
        val store = DownloadRequestStore(context)
        val media = ResolvedMedia("https://example.com/file.mp4", MediaKind.VIDEO)
        val input = DownloadWorkData.processedRequest(media, media, YtDlpDownloadOptions(), "https://example.com/post")
        val policies = listOf<Any>("broken", org.json.JSONObject().put("proxy", "http://user:secret@host:80")
            .put("rateKib", 0).put("ipv4", false).put("fragments", 1), org.json.JSONObject().put("rateKib", -1))
        policies.forEach { policy ->
            val id = UUID.randomUUID()
            val options = org.json.JSONObject(DownloadOptionsCodec.encode(YtDlpDownloadOptions())).put("network", policy)
            store.put(id, Data.Builder().putAll(input).putString(DownloadWorkData.KEY_OPTIONS, options.toString()).build())
            assertNull(store.resolve(Data.Builder().putString(DownloadWorkData.KEY_REQUEST_REF, id.toString()).build()))
        }
    }
    @Test fun `pending links deduplicate and preserve partial acceptance on reload`() {
        context.getSharedPreferences("pending_links", Context.MODE_PRIVATE).edit().clear().commit()
        val store = PendingLinkStore(context)
        store.save("https://one.example/a https://two.example/b https://one.example/a")
        assertEquals(2, store.read().size)
        val link = store.read().first()
        store.update(link.copy(status = "Partially queued", acceptedItems = mapOf("entry#1" to UUID.randomUUID().toString())))
        val restored = PendingLinkStore(context).read().first()
        assertEquals("Partially queued", restored.status)
        assertEquals(1, restored.acceptedItems.size)
        store.save(link.url)
        assertEquals(restored, store.read().first())
        store.remove(link.url)
        assertEquals(1, store.read().size)
    }
    @Test fun `flat collection keeps entries beyond the former candidate limit`() {
        val entries = (1..30).joinToString(",") { "{\"title\":\"Entry $it\",\"url\":\"https://example.com/$it\"}" }
        val items = YtDlpEngine(context).parseInspection("{\"title\":\"Collection\",\"entries\":[$entries]}", "https://example.com/list", false)
        assertEquals(30, items.size)
        assertEquals(30, items.last().collectionIndex)
        assertEquals("Collection", items.first().collectionTitle)
    }
    @Test fun `caption catalog distinguishes manual and automatic languages`() {
        val items = YtDlpEngine(context).parseInspection("{\"subtitles\":{\"en\":[{\"name\":\"English\"}]},\"automatic_captions\":{\"hi\":[{}]}}", "https://example.com/video", false)
        assertEquals(listOf(false, true), items.single().captions.map { it.automatic })
        assertEquals(listOf("en", "hi"), items.single().captions.map { it.language })
    }
}
