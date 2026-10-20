package dev.qtremors.acqua.data.session

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.data.settings.AppSettingsRepository

class BrowserDataManager(
    context: Context,
    private val savedWebsites: SavedWebsiteRepository,
    private val instagramSessions: InstagramSessionStore,
    private val settings: AppSettingsRepository,
    private val siteDataRemoval: SiteDataRemoval = WebViewSiteDataRemoval(),
    private val cookieProfiles: CookieProfileStore = CookieProfileStore(context)
) {
    private val appContext = context.applicationContext
    val supportsSiteRemoval: Boolean get() = siteDataRemoval.supportsSiteRemoval

    fun clearWebsites(origins: Collection<String>, onComplete: () -> Unit) {
        val normalized = origins.mapNotNull(WebLink::origin).distinct()
        if (normalized.isEmpty()) return onComplete()

        val scoped = supportsSiteRemoval
        val finish = {
            cookieProfiles.removeSites(normalized)
            BrowserIdentityStore(appContext).remove(normalized)
            savedWebsites.remove(normalized)
            if (!scoped || normalized.any(WebLink::isInstagramHost)) instagramSessions.clear()
            siteDataRemoval.flushCookies()
            onComplete()
        }
        if (scoped) {
            // The extra count prevents an inline callback from finishing before every operation is submitted.
            val remaining = java.util.concurrent.atomic.AtomicInteger(normalized.size + 1)
            val completeOne = { if (remaining.decrementAndGet() == 0) finish() }
            normalized.forEach { origin -> siteDataRemoval.clearSite(origin, completeOne) }
            completeOne()
        } else {
            normalized.forEach { origin -> originVariants(origin).forEach(WebStorage.getInstance()::deleteOrigin) }
            siteDataRemoval.clearAllCookies(finish)
        }
    }

    @Suppress("DEPRECATION")
    fun clearAll(onComplete: () -> Unit) {
        savedWebsites.clear()
        cookieProfiles.clear()
        BrowserIdentityStore(appContext).clear()
        settings.setUseBrowserSessions(false)
        instagramSessions.clear()
        WebStorage.getInstance().deleteAllData()
        WebViewDatabase.getInstance(appContext).apply {
            clearHttpAuthUsernamePassword()
            clearFormData()
            clearUsernamePassword()
        }
        WebView(appContext).apply {
            clearCache(true)
            clearHistory()
            destroy()
        }
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            onComplete()
        }
    }

    private fun originVariants(origin: String): Set<String> {
        val host = WebLink.host(origin) ?: return emptySet()
        val baseHost = host.removePrefix("www.")
        return setOf(host, baseHost, "www.$baseHost").flatMapTo(mutableSetOf()) { candidate ->
            listOf("https://$candidate", "http://$candidate")
        }
    }
}
