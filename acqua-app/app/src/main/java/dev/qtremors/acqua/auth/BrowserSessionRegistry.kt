package dev.qtremors.acqua.auth

import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import org.json.JSONObject
import java.io.File

object BrowserSessionRegistry {
    private const val PREFS_NAME = "acqua_browser_sessions"
    private const val KEY_LOGIN_ENTRIES = "login_entries"
    private const val LEGACY_KEY_LOGIN_ORIGINS = "login_origins"
    private const val KEY_LAST_ORIGIN = "last_origin"
    private const val ICON_DIRECTORY = "website_login_icons"

    data class WebsiteLogin(
        val name: String,
        val host: String,
        val origin: String,
        val iconFile: File?
    )

    fun record(context: Context, url: String) {
        val host = WebLink.host(url) ?: return
        if (host == "acqua.local") return
        val origin = WebLink.origin(url) ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_LAST_ORIGIN, origin)
            .apply()
    }

    fun saveWebsiteLogin(context: Context, name: String, url: String, icon: Bitmap?) {
        val origin = WebLink.origin(url) ?: return
        val host = WebLink.host(origin)?.removePrefix("www.") ?: return
        val displayName = name.trim().take(40).ifBlank { defaultName(host) }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val entries = websiteLogins(context)
            .filterNot { it.host == host }
            .map { encodeLogin(it.name, it.origin) }
            .toMutableSet()
            .apply { add(encodeLogin(displayName, origin)) }

        prefs.edit()
            .putStringSet(KEY_LOGIN_ENTRIES, entries)
            .remove(LEGACY_KEY_LOGIN_ORIGINS)
            .putString(KEY_LAST_ORIGIN, origin)
            .apply()

        if (icon != null) {
            runCatching {
                iconDirectory(context).mkdirs()
                iconFile(context, host).outputStream().use { stream ->
                    icon.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
            }
        }
    }

    fun updateWebsiteIcon(context: Context, url: String, icon: Bitmap) {
        val host = WebLink.host(url)?.removePrefix("www.") ?: return
        websiteLogins(context).firstOrNull { it.host == host }?.let { login ->
            saveWebsiteLogin(context, login.name, login.origin, icon)
        }
    }

    fun websiteLogins(context: Context): List<WebsiteLogin> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedEntries = prefs.getStringSet(KEY_LOGIN_ENTRIES, emptySet()).orEmpty()
        val decodedEntries = if (savedEntries.isNotEmpty()) {
            savedEntries.mapNotNull(::decodeLogin)
        } else {
            prefs.getStringSet(LEGACY_KEY_LOGIN_ORIGINS, emptySet()).orEmpty().mapNotNull { origin ->
                WebLink.origin(origin)?.let { normalized -> defaultName(WebLink.host(normalized).orEmpty()) to normalized }
            }
        }

        return decodedEntries
            .mapNotNull { (name, savedOrigin) ->
                val origin = WebLink.origin(savedOrigin) ?: return@mapNotNull null
                val host = WebLink.host(origin)?.removePrefix("www.") ?: return@mapNotNull null
                WebsiteLogin(
                    name = name.ifBlank { defaultName(host) },
                    host = host,
                    origin = origin,
                    iconFile = iconFile(context, host).takeIf(File::isFile)
                )
            }
            .distinctBy(WebsiteLogin::host)
            .sortedBy { it.name.lowercase() }
    }

    fun lastOrigin(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_ORIGIN, null)
            ?.let(WebLink::normalize)

    fun clearWebsiteData(context: Context, origins: Collection<String>, onComplete: () -> Unit) {
        val normalizedOrigins = origins.mapNotNull(WebLink::origin).distinct()
        if (normalizedOrigins.isEmpty()) {
            onComplete()
            return
        }

        val selectedHosts = normalizedOrigins.mapNotNull(WebLink::host)
            .map { it.removePrefix("www.") }
            .toSet()
        val remainingLogins = websiteLogins(context).filterNot { it.host in selectedHosts }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet(
                KEY_LOGIN_ENTRIES,
                remainingLogins.map { encodeLogin(it.name, it.origin) }.toSet()
            )
            .apply()

        val cookieManager = CookieManager.getInstance()
        normalizedOrigins.forEach { origin ->
            clearOriginCookies(cookieManager, origin)
            originVariants(origin).forEach(WebStorage.getInstance()::deleteOrigin)
        }
        selectedHosts.forEach { host -> iconFile(context, host).delete() }
        if (normalizedOrigins.any(WebLink::isInstagramHost)) SecureSessionStore.clear(context)
        cookieManager.flush()
        onComplete()
    }

    @Suppress("DEPRECATION")
    fun clearAll(context: Context, onComplete: () -> Unit) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("acqua_prefs", Context.MODE_PRIVATE)
            .edit().remove("acqua_use_session_cookies").apply()
        SecureSessionStore.clear(context)
        iconDirectory(context).deleteRecursively()
        WebStorage.getInstance().deleteAllData()
        WebViewDatabase.getInstance(context).apply {
            clearHttpAuthUsernamePassword()
            clearFormData()
            clearUsernamePassword()
        }
        WebView(context).apply {
            clearCache(true)
            clearHistory()
            destroy()
        }
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            onComplete()
        }
    }

    private fun iconDirectory(context: Context) = File(context.cacheDir, ICON_DIRECTORY)

    private fun iconFile(context: Context, host: String) =
        File(iconDirectory(context), "${host.replace(Regex("[^a-zA-Z0-9.-]"), "_")}.png")

    private fun encodeLogin(name: String, origin: String): String =
        JSONObject().put("name", name).put("origin", origin).toString()

    private fun decodeLogin(value: String): Pair<String, String>? = runCatching {
        val json = JSONObject(value)
        json.optString("name") to json.getString("origin")
    }.getOrNull()

    private fun defaultName(host: String): String = host
        .removePrefix("www.")
        .substringBefore('.')
        .replaceFirstChar { it.uppercaseChar() }

    private fun clearOriginCookies(cookieManager: CookieManager, origin: String) {
        val host = WebLink.host(origin) ?: return
        val cookieNames = cookieManager.getCookie(origin).orEmpty()
            .split(';')
            .map { it.substringBefore('=').trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (cookieNames.isEmpty()) return

        originVariants(origin).forEach { candidateOrigin ->
            val candidateHost = WebLink.host(candidateOrigin) ?: return@forEach
            cookieNames.forEach { name ->
                cookieManager.setCookie(candidateOrigin, "$name=; Max-Age=0; Path=/")
                cookieManager.setCookie(
                    candidateOrigin,
                    "$name=; Max-Age=0; Domain=.$candidateHost; Path=/"
                )
            }
        }
    }

    private fun originVariants(origin: String): Set<String> {
        val host = WebLink.host(origin) ?: return emptySet()
        val baseHost = host.removePrefix("www.")
        return setOf(host, baseHost, "www.$baseHost").flatMapTo(mutableSetOf()) { candidateHost ->
            listOf("https://$candidateHost", "http://$candidateHost")
        }
    }
}
