package dev.qtremors.acqua.data.session

import android.content.Context
import android.webkit.CookieManager
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewFeature
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl

class BrowserCookieCapture(context: Context, private val readInfo: (String) -> List<String>? = { url ->
    if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) CookieManagerCompat.getCookieInfo(CookieManager.getInstance(), url) else null
}, private val readHeader: (String) -> String? = { CookieManager.getInstance().getCookie(it) }) {
    private val identity = BrowserIdentityStore(context)
    init { okhttp3.OkHttp.initialize(context.applicationContext) }
    fun capture(name: String, url: String, id: String? = null): CookieProfile {
        val capture = url.toHttpUrl().newBuilder().query(null).fragment(null).build()
        val userAgent = identity.userAgent(capture.toString()) ?: throw CookieProfileException()
        val attributes = readInfo(capture.toString())
        val records = if (attributes != null) attributes.also { require(it.size <= 200) }.mapNotNull { raw -> parseCookie(capture.toString(), raw) }
            else readHeader(capture.toString()).orEmpty().also { require(it.length <= CookieProfile.MAX_BYTES) }.split(';').mapNotNull { raw ->
                val pair = raw.trim().split('=', limit = 2)
                if (pair.size == 2 && pair[0].isNotBlank()) SessionCookie(pair[0], pair[1]) else null
            }
        return CookieProfile(id, name.trim(), capture.toString(), userAgent, System.currentTimeMillis(), records).validated()
    }
    companion object {
        fun parseCookie(url: String, raw: String): SessionCookie? {
            if (raw.length > 8192 || raw.any { it == '\r' || it == '\n' || it == '\t' }) return null
            val cookie = Cookie.parse(url.toHttpUrl(), raw) ?: return null
            val attributes = raw.split(';').drop(1).associate { it.trim().substringBefore('=').lowercase() to it.trim().substringAfter('=', "") }
            val sameSite = attributes["samesite"]?.let { value -> listOf("Strict", "Lax", "None").firstOrNull { it.equals(value, true) } }
            return SessionCookie(cookie.name, cookie.value, cookie.domain, cookie.hostOnly, cookie.path,
                cookie.expiresAt.takeIf { cookie.persistent }, cookie.secure, cookie.httpOnly, sameSite, "partitioned" in attributes)
        }
    }
}
