package dev.qtremors.acqua.downloader

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.data.history.HistoryRepository
import dev.qtremors.acqua.data.network.HttpMediaClient
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import java.io.File
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadExecutorThumbnailTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `extra images share the media name and folder and retries retain both history records`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = DownloadsProvider(temporary.root).apply { attachInfo(context, ProviderInfo().apply { authority = "media" }) }
        ShadowContentResolver.registerProviderInternal("media", provider)
        val settings = AppSettingsRepository(context).apply { setBaseFolder("Acqua"); setCategorizeMedia(true) }
        val history = HistoryRepository(context)
        history.clear()
        val image = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+cK4YAAAAASUVORK5CYII=")
        var transfers = 0
        for (kind in listOf(MediaKind.VIDEO, MediaKind.AUDIO)) {
            val audio = kind == MediaKind.AUDIO
            val primary = if (audio) "ID3".toByteArray() + ByteArray(100) else
                byteArrayOf(0, 0, 0, 24) + "ftypisom".toByteArray() + ByteArray(4) + "isommp42".toByteArray() +
                    byteArrayOf(0, 0, 0, 8) + "moov".toByteArray()
            val client = HttpMediaClient(OkHttpClient.Builder().addInterceptor { chain ->
                transfers++
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body((if (chain.request().url.encodedPath == "/cover") image else primary).toResponseBody()).build()
            }.build())
            val media = ResolvedMedia("https://example.com/${kind.name}", kind, thumbnailUrl = "https://example.com/cover",
                mimeType = if (audio) "audio/mpeg" else "video/mp4", fileExtension = if (audio) "mp3" else "mp4",
                downloadName = "Chosen ${kind.name.lowercase()}")
            val request = DownloadWorkRequest(media, media, media.url, 0,
                YtDlpDownloadOptions(contentType = if (audio) DownloadContentType.AUDIO else DownloadContentType.VIDEO,
                    embedMetadata = false, downloadThumbnail = true), publicationId = UUID.randomUUID().toString())
            val executor = DownloadExecutor(context, settings, history, client)
            val result = executor.execute(request, request.publicationId!!, {}, {})
            assertEquals(2, result.artifactUris.size)
            assertArrayEquals(primary, provider.files.getValue(result.uri).readBytes())
            val extra = result.artifactUris.last()
            assertArrayEquals(image, provider.files.getValue(extra).readBytes())
            val name = provider.rows.getValue(result.uri).getAsString(MediaStore.MediaColumns.DISPLAY_NAME).substringBeforeLast('.')
            assertEquals("$name.${if (audio) "artwork" else "thumbnail"}.png",
                provider.rows.getValue(extra).getAsString(MediaStore.MediaColumns.DISPLAY_NAME))
            assertEquals(provider.rows.getValue(result.uri).getAsString(MediaStore.MediaColumns.RELATIVE_PATH),
                provider.rows.getValue(extra).getAsString(MediaStore.MediaColumns.RELATIVE_PATH))
            val savedImage = history.load().single { it.fileUri == extra.toString() }
            assertEquals("image/png", savedImage.mimeType)
            assertFalse(savedImage.isVideo)
            assertEquals(result, executor.execute(request, request.publicationId!!, {}, {}))
        }
        assertEquals(4, transfers)
        assertEquals(4, provider.rows.size)
        assertEquals(4, history.load().count { it.url.startsWith("https://example.com/") })
    }

    @Test fun `history policy covers direct processed captions thumbnails authenticated requests and retries`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = DownloadsProvider(temporary.root).apply { attachInfo(context, ProviderInfo().apply { authority = "media" }) }
        ShadowContentResolver.registerProviderInternal("media", provider)
        val settings = AppSettingsRepository(context).apply { setUseBrowserSessions(true) }
        val history = HistoryRepository(context)
        val image = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+cK4YAAAAASUVORK5CYII=")
        val client = HttpMediaClient(OkHttpClient.Builder().addInterceptor { chain ->
            val primary = when {
                chain.request().url.encodedPath.contains("/AUDIO/") -> "ID3".toByteArray() + ByteArray(100)
                chain.request().url.encodedPath.contains("/VIDEO/") -> byteArrayOf(0, 0, 0, 16) + "ftypisom".toByteArray() +
                    byteArrayOf(0, 0, 0, 0, 0, 0, 0, 8) + "mdat".toByteArray()
                else -> image
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(primary.toResponseBody()).build()
        }.build())
        val engine = object : DownloadProcessingEngine {
            override fun download(media: ResolvedMedia, options: YtDlpDownloadOptions, onProgress: (YtDlpProgress) -> Unit,
                taskKey: String?): DownloadOutputSet {
                val directory = temporary.newFolder()
                val main = File(directory, if (options.contentType == DownloadContentType.AUDIO) "main.m4a" else "main.mp4").apply { writeBytes(image) }
                val caption = File(directory, "main.en.srt").apply { writeText("1\n00:00:00,000 --> 00:00:01,000\nCaption\n") }
                return DownloadOutputSet(directory, listOf(DownloadArtifact(main), DownloadArtifact(caption, caption = true, suffix = ".en")))
            }
            override fun cleanup(outputs: DownloadOutputSet) { outputs.directory.deleteRecursively() }
            override fun cancelAll() = Unit
        }
        for (recordHistory in listOf(false, true)) for (backend in dev.qtremors.acqua.domain.MediaBackend.entries) {
            val kinds = if (backend == dev.qtremors.acqua.domain.MediaBackend.DIRECT) MediaKind.entries else listOf(MediaKind.AUDIO, MediaKind.VIDEO)
            for (kind in kinds) {
                history.clear()
                val media = ResolvedMedia("https://example.com/${kind.name}/${UUID.randomUUID()}", kind, backend = backend,
                    title = "Original title", artist = "Aster", sourceIdentity = "source:42", downloadName = "Renamed",
                    thumbnailUrl = "https://example.com/cover", requestCookies = "session=fixture", explicitBrowserSessionAuthorized = true,
                    mimeType = when (kind) { MediaKind.IMAGE -> "image/png"; MediaKind.AUDIO -> "audio/mpeg"; MediaKind.VIDEO -> "video/mp4" },
                    fileExtension = when (kind) { MediaKind.IMAGE -> "png"; MediaKind.AUDIO -> "mp3"; MediaKind.VIDEO -> "mp4" })
                val options = YtDlpDownloadOptions(contentType = if (kind == MediaKind.AUDIO) DownloadContentType.AUDIO else DownloadContentType.VIDEO,
                    recordHistory = recordHistory, embedMetadata = false, downloadThumbnail = true, captionMode = CaptionMode.SEPARATE)
                val request = DownloadWorkRequest(media, media, media.url, 0, options, publicationId = UUID.randomUUID().toString())
                val executor = DownloadExecutor(context, settings, history, client) { engine }
                val result = executor.execute(request, request.publicationId!!, {}, {})
                assertFalse(result.artifactUris.isEmpty())
                assertEquals(if (recordHistory) result.artifactUris.size else 0, history.load().size)
                history.load().forEach {
                    assertEquals("Original title", it.title)
                    assertEquals("Aster", it.author)
                    assertEquals("source:42", it.sourceIdentity)
                }
                val count = provider.rows.size
                settings.setDownloadDefaults(options.copy(recordHistory = !recordHistory))
                assertEquals(result, DownloadExecutor(context, settings, history, client) { engine }.execute(request, request.publicationId!!, {}, {}))
                assertEquals(count, provider.rows.size)
                assertEquals(if (recordHistory) result.artifactUris.size else 0, history.load().size)
            }
        }
    }

    @Test fun `chapter template context names each media file and keeps caption basenames paired`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = DownloadsProvider(temporary.root).apply { attachInfo(context, ProviderInfo().apply { authority = "media" }) }
        ShadowContentResolver.registerProviderInternal("media", provider)
        val settings = AppSettingsRepository(context)
        val media = ResolvedMedia("https://example.com/chapter-test", MediaKind.VIDEO, backend = dev.qtremors.acqua.domain.MediaBackend.YT_DLP,
            title = "Video", sourceIdentity = "Source:abc", sourceTimestampMillis = 1_704_067_200_000L)
        val engine = object : DownloadProcessingEngine {
            override fun download(media: ResolvedMedia, options: YtDlpDownloadOptions, onProgress: (YtDlpProgress) -> Unit,
                taskKey: String?): DownloadOutputSet {
                val directory = temporary.newFolder()
                return DownloadOutputSet(directory, listOf("Opening", "Closing").flatMapIndexed { index, title ->
                    listOf(DownloadArtifact(File(directory, "$index.mp4").apply { writeText("media") }, sourceId = "$index", title = title),
                        DownloadArtifact(File(directory, "$index.en.srt").apply { writeText("caption") }, caption = true, suffix = ".en", sourceId = "$index"))
                })
            }
            override fun cleanup(outputs: DownloadOutputSet) { outputs.directory.deleteRecursively() }
            override fun cancelAll() = Unit
        }
        val request = DownloadWorkRequest(media, media, media.url, 0,
            YtDlpDownloadOptions(recordHistory = false, splitChapters = true, captionMode = CaptionMode.SEPARATE),
            storageSettings = dev.qtremors.acqua.data.settings.DownloadSettings("Acqua", false, "{source_id}_{chapter}_{upload_date}"),
            publicationId = UUID.randomUUID().toString())
        val result = DownloadExecutor(context, settings, HistoryRepository(context), HttpMediaClient()) { engine }
            .execute(request, request.publicationId!!, {}, {})
        assertEquals(listOf("abc_Opening_20240101.mp4", "abc_Opening_20240101.en.srt",
            "abc_Closing_20240101.mp4", "abc_Closing_20240101.en.srt"),
            result.artifactUris.map { provider.rows.getValue(it).getAsString(MediaStore.MediaColumns.DISPLAY_NAME) })
        val withoutChapterToken = request.copy(storageSettings = request.storageSettings!!.copy(filenamePattern = "{source_id}_{upload_date}"),
            publicationId = UUID.randomUUID().toString())
        val fallback = DownloadExecutor(context, settings, HistoryRepository(context), HttpMediaClient()) { engine }
            .execute(withoutChapterToken, withoutChapterToken.publicationId!!, {}, {})
        assertEquals(listOf("abc_20240101_Opening.mp4", "abc_20240101_Opening.en.srt",
            "abc_20240101_Closing.mp4", "abc_20240101_Closing.en.srt"),
            fallback.artifactUris.map { provider.rows.getValue(it).getAsString(MediaStore.MediaColumns.DISPLAY_NAME) })
    }

    @Test fun `every multi-file publication boundary resumes without duplicate media or sidecars`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = AppSettingsRepository(context).apply { setUseBrowserSessions(false) }
        val history = HistoryRepository(context)
        for (failure in 1..4) {
            val provider = DownloadsProvider(temporary.newFolder(), failInsert = failure)
            provider.attachInfo(context, ProviderInfo().apply { authority = "media" })
            ShadowContentResolver.registerProviderInternal("media", provider)
            val temporaryOutputs = mutableListOf<File>()
            val engine = object : DownloadProcessingEngine {
                override fun download(media: ResolvedMedia, options: YtDlpDownloadOptions, onProgress: (YtDlpProgress) -> Unit,
                    taskKey: String?): DownloadOutputSet {
                    val directory = temporary.newFolder().also(temporaryOutputs::add)
                    val artifacts = (1..2).flatMap { index ->
                        val main = File(directory, "Part$index.mp4").apply { writeText("media $index") }
                        val subtitle = File(directory, "Part$index.en.srt").apply { writeText("caption $index") }
                        listOf(DownloadArtifact(main, sourceId = "source:part:$index", title = "Part $index"),
                            DownloadArtifact(subtitle, caption = true, suffix = ".en", sourceId = "source:part:$index"))
                    }
                    return DownloadOutputSet(directory, artifacts)
                }
                override fun cleanup(outputs: DownloadOutputSet) { outputs.directory.deleteRecursively() }
                override fun cancelAll() = Unit
            }
            val media = ResolvedMedia("https://example.com/video", MediaKind.VIDEO, backend = dev.qtremors.acqua.domain.MediaBackend.YT_DLP)
            val request = DownloadWorkRequest(media, media, media.url, 0,
                YtDlpDownloadOptions(recordHistory = false, captionMode = CaptionMode.BOTH), publicationId = UUID.randomUUID().toString())
            val first = DownloadExecutor(context, settings, history, HttpMediaClient()) { engine }
            assertTrue(runCatching { first.execute(request, request.publicationId!!, {}, {}) }.isFailure)
            assertEquals(failure - 1, provider.rows.size)
            val existing = provider.files.mapValues { it.value.readBytes().toList() }
            val restarted = DownloadExecutor(context, settings, history, HttpMediaClient()) { engine }
            val result = restarted.execute(request, request.publicationId!!, {}, {})
            assertEquals(4, result.artifactUris.size)
            assertEquals(4, result.artifactUris.distinct().size)
            assertEquals(4, provider.rows.size)
            assertEquals(setOf("media 1", "caption 1", "media 2", "caption 2"), provider.files.values.map { it.readText() }.toSet())
            existing.forEach { (uri, contents) -> assertEquals(contents, provider.files.getValue(uri).readBytes().toList()) }
            assertEquals(result, restarted.execute(request, request.publicationId!!, {}, {}))
            assertEquals(4, provider.rows.size)
            assertTrue(temporaryOutputs.none { it.exists() })
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `parallel matching requests preserve duplicate skipping after the first publication`() = kotlinx.coroutines.test.runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = DownloadsProvider(temporary.newFolder())
        provider.attachInfo(context, ProviderInfo().apply { authority = "media" })
        ShadowContentResolver.registerProviderInternal("media", provider)
        val settings = AppSettingsRepository(context).apply { setUseBrowserSessions(false) }
        val started = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val engine = object : DownloadProcessingEngine {
            override fun download(media: ResolvedMedia, options: YtDlpDownloadOptions, onProgress: (YtDlpProgress) -> Unit,
                taskKey: String?): DownloadOutputSet {
                calls.incrementAndGet(); started.countDown()
                check(release.await(2, java.util.concurrent.TimeUnit.SECONDS))
                val directory = temporary.newFolder()
                return DownloadOutputSet(directory, listOf(DownloadArtifact(File(directory, "media.mp4").apply { writeText("media") }, sourceId = "media")))
            }
            override fun cleanup(outputs: DownloadOutputSet) { outputs.directory.deleteRecursively() }
            override fun cancelAll() = Unit
        }
        val media = ResolvedMedia("https://example.com/${UUID.randomUUID()}", MediaKind.VIDEO, backend = dev.qtremors.acqua.domain.MediaBackend.YT_DLP)
        val request = DownloadWorkRequest(media, media, media.url, 0, YtDlpDownloadOptions(skipArchived = true, recordHistory = false),
            publicationId = UUID.randomUUID().toString())
        val executor = DownloadExecutor(context, settings, HistoryRepository(context), HttpMediaClient()) { engine }
        val first = async { executor.execute(request, "first", {}, {}) }
        try {
            runCurrent()
            assertTrue(started.await(2, java.util.concurrent.TimeUnit.SECONDS))
            var secondTransferred = false
            val other = request.copy(publicationId = UUID.randomUUID().toString())
            val second = async { executor.execute(other, "second", {}, { secondTransferred = true }) }
            runCurrent()
            assertFalse(secondTransferred)
            release.countDown()
            val original = first.await()
            val duplicate = second.await()
            assertTrue(duplicate.skipped)
            assertEquals(original.uri, duplicate.uri)
            assertEquals(1, calls.get())
            assertEquals(1, provider.rows.size)
        } finally { release.countDown() }
    }

    private class DownloadsProvider(private val directory: File, private val failInsert: Int = -1) : ContentProvider() {
        val rows = mutableMapOf<Uri, ContentValues>()
        val files = mutableMapOf<Uri, File>()
        private var insertCount = 0
        override fun onCreate() = true
        override fun insert(uri: Uri, values: ContentValues?): Uri {
            if (++insertCount == failInsert) throw java.io.IOException("Disposable provider fault")
            val result = Uri.withAppendedPath(uri, (rows.size + 1).toString())
            rows[result] = ContentValues(values!!)
            files[result] = File(directory, "output-${rows.size}")
            return result
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
            ParcelFileDescriptor.open(files.getValue(uri), ParcelFileDescriptor.parseMode(mode))
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
            rows.getValue(uri).putAll(values!!)
            return 1
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            val columns = projection ?: rows.getValue(uri).keySet().toTypedArray()
            return MatrixCursor(columns).apply { addRow(columns.map { rows.getValue(uri).get(it) }.toTypedArray()) }
        }
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            rows.remove(uri)
            files.remove(uri)?.delete()
            return 1
        }
        override fun getType(uri: Uri): String? = rows[uri]?.getAsString(MediaStore.MediaColumns.MIME_TYPE)
    }
}
