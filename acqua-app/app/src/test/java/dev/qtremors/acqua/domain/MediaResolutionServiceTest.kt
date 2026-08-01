package dev.qtremors.acqua.domain

import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.feature.downloader.isAudioPreview
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class MediaResolutionServiceTest {
    @Test
    fun `yt-dlp preview follows selected output type`() {
        val video = ResolvedMedia("https://example.com/video", MediaKind.VIDEO, backend = MediaBackend.YT_DLP)

        assertTrue(video.isAudioPreview(DownloadContentType.AUDIO))
        assertEquals(false, video.isAudioPreview(DownloadContentType.VIDEO))
    }

    @Test
    fun `known source is resolved and inspected`() = runBlocking {
        val raw = ResolvedMedia("https://cdn.test/photo", MediaKind.IMAGE)
        val service = MediaResolutionService(
            sourceResolver = MediaResolver { listOf(raw) },
            inspectMedia = { it.copy(mimeType = "image/png", fileExtension = "png") }
        )

        val result = service.resolve("https://instagram.com/p/abc", false) { _, _ -> emptyList() }

        assertEquals(
            listOf(raw.copy(referer = "https://instagram.com/p/abc", mimeType = "image/png", fileExtension = "png")),
            result
        )
    }

    @Test
    fun `yt-dlp selection routes YouTube links directly to yt-dlp`() = runBlocking {
        var nativeCalled = false
        val ytDlpItem = ResolvedMedia(
            "https://youtube.com/watch?v=abc",
            MediaKind.VIDEO,
            backend = MediaBackend.YT_DLP
        )
        val service = MediaResolutionService(
            sourceResolver = MediaResolver { nativeCalled = true; emptyList() },
            ytDlpResolver = MediaResolver { listOf(ytDlpItem) },
            inspectMedia = { error("yt-dlp results must not be inspected as direct files") }
        )

        val result = service.resolve(
            "https://youtu.be/abc",
            browserSessionsEnabled = false,
            engine = DownloadEngine.YT_DLP
        ) { _, _ -> emptyList() }

        assertEquals(false, nativeCalled)
        assertEquals(listOf(ytDlpItem), result)
    }

    @Test
    fun `Acqua is the default and requires explicit yt-dlp selection for YouTube`() {
        var ytDlpCalled = false
        val service = MediaResolutionService(
            sourceResolver = MediaResolver { emptyList() },
            ytDlpResolver = MediaResolver { ytDlpCalled = true; emptyList() },
            inspectMedia = { it }
        )

        val error = assertThrows(MediaResolutionException::class.java) {
            runBlocking { service.resolve("https://youtu.be/abc", false) { _, _ -> emptyList() } }
        }

        assertEquals(MediaResolutionFailure.ENGINE_REQUIRED, error.failure)
        assertEquals(false, ytDlpCalled)
    }

    @Test
    fun `YouTube browser cookies are forwarded only when session use is authorized`() = runBlocking {
        var sessionAllowed = false
        val resolver = object : SessionAwareMediaResolver {
            override fun resolve(url: String, allowBrowserSession: Boolean): List<ResolvedMedia> {
                sessionAllowed = allowBrowserSession
                return listOf(ResolvedMedia(url, MediaKind.VIDEO, backend = MediaBackend.YT_DLP))
            }
        }
        val service = MediaResolutionService(
            sourceResolver = MediaResolver { emptyList() },
            ytDlpResolver = resolver,
            inspectMedia = { it }
        )

        service.resolve(
            "https://music.youtube.com/watch?v=abc",
            browserSessionsEnabled = true,
            engine = DownloadEngine.YT_DLP
        ) { _, _ -> emptyList() }

        assertEquals(true, sessionAllowed)
    }

    @Test
    fun `acqua selection never probes yt-dlp`() = runBlocking {
        val direct = ResolvedMedia("https://cdn.test/post.jpg", MediaKind.IMAGE)
        var ytDlpCalled = false
        val service = MediaResolutionService(
            sourceResolver = MediaResolver { listOf(direct) },
            ytDlpResolver = MediaResolver {
                ytDlpCalled = true
                error("yt-dlp should not be called")
            },
            inspectMedia = { it }
        )

        val result = service.resolve(
            "https://instagram.com/p/abc",
            browserSessionsEnabled = false,
            engine = DownloadEngine.ACQUA
        ) { _, _ -> emptyList() }

        assertEquals(false, ytDlpCalled)
        assertEquals(listOf(direct.copy(referer = "https://instagram.com/p/abc")), result)
    }

    @Test
    fun `yt-dlp selection bypasses acqua extraction`() = runBlocking {
        var acquaCalled = false
        val ytDlpItem = ResolvedMedia(
            "https://example.com/watch/abc",
            MediaKind.VIDEO,
            backend = MediaBackend.YT_DLP
        )
        val service = MediaResolutionService(
            sourceResolver = MediaResolver {
                acquaCalled = true
                emptyList()
            },
            ytDlpResolver = MediaResolver { listOf(ytDlpItem) },
            inspectMedia = { it }
        )

        val result = service.resolve(
            "https://example.com/watch/abc",
            browserSessionsEnabled = false,
            engine = DownloadEngine.YT_DLP
        ) { _, _ -> emptyList() }

        assertEquals(false, acquaCalled)
        assertEquals(listOf(ytDlpItem), result)
    }

    @Test
    fun `generic pages require browser extraction`() {
        val service = MediaResolutionService(MediaResolver { emptyList() }, inspectMedia = { it })

        assertThrows(MediaResolutionException::class.java) {
            runBlocking { service.resolve("https://example.com/page", false) { _, _ -> emptyList() } }
        }
    }

    @Test
    fun `browser resolver is used after known source failure`() = runBlocking {
        val browserItem = ResolvedMedia("https://cdn.test/video.mp4", MediaKind.VIDEO)
        var explicitlyAuthorized = true
        val service = MediaResolutionService(MediaResolver { error("network unavailable") }, inspectMedia = { it })

        val result = service.resolve("https://instagram.com/p/abc", true) { _, explicitAuthorization ->
            explicitlyAuthorized = explicitAuthorization
            listOf(browserItem)
        }

        assertEquals(false, explicitlyAuthorized)
        assertEquals(listOf(browserItem), result)
    }

    @Test
    fun `largest complete reel variant is selected`() = runBlocking {
        val small = ResolvedMedia("https://cdn.test/small.mp4", MediaKind.VIDEO, width = 480, height = 854, fileSize = 3_000)
        val large = ResolvedMedia("https://cdn.test/large.mp4", MediaKind.VIDEO, width = 1080, height = 1920, fileSize = 2_000)
        val service = MediaResolutionService(MediaResolver { listOf(small, large) }, inspectMedia = { it })

        val result = service.resolve("https://instagram.com/reel/abc", false) { _, _ -> emptyList() }

        assertEquals(listOf(large.copy(referer = "https://instagram.com/reel/abc")), result)
    }

    @Test
    fun `explicit browser download bypasses source resolution when sessions are disabled`() = runBlocking {
        var sourceCalled = false
        var explicitlyAuthorized = false
        val browserItem = ResolvedMedia("https://cdn.test/private.mp4", MediaKind.VIDEO)
        val service = MediaResolutionService(
            MediaResolver {
                sourceCalled = true
                emptyList()
            },
            inspectMedia = { it }
        )

        val result = service.resolve(
            "https://instagram.com/reel/private",
            browserSessionsEnabled = false,
            forceBrowserResolution = true
        ) { _, explicitSessionAuthorization ->
            explicitlyAuthorized = explicitSessionAuthorization
            listOf(browserItem)
        }

        assertEquals(false, sourceCalled)
        assertEquals(true, explicitlyAuthorized)
        assertEquals(listOf(browserItem), result)
    }

    @Test
    fun `invalid source candidates fall back to browser when sessions are enabled`() = runBlocking {
        val sourceItem = ResolvedMedia("https://cdn.test/expired.mp4", MediaKind.VIDEO)
        val browserItem = ResolvedMedia("https://cdn.test/current.mp4", MediaKind.VIDEO)
        val service = MediaResolutionService(
            sourceResolver = MediaResolver { listOf(sourceItem) },
            inspectMedia = { it.takeIf { candidate -> candidate.url == browserItem.url } }
        )

        val result = service.resolve("https://instagram.com/reel/private", true) { _, _ ->
            listOf(browserItem)
        }

        assertEquals(listOf(browserItem), result)
    }

    @Test
    fun `candidate inspection is deduplicated capped and bounded`() = runBlocking {
        val active = AtomicInteger()
        val maximumActive = AtomicInteger()
        val inspected = AtomicInteger()
        val candidates = (0 until 30).map {
            ResolvedMedia("https://cdn.test/$it.jpg", MediaKind.IMAGE)
        } + ResolvedMedia("https://cdn.test/0.jpg", MediaKind.IMAGE)
        val service = MediaResolutionService(
            sourceResolver = MediaResolver { candidates },
            inspectMedia = { item ->
                inspected.incrementAndGet()
                val concurrent = active.incrementAndGet()
                maximumActive.updateAndGet { previous -> maxOf(previous, concurrent) }
                Thread.sleep(5)
                active.decrementAndGet()
                item
            }
        )

        val result = service.resolve("https://instagram.com/p/bounded", false) { _, _ -> emptyList() }

        assertEquals(24, inspected.get())
        assertEquals(24, result.size)
        assertTrue(maximumActive.get() <= 4)
    }
}
