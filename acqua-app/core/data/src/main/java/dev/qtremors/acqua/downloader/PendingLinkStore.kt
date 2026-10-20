package dev.qtremors.acqua.downloader

import android.content.Context
import androidx.core.content.edit
import dev.qtremors.acqua.domain.WebLink
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject

data class PendingLink(val url: String, val createdAt: Long, val status: String = "Saved",
    val acceptedItems: Map<String, String> = emptyMap(), val totalEntries: Int = 0)

internal class PendingLinkStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("pending_links", Context.MODE_PRIVATE)
    fun observe(): Flow<List<PendingLink>> = callbackFlow {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(read())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun read(): List<PendingLink> = synchronized(LOCK) {
        preferences.all.values.mapNotNull { value -> runCatching {
            val json = JSONObject(value as String)
            val accepted = json.optJSONObject("accepted") ?: JSONObject()
            PendingLink(json.getString("url"), json.getLong("created"), json.optString("status", "Saved"),
                accepted.keys().asSequence().associateWith { accepted.getString(it) }, json.optInt("total"))
        }.getOrNull() }.sortedBy(PendingLink::createdAt)
    }
    fun save(text: String) = synchronized(LOCK) {
        WebLink.extractAll(text).forEach { url -> if (!preferences.contains(url)) update(PendingLink(url, System.currentTimeMillis())) }
    }
    fun update(link: PendingLink) = synchronized(LOCK) {
        val accepted = JSONObject()
        link.acceptedItems.forEach { (key, id) -> accepted.put(key, id) }
        check(preferences.edit().putString(link.url, JSONObject().put("url", link.url).put("created", link.createdAt)
            .put("status", link.status).put("accepted", accepted).put("total", link.totalEntries).toString()).commit()) { "Could not save this pending link." }
    }
    fun remove(url: String) = synchronized(LOCK) { preferences.edit(commit = true) { remove(url) } }
    companion object { private val LOCK = Any() }
}
