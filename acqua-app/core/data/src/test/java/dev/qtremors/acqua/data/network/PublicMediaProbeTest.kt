package dev.qtremors.acqua.data.network

import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.MediaResolutionException
import dev.qtremors.acqua.domain.MediaResolutionFailure
import dev.qtremors.acqua.domain.MediaResolutionService
import dev.qtremors.acqua.domain.MediaResolver
import dev.qtremors.acqua.domain.ResolvedMedia
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.io.ByteArrayOutputStream
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PublicMediaProbeTest {
    @Test fun `public direct inspection uses its configured proxy without browser cookies`() {
        MockWebServer().use { proxy ->
            proxy.enqueue(MockResponse().setHeader("Content-Type", "video/mp4").setBody(Buffer().write(mp4)))
            val address = "http://127.0.0.1:${proxy.url("/").port}"
            val http = HttpMediaClient(networkOptions = { TransferNetworkOptions(proxy = address, forceIpv4 = true) })
            assertEquals(MediaKind.VIDEO, http.resolvePublicMedia("http://unresolvable.invalid/movie")!!.kind)
            val request = proxy.takeRequest(2, TimeUnit.SECONDS)!!
            assertTrue(request.requestLine.startsWith("GET http://unresolvable.invalid/movie "))
            assertNull(request.getHeader("Cookie"))
            assertEquals("bytes=0-65535", request.getHeader("Range"))
        }
    }

    @Test
    fun `public image video and audio resolve without browser sessions or filename hints`() = runBlocking {
        MockWebServer().use { server ->
            val http = HttpMediaClient()
            val service = service(http)
            val samples = listOf(
                Triple(jpeg, "image/jpeg", MediaKind.IMAGE),
                Triple(mp4, "video/mp4", MediaKind.VIDEO),
                Triple("ID3\u0004\u0000\u0000".toByteArray(), "audio/mpeg", MediaKind.AUDIO),
                Triple(mp4, "application/octet-stream", MediaKind.VIDEO)
            )
            samples.forEachIndexed { index, (bytes, mime, kind) ->
                server.enqueue(response(bytes, mime))
                val url = server.url("/asset?id=$index").toString()
                val resolved = service.resolve(url, browserSessionsEnabled = false) { _, _ -> error("Unexpected browser") }.single()
                assertEquals(kind, resolved.kind)
                assertEquals(MediaBackend.DIRECT, resolved.backend)
                assertEquals(bytes.size.toLong(), resolved.fileSize)
                assertEquals(url, resolved.url)
                assertNull(resolved.requestCookies)
                assertEquals(false, resolved.explicitBrowserSessionAuthorized)
                assertEquals("bytes=0-65535", server.takeRequest().getHeader("Range"))
            }
        }
    }

    @Test
    fun `range probe requires a complete prefix starting at zero with a known total`() {
        MockWebServer().use { server ->
            val http = HttpMediaClient()
            val prefix = jpeg + ByteArray(65_536 - jpeg.size)
            server.enqueue(response(prefix, "image/jpeg").setResponseCode(206).setHeader("Content-Range", "bytes 0-65535/1000000"))
            assertEquals(1_000_000L, http.resolvePublicMedia(server.url("/good").toString())?.fileSize)
            val ranges = listOf(null, "bytes 1-65536/1000000", "bytes 0-65535/*", "bytes 0-65535/100", "bytes 0-100/1000000")
            ranges.forEach { range ->
                server.enqueue(response(prefix, "image/jpeg").setResponseCode(206).apply {
                    if (range != null) setHeader("Content-Range", range)
                })
                assertNull(http.resolvePublicMedia(server.url("/bad").toString()))
            }
            server.enqueue(response(jpeg, "image/jpeg").setResponseCode(206).setHeader("Content-Range", "bytes 0-65535/1000000"))
            assertNull(http.resolvePublicMedia(server.url("/short").toString()))
        }
    }

    @Test
    fun `HTML manifests fragments and unsupported payloads cannot masquerade as media`() {
        MockWebServer().use { server ->
            val http = HttpMediaClient()
            listOf(
                response("<html>sign in</html>".toByteArray(), "image/jpeg"),
                response(jpeg, "text/html"),
                response("#EXTM3U\nsegment.ts".toByteArray(), "application/vnd.apple.mpegurl"),
                response("<MPD/>".toByteArray(), "application/dash+xml"),
                response("moof fragment".toByteArray(), "video/mp4"),
                response(byteArrayOf(0, 0, 0, 32) + "ftypisom".toByteArray(), "video/mp4"),
                response(jpeg, "image/jpeg").setHeader("Content-Range", "bytes 0-3/1000")
            ).forEach { response ->
                server.enqueue(response)
                assertNull(http.resolvePublicMedia(server.url("/fake.mp4").toString()))
            }
            val failure = assertThrows(MediaResolutionException::class.java) {
                http.resolvePublicMedia(server.url("/video?bytestart=10&byteend=20").toString())
            }
            assertEquals(MediaResolutionFailure.UNSUPPORTED_MEDIA, failure.failure)
        }
    }

    @Test
    fun `restricted links and network failures have distinct errors`() {
        MockWebServer().use { server ->
            val http = HttpMediaClient()
            for (code in listOf(401, 403, 503)) {
                server.enqueue(MockResponse().setResponseCode(code))
                val error = assertThrows(MediaResolutionException::class.java) {
                    runBlocking { service(http).resolve(server.url("/restricted").toString(), false) { _, _ -> error("Unexpected browser") } }
                }
                assertEquals(if (code == 503) MediaResolutionFailure.NETWORK else MediaResolutionFailure.SESSION_REQUIRED, error.failure)
            }
            server.enqueue(response(jpeg, "image/jpeg").setBodyDelay(200, TimeUnit.MILLISECONDS))
            val impatient = HttpMediaClient(OkHttpClient.Builder().readTimeout(30, TimeUnit.MILLISECONDS).build())
            assertEquals(MediaResolutionFailure.NETWORK, assertThrows(MediaResolutionException::class.java) {
                impatient.resolvePublicMedia(server.url("/timeout").toString())
            }.failure)
        }
    }

    @Test
    fun `public redirects never load cookies from a configured session jar`() {
        MockWebServer().use { origin ->
            MockWebServer().use { destination ->
                val cookieJar = object : CookieJar {
                    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
                    override fun loadForRequest(url: HttpUrl): List<Cookie> = error("Public probes must not load session cookies")
                }
                origin.enqueue(MockResponse().setResponseCode(302).setHeader("Set-Cookie", "session=secret")
                    .setHeader("Location", destination.url("/photo").toString().replace("localhost", "127.0.0.1")))
                destination.enqueue(response(jpeg, "image/jpeg"))
                val http = HttpMediaClient(OkHttpClient.Builder().cookieJar(cookieJar).build())
                assertEquals(MediaKind.IMAGE, http.resolvePublicMedia(origin.url("/photo").toString())?.kind)
                for (request in listOf(origin.takeRequest(), destination.takeRequest())) {
                    assertNull(request.getHeader("Cookie"))
                    assertNull(request.getHeader("Authorization"))
                }
            }
        }
    }

    @Test
    fun `inspection stops reading at 64 KiB even when the server ignores Range`() {
        MockWebServer().use { server ->
            val bytesRead = AtomicLong()
            var closed = false
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                val body = requireNotNull(response.body)
                val boundedSource = object : ForwardingSource(body.source()) {
                    override fun read(sink: Buffer, byteCount: Long): Long = super.read(sink, byteCount).also {
                        if (it > 0) bytesRead.addAndGet(it)
                    }
                    override fun close() { closed = true; super.close() }
                }.buffer()
                response.newBuilder().body(object : ResponseBody() {
                    override fun contentType() = body.contentType()
                    override fun contentLength() = body.contentLength()
                    override fun source() = boundedSource
                }).build()
            }.build()
            val payload = jpeg + ByteArray(1_000_000 - jpeg.size)
            server.enqueue(response(payload, "image/jpeg"))
            assertEquals(payload.size.toLong(), HttpMediaClient(client).resolvePublicMedia(server.url("/large").toString())?.fileSize)
            assertEquals(65_536L, bytesRead.get())
            assertTrue(closed)
        }
    }

    @Test
    fun `browser fallback is explicit and yt-dlp keeps the selected engine route`() = runBlocking {
        MockWebServer().use { server ->
            val http = HttpMediaClient()
            val service = service(http)
            server.enqueue(MockResponse().setBody("<html>page</html>"))
            val error = assertThrows(MediaResolutionException::class.java) {
                runBlocking { service.resolve(server.url("/page").toString(), true) { _, _ -> error("Unexpected browser") } }
            }
            assertEquals(MediaResolutionFailure.UNSUPPORTED_MEDIA, error.failure)
            server.enqueue(MockResponse().setResponseCode(403))
            val browserItem = ResolvedMedia("https://cdn.example/video.mp4", MediaKind.VIDEO)
            val browserService = MediaResolutionService(MediaResolver { emptyList() }, publicMediaProbe = http::resolvePublicMedia, inspectMedia = { it })
            val resolved = browserService.resolve(server.url("/page").toString(), true, allowBrowserFallback = true) { _, authorized ->
                assertTrue(authorized)
                listOf(browserItem)
            }
            assertEquals(listOf(browserItem), resolved)

            val ytDlpItem = browserItem.copy(backend = MediaBackend.YT_DLP)
            val ytDlpService = MediaResolutionService(MediaResolver { error("Unexpected source") }, MediaResolver { listOf(ytDlpItem) },
                publicMediaProbe = { error("Unexpected direct probe") }, inspectMedia = { error("Unexpected inspection") })
            assertEquals(listOf(ytDlpItem), ytDlpService.resolve(server.url("/asset").toString(), false, engine = DownloadEngine.YT_DLP) { _, _ -> error("Unexpected browser") })
            assertEquals(2, server.requestCount)
        }
    }

    private fun service(http: HttpMediaClient) = MediaResolutionService(
        sourceResolver = MediaResolver { error("Unexpected source resolver") },
        publicMediaProbe = http::resolvePublicMedia,
        inspectMedia = { error("Public media was already inspected") }
    )

    private fun response(bytes: ByteArray, mime: String) = MockResponse().setHeader("Content-Type", mime).setBody(Buffer().write(bytes))

    private val jpeg = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpg", it)
    }.toByteArray()
    private val mp4 = byteArrayOf(0, 0, 0, 16) + "ftypisom".toByteArray() +
        byteArrayOf(0, 0, 0, 0, 0, 0, 0, 8) + "mdat".toByteArray()
}
