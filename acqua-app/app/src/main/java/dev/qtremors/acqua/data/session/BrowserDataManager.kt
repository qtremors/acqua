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
    private val settings: AppSettingsRepository
) {
    private val appContext = context.applicationContext

    fun clearWebsites(origins: Collection<String>, onComplete: () -> Unit) {
        val normalized = origins.mapNotNull(WebLink::origin).distinct()
        if (normalized.isEmpty()) return onComplete()

        savedWebsites.remove(normalized)
        val cookieManager = CookieManager.getInstance()
        normalized.forEach { origin ->
            clearOriginCookies(cookieManager, origin)
            originVariants(origin).forEach(WebStorage.getInstance()::deleteOrigin)
        }
        if (normalized.any(WebLink::isInstagramHost)) instagramSessions.clear()
        cookieManager.flush()
        onComplete()
    }

    @Suppress("DEPRECATION")
    fun clearAll(onComplete: () -> Unit) {
        savedWebsites.clear()
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

    private fun clearOriginCookies(manager: CookieManager, origin: String) {
        val cookieNames = manager.getCookie(origin).orEmpty().split(';')
            .map { it.substringBefore('=').trim() }.filter(String::isNotEmpty).distinct()
        originVariants(origin).forEach { candidate ->
            val host = WebLink.host(candidate) ?: return@forEach
            cookieNames.forEach { name ->
                manager.setCookie(candidate, "$name=; Max-Age=0; Path=/")
                manager.setCookie(candidate, "$name=; Max-Age=0; Domain=.$host; Path=/")
            }
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
