package dev.qtremors.acqua.data.session

import android.content.Context
import dev.qtremors.acqua.domain.WebLink

class BrowserIdentityStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("browser_identity_v1", Context.MODE_PRIVATE)
    fun record(url: String, userAgent: String) {
        val origin = WebLink.origin(url) ?: return
        if (userAgent.isBlank() || userAgent.length > 1024 || userAgent.any(Char::isISOControl)) return
        val page = android.net.Uri.parse(url).buildUpon().clearQuery().fragment(null).build().toString().take(8192)
        if (preferences.getString(origin, null) == userAgent && preferences.getString("last", null) == page) return
        val editor = preferences.edit().putString(origin, userAgent).putString("last", page)
        preferences.all.keys.filter { it != "last" && it != origin }.drop(63).forEach(editor::remove)
        editor.apply()
    }
    fun userAgent(url: String): String? = WebLink.origin(url)?.let { preferences.getString(it, null) }
    fun lastUrl(): String? = preferences.getString("last", null)
    fun clear() { preferences.edit().clear().apply() }
    fun remove(origins: Collection<String>) {
        val sites = origins.mapNotNull(WebLink::host)
        fun matches(host: String) = sites.any { host == it || host.endsWith(".$it") || it.endsWith(".$host") }
        val editor = preferences.edit()
        preferences.all.keys.filter { key -> WebLink.host(key)?.let(::matches) == true }
            .forEach(editor::remove)
        if (preferences.getString("last", null)?.let(WebLink::host)?.let(::matches) == true) editor.remove("last")
        editor.apply()
    }
}
