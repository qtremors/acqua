package dev.qtremors.acqua.data.session

import android.content.Context
import android.webkit.CookieManager
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrowserDataManagerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val websites = SavedWebsiteRepository(context)
    private val sessions = InstagramSessionStore(context)
    private val settings = AppSettingsRepository(context)

    @Test fun `supported site removal waits for all callbacks and flushes before reporting completion`() {
        websites.save("Selected", "https://selected.example", null)
        websites.save("Other", "https://other.example", null)
        val removal = FakeRemoval(true)
        val events = removal.events
        val manager = BrowserDataManager(context, websites, sessions, settings, removal)
        var complete = false
        manager.clearWebsites(listOf("https://selected.example/path", "https://www.selected.example")) {
            events += "complete"
            complete = true
        }
        assertFalse(complete)
        assertEquals(2, websites.load().size)
        assertEquals(2, removal.pending.size)
        removal.pending.removeAt(1).invoke()
        assertFalse(complete)
        removal.pending.removeAt(0).invoke()
        assertTrue(complete)
        assertEquals(listOf("other.example"), SavedWebsiteRepository(context).load().map { it.host })
        assertEquals(listOf("flush", "complete"), events)
        assertFalse(removal.allCookiesRequested)
    }

    @Test fun `inline site callbacks and empty input complete exactly once`() {
        val removal = FakeRemoval(true).apply { inline = true }
        val manager = BrowserDataManager(context, websites, sessions, settings, removal)
        var completions = 0
        manager.clearWebsites(listOf("https://one.example", "https://two.example")) { completions++ }
        assertEquals(1, completions)
        assertEquals(1, removal.events.count { it == "flush" })
        manager.clearWebsites(listOf("about:blank", "not a URL")) { completions++ }
        assertEquals(2, completions)
        assertEquals(1, removal.events.count { it == "flush" })
    }

    @Test fun `legacy fallback clears hidden path host and domain cookies for all sites before completion`() {
        val cookies = CookieManager.getInstance()
        cookies.removeAllCookies(null)
        cookies.setCookie("https://selected.example/", "root=fixture; Path=/; Secure; HttpOnly")
        cookies.setCookie("https://selected.example/private/", "hidden=fixture; Path=/private; Secure")
        cookies.setCookie("https://selected.example/", "domain=fixture; Domain=.selected.example; Path=/")
        cookies.setCookie("https://other.example/", "other=fixture; Path=/")
        assertTrue(cookies.getCookie("https://selected.example/private/").contains("hidden=fixture"))
        assertTrue(cookies.getCookie("https://other.example/").contains("other=fixture"))
        websites.save("Selected", "https://selected.example", null)
        websites.save("Other", "https://other.example", null)
        val sessionPreferences = context.getSharedPreferences("acqua_secure_sessions", Context.MODE_PRIVATE)
        sessionPreferences.edit().putString("instagram_session", "fixture").commit()
        val removal = FakeRemoval(false)
        val manager = BrowserDataManager(context, websites, sessions, settings, removal)
        var completed = false
        manager.clearWebsites(listOf("https://selected.example")) { completed = true }
        assertFalse(completed)
        assertTrue(removal.allCookiesRequested)
        cookies.removeAllCookies { removal.pending.single().invoke() }
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertTrue(completed)
        assertNull(cookies.getCookie("https://selected.example/private/"))
        assertNull(cookies.getCookie("https://other.example/"))
        assertFalse(sessionPreferences.contains("instagram_session"))
        assertEquals(listOf("other.example"), SavedWebsiteRepository(context).load().map { it.host })
        assertEquals(listOf("flush"), removal.events)
    }

    @Test fun `scoped deletion preserves unrelated cached sessions and clears a selected Instagram session`() {
        val preferences = context.getSharedPreferences("acqua_secure_sessions", Context.MODE_PRIVATE)
        preferences.edit().putString("instagram_session", "fixture").commit()
        val removal = FakeRemoval(true).apply { inline = true }
        val manager = BrowserDataManager(context, websites, sessions, settings, removal)
        manager.clearWebsites(listOf("https://selected.example")) {}
        assertTrue(preferences.contains("instagram_session"))
        manager.clearWebsites(listOf("https://www.instagram.com")) {}
        assertFalse(preferences.contains("instagram_session"))
    }

    @Test fun `website clearing removes related saved profiles and identities but preserves other websites`() {
        val profiles = CookieProfileStore(context, FixtureCookieCipher())
        val selected = CookieProfile(java.util.UUID.randomUUID().toString(), "Selected", "https://www.selected.example/private", "Observed UA", System.currentTimeMillis(),
            listOf(SessionCookie("sid", "fixture"))).validated()
        val other = selected.copy(id = java.util.UUID.randomUUID().toString(), name = "Other", captureUrl = "https://other.example/private")
        profiles.put(selected); profiles.put(other)
        val identity = BrowserIdentityStore(context)
        identity.record(selected.captureUrl, selected.userAgent); identity.record(other.captureUrl, other.userAgent)
        val removal = FakeRemoval(true).apply { inline = true }
        BrowserDataManager(context, websites, sessions, settings, removal, profiles).clearWebsites(listOf("https://selected.example")) {}
        assertEquals(listOf(other), profiles.all())
        assertNull(identity.userAgent(selected.captureUrl))
        assertEquals(other.userAgent, identity.userAgent(other.captureUrl))
    }

    private class FakeRemoval(override val supportsSiteRemoval: Boolean) : SiteDataRemoval {
        val pending = mutableListOf<() -> Unit>()
        val events = mutableListOf<String>()
        var inline = false
        var allCookiesRequested = false
        override fun clearSite(origin: String, onComplete: () -> Unit) {
            if (inline) onComplete() else pending += onComplete
        }
        override fun clearAllCookies(onComplete: () -> Unit) { allCookiesRequested = true; pending += onComplete }
        override fun flushCookies() { events += "flush" }
    }
}
