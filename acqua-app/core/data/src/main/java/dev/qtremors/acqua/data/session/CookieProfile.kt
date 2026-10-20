package dev.qtremors.acqua.data.session

import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject

class CookieProfileException : IllegalArgumentException("The saved session is unavailable, expired, or outside its authorized website. Review it in Settings.")

data class SessionCookie(val name: String, val value: String, val domain: String? = null, val hostOnly: Boolean? = null,
    val path: String? = null, val expiresAt: Long? = null, val secure: Boolean? = null, val httpOnly: Boolean? = null,
    val sameSite: String? = null, val partitioned: Boolean? = null) {
    val attributesAvailable: Boolean get() = domain != null
    fun effective(capture: HttpUrl, capturedAt: Long): Cookie = Cookie.Builder().name(name).value(value).apply {
        if (domain == null) hostOnlyDomain(capture.host) else if (hostOnly == true) hostOnlyDomain(domain) else domain(domain)
        path(path ?: capture.encodedPath)
        expiresAt(expiresAt ?: if (attributesAvailable) Long.MAX_VALUE else capturedAt + 24 * 60 * 60 * 1000L)
        if (secure == true || secure == null && capture.isHttps) secure()
        if (httpOnly == true) httpOnly()
    }.build()
}

data class CookieProfile(val id: String?, val name: String, val captureUrl: String, val userAgent: String,
    val capturedAt: Long, val cookies: List<SessionCookie>) {
    val fingerprint: String get() = java.security.MessageDigest.getInstance("SHA-256").digest(json().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    val scope: String get() = captureUrl.toHttpUrl().let { it.topPrivateDomain() ?: it.host }
    fun allows(url: HttpUrl): Boolean = url.host == scope || url.host.endsWith(".$scope")
    fun matching(url: HttpUrl, now: Long = System.currentTimeMillis()): List<Cookie> {
        if (!allows(url)) return emptyList()
        return cookies.filter { it.partitioned != true }.map { it.effective(captureUrl.toHttpUrl(), capturedAt) }
            .filter { it.expiresAt > now && it.matches(url) }
    }
    fun validated(): CookieProfile = apply {
        if (id != null) require(java.util.UUID.fromString(id).toString() == id)
        require(name.isNotBlank() && name.length <= 60 && name.none(Char::isISOControl))
        require(captureUrl.length <= 8192 && captureUrl.toHttpUrl().username.isEmpty() && captureUrl.toHttpUrl().password.isEmpty())
        require(userAgent.isNotBlank() && userAgent.length <= 1024 && userAgent.none(Char::isISOControl))
        require(capturedAt >= 0 && cookies.size in 1..200)
        cookies.forEach { record ->
            require(record.name.length <= 256 && record.value.length <= 4096 && record.name.none(Char::isISOControl) && record.value.none(Char::isISOControl))
            require(record.sameSite == null || record.sameSite in setOf("Strict", "Lax", "None"))
            if (record.attributesAvailable) require(record.path != null && record.hostOnly != null && record.secure != null && record.httpOnly != null)
            else require(record.path == null && record.hostOnly == null && record.secure == null && record.httpOnly == null && record.expiresAt == null)
            val cookie = record.effective(captureUrl.toHttpUrl(), capturedAt)
            require(cookie.domain == scope || cookie.domain.endsWith(".$scope"))
        }
    }
    fun netscape(now: Long = System.currentTimeMillis()): List<String> = cookies.filter { it.partitioned != true }.mapNotNull { record ->
        val cookie = record.effective(captureUrl.toHttpUrl(), capturedAt)
        if (cookie.expiresAt <= now) return@mapNotNull null
        val domain = (if (cookie.hostOnly) "" else ".") + cookie.domain
        listOf((if (cookie.httpOnly) "#HttpOnly_" else "") + domain, if (cookie.hostOnly) "FALSE" else "TRUE", cookie.path,
            if (cookie.secure) "TRUE" else "FALSE", if (record.attributesAvailable && record.expiresAt == null) "0" else (cookie.expiresAt / 1000).toString(), cookie.name, cookie.value).joinToString("\t")
    }
    fun json(): String = JSONObject().put("kind", "acqua.cookie-profile").put("version", 1).put("id", id).put("name", name)
        .put("url", captureUrl).put("userAgent", userAgent).put("capturedAt", capturedAt).put("cookies", JSONArray(cookies.map { cookie ->
            JSONObject().put("name", cookie.name).put("value", cookie.value).put("domain", cookie.domain).put("hostOnly", cookie.hostOnly)
                .put("path", cookie.path).put("expiresAt", cookie.expiresAt).put("secure", cookie.secure).put("httpOnly", cookie.httpOnly)
                .put("sameSite", cookie.sameSite).put("partitioned", cookie.partitioned)
        })).toString().also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) }

    companion object {
        const val MAX_BYTES = 256 * 1024
        fun parse(text: String): CookieProfile = runCatching {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
            val root = JSONObject(text)
            require(root.getString("kind") == "acqua.cookie-profile" && root.getInt("version") == 1)
            val rows = root.getJSONArray("cookies")
            require(rows.length() in 1..200)
            CookieProfile(root.optional("id"), root.getString("name"), root.getString("url"), root.getString("userAgent"), root.getLong("capturedAt"),
                (0 until rows.length()).map { index -> rows.getJSONObject(index).let { row -> SessionCookie(row.getString("name"), row.getString("value"),
                    row.optional("domain"), if (row.has("hostOnly")) row.getBoolean("hostOnly") else null, row.optional("path"),
                    if (row.has("expiresAt")) row.getLong("expiresAt") else null, if (row.has("secure")) row.getBoolean("secure") else null,
                    if (row.has("httpOnly")) row.getBoolean("httpOnly") else null, row.optional("sameSite"), if (row.has("partitioned")) row.getBoolean("partitioned") else null) } }).validated()
        }.getOrElse { throw CookieProfileException() }
        private fun JSONObject.optional(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
    }
}
