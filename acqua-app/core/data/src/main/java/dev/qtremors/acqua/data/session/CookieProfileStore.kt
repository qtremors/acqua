package dev.qtremors.acqua.data.session

import android.content.Context
import android.net.Uri
import okhttp3.HttpUrl.Companion.toHttpUrl

class CookieProfileStore(context: Context, private val cipher: CookieProfileCipher = KeystoreCookieProfileCipher()) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("cookie_profiles_v1", Context.MODE_PRIVATE)
    private val snapshots = app.getSharedPreferences("cookie_profile_snapshots_v1", Context.MODE_PRIVATE)
    init { okhttp3.OkHttp.initialize(app) }
    fun all(): List<CookieProfile> = synchronized(LOCK) {
        preferences.all.keys.sorted().map { id -> get(id) }.sortedBy { it.name.lowercase() }
    }
    fun get(id: String): CookieProfile = runCatching {
        val encoded = preferences.getString(id, null) ?: throw CookieProfileException()
        CookieProfile.parse(cipher.decrypt(encoded)).also { require(it.id == id) }
    }.getOrElse { throw CookieProfileException() }
    fun put(profile: CookieProfile) = synchronized(LOCK) {
        profile.validated()
        val id = profile.id ?: throw CookieProfileException()
        require(preferences.contains(id) || preferences.all.size < 20)
        if (preferences.contains(id)) require(get(id).scope == profile.scope)
        require(all().none { it.id != id && it.name.equals(profile.name, true) })
        check(preferences.edit().putString(id, cipher.encrypt(profile.json())).commit())
    }
    fun remove(id: String) = synchronized(LOCK) {
        check(preferences.edit().remove(id).commit())
        val edit = snapshots.edit()
        snapshots.all.keys.filter { it.startsWith("$id:") }.forEach(edit::remove)
        check(edit.commit())
    }
    fun clear() = synchronized(LOCK) { check(preferences.edit().clear().commit()); check(snapshots.edit().clear().commit()); cipher.erase() }
    fun removeSites(urls: Collection<String>) = synchronized(LOCK) {
        val sites = urls.map { it.toHttpUrl().let { url -> url.topPrivateDomain() ?: url.host } }.toSet()
        all().filter { it.scope in sites }.forEach { remove(it.id!!) }
    }
    fun accepted(id: String, url: String, snapshot: String? = null, fingerprint: String? = null): String = synchronized(LOCK) {
        if (!dev.qtremors.acqua.data.settings.AppSettingsRepository(app).useBrowserSessions()) throw CookieProfileException()
        val live = get(id)
        val profile = snapshot?.let(CookieProfile::parse) ?: live
        if (profile.id != id || profile.fingerprint != live.fingerprint || fingerprint != null && fingerprint != profile.fingerprint) throw CookieProfileException()
        if (!profile.allows(url.toHttpUrl()) || profile.netscape().isEmpty()) throw CookieProfileException()
        val key = "${profile.id}:${profile.fingerprint}"
        val existing = snapshots.getString(key, null)?.let { org.json.JSONObject(it) }
        val payload = existing?.getString("payload") ?: cipher.encrypt(profile.json())
        check(snapshots.edit().putString(key, org.json.JSONObject().put("created", System.currentTimeMillis()).put("payload", payload).toString()).commit())
        "profile:$key"
    }
    fun restore(encoded: String): CookieProfile = runCatching {
        if (!encoded.startsWith("profile:")) return@runCatching CookieProfile.parse(cipher.decrypt(encoded))
        val key = encoded.removePrefix("profile:")
        require(key.matches(Regex("[a-f0-9-]{36}:[a-f0-9]{64}")))
        val saved = snapshots.getString(key, null) ?: throw CookieProfileException()
        CookieProfile.parse(cipher.decrypt(org.json.JSONObject(saved).getString("payload"))).also { require("${it.id}:${it.fingerprint}" == key) }
    }.getOrElse { throw CookieProfileException() }
    fun pruneSnapshots(retained: Set<String>, now: Long = System.currentTimeMillis()) = synchronized(LOCK) {
        val edit = snapshots.edit()
        snapshots.all.forEach { (key, value) ->
            val created = runCatching { org.json.JSONObject(value as String).getLong("created") }.getOrDefault(0L)
            if ("profile:$key" !in retained && now - created >= 24 * 60 * 60 * 1000L) edit.remove(key)
        }
        check(edit.commit())
    }
    fun requireAuthorized(profile: CookieProfile, url: String) {
        if (!dev.qtremors.acqua.data.settings.AppSettingsRepository(app).useBrowserSessions() || profile.id == null || !preferences.contains(profile.id) ||
            !profile.allows(url.toHttpUrl()) || profile.netscape().isEmpty()) throw CookieProfileException()
    }
    fun export(id: String, capturedAt: Long, uri: Uri, fingerprint: String? = null) {
        val profile = get(id)
        if (profile.capturedAt != capturedAt || fingerprint != null && profile.fingerprint != fingerprint) throw CookieProfileException()
        val bytes = profile.json().toByteArray(Charsets.UTF_8)
        app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: throw CookieProfileException()
    }
    companion object { private val LOCK = Any() }
}
