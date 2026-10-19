package dev.qtremors.acqua.data.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.data.network.HttpMediaClient
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.MediaResolutionService
import dev.qtremors.acqua.domain.MediaResolver
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.downloader.DownloadExecutor
import dev.qtremors.acqua.downloader.DownloadOutputSet
import dev.qtremors.acqua.downloader.DownloadProcessingEngine
import dev.qtremors.acqua.downloader.DownloadRequestStore
import dev.qtremors.acqua.downloader.DownloadWorkCoordinator
import dev.qtremors.acqua.downloader.DownloadWorkData
import dev.qtremors.acqua.downloader.DownloadWorkRequest
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions
import dev.qtremors.acqua.downloader.YtDlpProgress
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CookieProfileTransferTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = CookieProfileStore(context, FixtureCookieCipher())
    private val image = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+cK4YAAAAASUVORK5CYII=")
    @Before fun reset() {
        context.getSharedPreferences("cookie_profiles_v1", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("cookie_profile_snapshots_v1", Context.MODE_PRIVATE).edit().clear().commit()
        AppSettingsRepository(context).setUseBrowserSessions(true)
    }

    @Test fun `redirects require the saved agent and enforce cookie paths hosts and expiry`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/private/next")))
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/public")))
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/outside").newBuilder().host("127.0.0.1").build()))
            server.enqueue(response())
            val profile = profile(server.url("/private/start").toString(), "first")
            val http = HttpMediaClient().withSession(profile.json()).withNetworkOptions(dev.qtremors.acqua.domain.TransferNetworkOptions(forceIpv4 = true))
            val output = ByteArrayOutputStream()
            http.downloadToStreamCancellable(ResolvedMedia(profile.captureUrl, MediaKind.IMAGE), output)
            assertEquals(image.size, output.size())
            val requests = (1..4).map { server.takeRequest(2, TimeUnit.SECONDS)!! }
            assertTrue(requests.all { it.getHeader("User-Agent") == profile.userAgent })
            assertEquals("sid=first", requests[0].getHeader("Cookie"))
            assertEquals("sid=first", requests[1].getHeader("Cookie"))
            assertNull(requests[2].getHeader("Cookie"))
            assertNull(requests[3].getHeader("Cookie"))
            server.enqueue(response())
            http.withSession(profile.copy(cookies = profile.cookies.map { it.copy(expiresAt = 1L) }).json())
                .downloadToStream(ResolvedMedia(profile.captureUrl, MediaKind.IMAGE), ByteArrayOutputStream())
            assertNull(server.takeRequest().getHeader("Cookie"))
        }
    }

    @Test fun `parallel sessions never share cookie jars and public probes stay unauthenticated`() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = response()
            }
            val first = profile(server.url("/private/first").toString(), "first")
            val second = profile(server.url("/private/second").toString(), "second")
            val base = HttpMediaClient()
            coroutineScope {
                val one = async(kotlinx.coroutines.Dispatchers.IO) { base.withSession(first.json()).downloadToStream(
                    ResolvedMedia(first.captureUrl, MediaKind.IMAGE), ByteArrayOutputStream()) }
                val two = async(kotlinx.coroutines.Dispatchers.IO) { base.withSession(second.json()).downloadToStream(
                    ResolvedMedia(second.captureUrl, MediaKind.IMAGE), ByteArrayOutputStream()) }
                one.await(); two.await()
            }
            val requests = (1..2).map { server.takeRequest(2, TimeUnit.SECONDS)!! }.associateBy { it.path }
            assertEquals("sid=first", requests["/private/first"]!!.getHeader("Cookie"))
            assertEquals("sid=second", requests["/private/second"]!!.getHeader("Cookie"))
            assertTrue(base.withSession(first.json()).resolvePublicMedia(first.captureUrl) != null)
            val public = server.takeRequest()
            assertNull(public.getHeader("Cookie"))
            assertFalse(public.getHeader("User-Agent") == first.userAgent)
        }
    }

    @Test fun `selected session inspection never falls back to the current browser or public resolver`() = runBlocking {
        val profile = profile("https://example.com/private/video", "first")
        var calls = 0
        val service = MediaResolutionService(sourceResolver = MediaResolver { error("Unexpected source") },
            ytDlpResolver = MediaResolver { error("Unexpected public inspection") },
            profileResolver = { url, id, engine ->
                assertEquals(profile.id, id); assertEquals(DownloadEngine.YT_DLP, engine); calls++
                listOf(ResolvedMedia(url, MediaKind.VIDEO, backend = MediaBackend.YT_DLP, authorizedSession = profile.json(), requestUserAgent = profile.userAgent))
            }, inspectMedia = { error("Unexpected direct inspector") })
        val items = service.resolveConfigured(profile.captureUrl, true, DownloadEngine.YT_DLP, profile.id) { _, _ -> error("Unexpected browser") }
        assertEquals(profile.json(), items.single().authorizedSession)
        assertEquals(1, calls)
        try { service.resolveConfigured(profile.captureUrl, false, DownloadEngine.YT_DLP, profile.id) { _, _ -> error("Unexpected browser") }; error("Expected denial") }
        catch (_: dev.qtremors.acqua.domain.MediaResolutionException) { assertEquals(1, calls) }
    }

    @Test fun `selected native failures do not retry with another account and revoked sessions never start`() = runBlocking {
        val profile = profile("https://example.com/private/video", "first")
        store.put(profile)
        val options = YtDlpDownloadOptions(cookieProfileId = profile.id, cookieProfileFingerprint = profile.fingerprint)
        val media = ResolvedMedia(profile.captureUrl, MediaKind.VIDEO, backend = MediaBackend.YT_DLP, authorizedSession = profile.json(),
            requestUserAgent = profile.userAgent, explicitBrowserSessionAuthorized = true)
        var calls = 0
        val execution = DownloadExecutor(context, AppSettingsRepository(context), dev.qtremors.acqua.data.history.HistoryRepository(context), HttpMediaClient(), profiles = store,
            engineFactory = { object : DownloadProcessingEngine {
                override fun download(media: ResolvedMedia, options: YtDlpDownloadOptions, onProgress: (YtDlpProgress) -> Unit, taskKey: String?): DownloadOutputSet {
                    calls++; assertEquals(profile.json(), media.authorizedSession); assertTrue(media.explicitBrowserSessionAuthorized)
                    throw CookieProfileException()
                }
                override fun cleanup(outputs: DownloadOutputSet) = Unit
                override fun cancelAll() = Unit
            } })
        val request = DownloadWorkRequest(media, media, media.url, 0, options)
        try { execution.execute(request, UUID.randomUUID().toString(), {}, {}); error("Expected failure") }
        catch (_: CookieProfileException) { assertEquals(1, calls) }
        store.remove(profile.id!!)
        try { execution.execute(request, UUID.randomUUID().toString(), {}, {}); error("Expected revocation") }
        catch (_: CookieProfileException) { assertEquals(1, calls) }
    }

    @Test @Config(sdk = [33]) fun `coordinator snapshots survive refresh and retry without plaintext scheduler extras`() {
        val configuration = androidx.work.Configuration.Builder().setExecutor(androidx.work.testing.SynchronousExecutor())
            .setTaskExecutor(androidx.work.testing.SynchronousExecutor()).build()
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        val profile = profile("https://example.com/private/video", "first")
        store.put(profile)
        val coordinator = DownloadWorkCoordinator(context, store)
        val media = ResolvedMedia(profile.captureUrl, MediaKind.VIDEO, backend = MediaBackend.YT_DLP, authorizedSession = profile.json())
        val options = YtDlpDownloadOptions(cookieProfileId = profile.id, cookieProfileFingerprint = profile.fingerprint)
        val id = coordinator.enqueue(media, media, options, media.url)
        val input = DownloadRequestStore(context, store).input(id)!!
        assertFalse(input.keyValueMap.values.joinToString().contains("sid=first"))
        store.put(profile.copy(cookies = profile.cookies.map { it.copy(value = "second") }))
        androidx.work.WorkManager.getInstance(context).cancelWorkById(id).result.get()
        val retry = coordinator.retry(id)!!
        val restored = DownloadRequestStore(context, store).resolve(androidx.work.Data.Builder().putString(DownloadWorkData.KEY_REQUEST_REF, retry.toString()).build())!!
        assertEquals(profile.json(), restored.media.authorizedSession)
        assertEquals(options, restored.options)
        assertThrows(CookieProfileException::class.java) { coordinator.enqueue(media, media, options, media.url) }
        coordinator.cancel(retry)
    }

    private fun profile(url: String, value: String): CookieProfile {
        val parsed = url.toHttpUrl()
        return CookieProfile(UUID.randomUUID().toString(), value, url, "Saved Browser UA $value", System.currentTimeMillis(), listOf(
            SessionCookie("sid", value, parsed.host, true, "/private", System.currentTimeMillis() + 86400000, false, true, "Lax", false))).validated()
    }
    private fun response(): MockResponse = MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(image))
}
