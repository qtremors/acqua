package dev.qtremors.acqua.data.session

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.edit
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.platform.image.BoundedBitmapDecoder
import org.json.JSONObject
import java.io.File

data class SavedWebsite(
    val name: String,
    val host: String,
    val origin: String,
    val iconFile: File?
)

class SavedWebsiteRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun record(url: String) {
        val host = WebLink.host(url) ?: return
        if (host == "acqua.local") return
        WebLink.origin(url)?.let { origin -> preferences.edit { putString(KEY_LAST_ORIGIN, origin) } }
    }

    fun save(name: String, url: String, icon: Bitmap?) {
        val origin = WebLink.origin(url) ?: return
        val host = WebLink.host(origin)?.removePrefix("www.") ?: return
        val displayName = name.trim().take(40).ifBlank { defaultName(host) }
        val entries = load()
            .filterNot { it.host == host }
            .map { encode(it.name, it.origin) }
            .toMutableSet()
            .apply { add(encode(displayName, origin)) }

        preferences.edit {
            putStringSet(KEY_ENTRIES, entries)
            remove(LEGACY_KEY_ORIGINS)
            putString(KEY_LAST_ORIGIN, origin)
        }
        icon?.let { saveIcon(host, it) }
    }

    fun update(originalOrigin: String, name: String, url: String) {
        val originalHost = WebLink.host(originalOrigin)?.removePrefix("www.") ?: return
        val updatedOrigin = WebLink.origin(url) ?: return
        val updatedHost = WebLink.host(updatedOrigin)?.removePrefix("www.") ?: return
        val displayName = name.trim().take(40).ifBlank { defaultName(updatedHost) }
        val entries = load()
            .filterNot { it.host == originalHost || it.host == updatedHost }
            .map { encode(it.name, it.origin) }
            .toMutableSet()
            .apply { add(encode(displayName, updatedOrigin)) }
        val updateLastOrigin = lastOrigin()?.let(WebLink::host)
            ?.removePrefix("www.") == originalHost

        preferences.edit {
            putStringSet(KEY_ENTRIES, entries)
            remove(LEGACY_KEY_ORIGINS)
            if (updateLastOrigin) putString(KEY_LAST_ORIGIN, updatedOrigin)
        }
        if (originalHost != updatedHost) iconFile(originalHost).delete()
    }

    fun updateIcon(url: String, icon: Bitmap) {
        val host = WebLink.host(url)?.removePrefix("www.") ?: return
        load().firstOrNull { it.host == host }?.let { save(it.name, it.origin, icon) }
    }

    fun load(): List<SavedWebsite> {
        val savedEntries = preferences.getStringSet(KEY_ENTRIES, emptySet()).orEmpty()
        val decoded = if (savedEntries.isNotEmpty()) {
            savedEntries.mapNotNull(::decode)
        } else {
            preferences.getStringSet(LEGACY_KEY_ORIGINS, emptySet()).orEmpty().mapNotNull { origin ->
                WebLink.origin(origin)?.let { defaultName(WebLink.host(it).orEmpty()) to it }
            }
        }
        return decoded.mapNotNull { (name, savedOrigin) ->
            val origin = WebLink.origin(savedOrigin) ?: return@mapNotNull null
            val host = WebLink.host(origin)?.removePrefix("www.") ?: return@mapNotNull null
            SavedWebsite(name.ifBlank { defaultName(host) }, host, origin, iconFile(host).takeIf(File::isFile))
        }.distinctBy(SavedWebsite::host).sortedBy { it.name.lowercase() }
    }

    fun lastOrigin(): String? = preferences.getString(KEY_LAST_ORIGIN, null)?.let(WebLink::normalize)

    fun remove(origins: Collection<String>) {
        val hosts = origins.mapNotNull(WebLink::host).map { it.removePrefix("www.") }.toSet()
        preferences.edit { putStringSet(KEY_ENTRIES, load().filterNot { it.host in hosts }.map { encode(it.name, it.origin) }.toSet()) }
        hosts.forEach { iconFile(it).delete() }
    }

    fun clear() {
        preferences.edit { clear() }
        iconDirectory().deleteRecursively()
    }

    private fun saveIcon(host: String, icon: Bitmap) = runCatching {
        iconDirectory().mkdirs()
        val bounded = BoundedBitmapDecoder.scale(icon, ICON_SIZE_PX, ICON_SIZE_PX) ?: return@runCatching
        iconFile(host).outputStream().use { bounded.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (bounded !== icon) bounded.recycle()
    }

    private fun iconDirectory() = File(appContext.cacheDir, ICON_DIRECTORY)
    private fun iconFile(host: String) = File(iconDirectory(), "${host.replace(Regex("[^a-zA-Z0-9.-]"), "_")}.png")
    private fun encode(name: String, origin: String) = JSONObject().put("name", name).put("origin", origin).toString()
    private fun decode(value: String): Pair<String, String>? = runCatching {
        JSONObject(value).let { it.optString("name") to it.getString("origin") }
    }.getOrNull()
    private fun defaultName(host: String) = host.removePrefix("www.").substringBefore('.').replaceFirstChar { it.uppercaseChar() }

    private companion object {
        const val PREFS_NAME = "acqua_browser_sessions"
        const val KEY_ENTRIES = "login_entries"
        const val LEGACY_KEY_ORIGINS = "login_origins"
        const val KEY_LAST_ORIGIN = "last_origin"
        const val ICON_DIRECTORY = "website_login_icons"
        const val ICON_SIZE_PX = 256
    }
}
