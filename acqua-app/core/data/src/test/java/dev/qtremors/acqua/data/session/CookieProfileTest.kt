package dev.qtremors.acqua.data.session

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.downloader.DownloadOptionsCodec
import dev.qtremors.acqua.downloader.DownloadRequestStore
import dev.qtremors.acqua.downloader.DownloadWorkData
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CookieProfileTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val cipher = FixtureCookieCipher()
    private val store = CookieProfileStore(context, cipher)
    private val now = System.currentTimeMillis()
    @Before fun reset() {
        context.getSharedPreferences("cookie_profiles_v1", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("cookie_profile_snapshots_v1", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("browser_identity_v1", Context.MODE_PRIVATE).edit().clear().commit()
        AppSettingsRepository(context).setUseBrowserSessions(true)
    }

    @Test fun `available attributes preserve host domain path expiry security and partition context`() {
        val domain = BrowserCookieCapture.parseCookie("https://www.example.com/private/watch", "sid=private-value; Domain=.example.com; Path=/private; Secure; HttpOnly; SameSite=Lax; Expires=Wed, 09 Jun 2038 10:18:14 GMT")!!
        val host = BrowserCookieCapture.parseCookie("https://www.example.com/private/watch", "host=host-value; Path=/private; Secure")!!
        val partitioned = BrowserCookieCapture.parseCookie("https://www.example.com/private/watch", "partition=unrepresentable; Path=/private; Secure; Partitioned")!!
        val profile = fixture().copy(cookies = listOf(domain, host, partitioned)).validated()
        assertEquals(profile, CookieProfile.parse(profile.json()))
        assertEquals(listOf("sid", "host"), profile.matching("https://www.example.com/private/next".toHttpUrl()).map { it.name })
        assertEquals(listOf("sid"), profile.matching("https://other.example.com/private/next".toHttpUrl()).map { it.name })
        for (url in listOf("http://www.example.com/private/next", "https://www.example.com/public", "https://www.example.com/privately", "https://other.invalid/private"))
            assertTrue(profile.matching(url.toHttpUrl()).isEmpty())
        assertTrue(profile.netscape().first().startsWith("#HttpOnly_.example.com\tTRUE\t/private\tTRUE\t"))
        assertFalse(profile.netscape().any { it.contains("unrepresentable") })
        val expired = BrowserCookieCapture.parseCookie("https://www.example.com/private/watch", "old=expired; Path=/private; Expires=Wed, 09 Jun 2020 10:18:14 GMT")!!
        assertTrue(profile.copy(cookies = listOf(expired)).netscape().isEmpty())
        assertNull(BrowserCookieCapture.parseCookie(profile.captureUrl, "bad=private; Domain=other.invalid"))
        assertNull(BrowserCookieCapture.parseCookie(profile.captureUrl, "bad=private\nInjected: value"))
    }

    @Test fun `older captures retain unknown attributes and use conservative scope with a local expiry`() {
        val profile = fixture().copy(cookies = listOf(SessionCookie("sid", "fallback-value")))
        assertFalse(profile.cookies.single().attributesAvailable)
        assertEquals(profile, CookieProfile.parse(profile.json()))
        assertEquals(1, profile.matching(profile.captureUrl.toHttpUrl(), now).size)
        for (url in listOf("https://other.example.com/private/watch", "https://www.example.com/private/other", "http://www.example.com/private/watch"))
            assertTrue(profile.matching(url.toHttpUrl(), now).isEmpty())
        assertTrue(profile.matching(profile.captureUrl.toHttpUrl(), now + 24 * 60 * 60 * 1000L).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { profile.copy(cookies = List(201) { SessionCookie("name$it", "value") }).validated() }
        assertThrows(IllegalArgumentException::class.java) { profile.copy(userAgent = "Invalid\nUA").validated() }
    }

    @Test fun `capture pairs observed cookies with the actual browser agent in both feature branches`() {
        val identity = BrowserIdentityStore(context)
        identity.record("https://www.example.com/private/watch?token=not-retained", "Observed Browser UA")
        assertEquals("https://www.example.com/private/watch", identity.lastUrl())
        val full = BrowserCookieCapture(context, { listOf("sid=full; Path=/private; Secure; HttpOnly") }, { error("Unexpected fallback") })
            .capture("Full", "https://www.example.com/private/watch")
        assertEquals("Observed Browser UA", full.userAgent)
        assertTrue(full.cookies.single().attributesAvailable)
        val older = BrowserCookieCapture(context, { null }, { "sid=older; token=second" }).capture("Older", full.captureUrl)
        assertEquals(2, older.cookies.size)
        assertTrue(older.cookies.all { !it.attributesAvailable })
        identity.record(full.captureUrl, "Observed Desktop UA")
        assertEquals("Observed Browser UA", full.userAgent)
        assertEquals("Observed Desktop UA", BrowserCookieCapture(context, { null }, { "sid=new" }).capture("New", full.captureUrl).userAgent)
        identity.remove(listOf("https://example.com"))
        assertNull(identity.lastUrl())
        assertNull(identity.userAgent(full.captureUrl))
        assertThrows(CookieProfileException::class.java) { BrowserCookieCapture(context, { null }, { "sid=older" }).capture("Missing", full.captureUrl) }
    }

    @Test fun `encrypted profiles isolate names refreshes snapshots authorization and deletion`() {
        val original = fixture()
        store.put(original)
        val accepted = store.accepted(original.id!!, original.captureUrl, fingerprint = original.fingerprint)
        assertFalse(context.getSharedPreferences("cookie_profiles_v1", Context.MODE_PRIVATE).all.values.joinToString().contains("private-value"))
        assertEquals(original, store.restore(accepted))
        val refreshed = original.copy(cookies = original.cookies.map { it.copy(value = "different-account") }, capturedAt = now + 1)
        store.put(refreshed)
        assertThrows(CookieProfileException::class.java) { store.accepted(original.id!!, original.captureUrl, original.json(), original.fingerprint) }
        assertEquals(original, store.restore(accepted))
        store.requireAuthorized(original, original.captureUrl)
        val other = refreshed.copy(id = UUID.randomUUID().toString(), name = "Other")
        store.put(other)
        store.remove(original.id!!)
        assertThrows(CookieProfileException::class.java) { store.requireAuthorized(original, original.captureUrl) }
        assertEquals(other, store.get(other.id!!))
        assertThrows(CookieProfileException::class.java) { store.accepted(other.id!!, "https://outsider.invalid/") }
        AppSettingsRepository(context).setUseBrowserSessions(false)
        assertThrows(CookieProfileException::class.java) { store.accepted(other.id!!, other.captureUrl) }
        store.clear()
        assertTrue(store.all().isEmpty())
        assertThrows(CookieProfileException::class.java) { store.restore(accepted) }
    }

    @Test fun `exports preserve available attributes and refuse a changed snapshot without echoing cookies`() {
        val profile = fixture()
        store.put(profile)
        val uri = Uri.parse("content://fixture/export")
        val output = java.io.ByteArrayOutputStream()
        shadowOf(context.contentResolver).registerOutputStream(uri, output)
        store.export(profile.id!!, profile.capturedAt, uri, profile.fingerprint)
        assertEquals(profile, CookieProfile.parse(output.toString(Charsets.UTF_8.name())))
        val unused = java.io.ByteArrayOutputStream()
        shadowOf(context.contentResolver).registerOutputStream(uri, unused)
        val error = assertThrows(CookieProfileException::class.java) { store.export(profile.id!!, profile.capturedAt, uri, "changed") }
        assertFalse(error.message.orEmpty().contains("private-value"))
        assertEquals(0, unused.size())
    }

    @Test fun `durable queued authorization is encrypted frozen and separate from scheduler extras`() {
        val profile = fixture()
        store.put(profile)
        val options = YtDlpDownloadOptions(cookieProfileId = profile.id, cookieProfileFingerprint = profile.fingerprint)
        val media = ResolvedMedia(profile.captureUrl, MediaKind.VIDEO, backend = MediaBackend.YT_DLP, authorizedSession = profile.json())
        val data = DownloadWorkData.processedRequest(media, media, options, media.url)
        assertFalse(data.keyValueMap.values.joinToString().contains("private-value"))
        val id = UUID.randomUUID()
        val requests = DownloadRequestStore(context, store)
        requests.put(id, data, authorization = store.accepted(profile.id!!, media.url, media.authorizedSession, options.cookieProfileFingerprint))
        val reference = androidx.work.Data.Builder().putString(DownloadWorkData.KEY_REQUEST_REF, id.toString()).build()
        store.put(profile.copy(cookies = profile.cookies.map { it.copy(value = "refreshed-value") }))
        val restored = DownloadRequestStore(context, store).resolve(reference)!!
        assertEquals(profile, CookieProfile.parse(restored.media.authorizedSession!!))
        assertEquals(profile.userAgent, restored.media.requestUserAgent)
        assertEquals(options, restored.options)
        assertEquals(options, DownloadOptionsCodec.decode(DownloadOptionsCodec.encode(options), YtDlpDownloadOptions()))
        assertFalse(context.getSharedPreferences("download_requests_v1", Context.MODE_PRIVATE).getString(id.toString(), "")!!.contains("private-value"))
    }

    @Test fun `native cookie files retain attributes and are unique scoped and cleaned after process restart`() {
        val profile = fixture()
        store.put(profile)
        val transfer = BrowserSessionTransfer(context, store)
        val first = transfer.create(profile.captureUrl, true, profile.json())!!
        val second = transfer.create(profile.captureUrl, true, profile.json())!!
        assertTrue(first.file != second.file)
        assertTrue(first.file.readText().contains("www.example.com\tFALSE\t/private\tTRUE"))
        assertEquals(profile.userAgent, first.userAgent)
        assertNull(transfer.create(profile.captureUrl, false, profile.json()))
        assertThrows(CookieProfileException::class.java) { transfer.create("https://outsider.invalid/", true, profile.json()) }
        dev.qtremors.acqua.downloader.YtDlpTemporaryFiles.cleanupAfterProcessRestart(context)
        assertFalse(first.file.exists()); assertFalse(second.file.exists())
        AppSettingsRepository(context).setUseBrowserSessions(false)
        assertThrows(CookieProfileException::class.java) { transfer.create(profile.captureUrl, true, profile.json()) }
    }

    @Test fun `accepted batches share an encrypted snapshot and pruning retains referenced authorizations`() {
        val profile = fixture()
        store.put(profile)
        val one = store.accepted(profile.id!!, profile.captureUrl)
        val two = store.accepted(profile.id, profile.captureUrl)
        assertEquals(one, two)
        val snapshots = context.getSharedPreferences("cookie_profile_snapshots_v1", Context.MODE_PRIVATE)
        assertEquals(1, snapshots.all.size)
        assertFalse(snapshots.all.values.joinToString().contains("private-value"))
        store.pruneSnapshots(setOf(one), System.currentTimeMillis() + 25 * 60 * 60 * 1000L)
        assertEquals(profile, store.restore(one))
        store.pruneSnapshots(emptySet(), System.currentTimeMillis() + 25 * 60 * 60 * 1000L)
        assertThrows(CookieProfileException::class.java) { store.restore(one) }
        assertEquals(profile, store.get(profile.id))
    }

    @Test fun `native host fixture uses the production cookie rows and captured user agent`() {
        val profile = CookieProfile(UUID.randomUUID().toString(), "Native fixture", "http://127.0.0.1/private/start", "Observed Fixture Browser UA", now,
            listOf(SessionCookie("sid", "native-fixture", "127.0.0.1", true, "/private", now + 86400000, false, true))).validated()
        java.io.File("build/cookie-profile-fixture.json").apply { parentFile.mkdirs(); writeText(org.json.JSONObject()
            .put("rows", org.json.JSONArray(profile.netscape())).put("userAgent", profile.userAgent).toString()) }
    }

    private fun fixture() = CookieProfile(UUID.randomUUID().toString(), "Fixture", "https://www.example.com/private/watch", "Observed Browser UA", now,
        listOf(SessionCookie("sid", "private-value", "www.example.com", true, "/private", now + 86400000, true, true, "Lax", false))).validated()
}

class FixtureCookieCipher : CookieProfileCipher {
    private var key = javax.crypto.KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()
    override fun encrypt(value: String): String {
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply { init(javax.crypto.Cipher.ENCRYPT_MODE, key) }
        return java.util.Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()))
    }
    override fun decrypt(value: String): String {
        val bytes = java.util.Base64.getDecoder().decode(value)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(javax.crypto.Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }
    override fun erase() { key = javax.crypto.KeyGenerator.getInstance("AES").apply { init(128) }.generateKey() }
}
