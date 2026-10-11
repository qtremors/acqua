package dev.qtremors.acqua.downloader

import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.WebLink
import org.json.JSONObject
import java.util.concurrent.CancellationException

/** Flatten bounded collection trees while retaining each leaf's local playlist position. */
internal class CollectionInspection(
    private val parseItem: (JSONObject, String, Boolean) -> ResolvedMedia,
    private val loadNested: ((String) -> String)? = null
) {
    private val items = mutableListOf<ResolvedMedia>()
    private val active = mutableSetOf<String>()
    private val cache = mutableMapOf<String, JSONObject>()
    private var visited = 0
    private var loaded = 0

    fun parse(json: String, url: String, authorized: Boolean): List<ResolvedMedia> {
        require(json.length <= 16 * 1024 * 1024) { "This collection is too large to inspect." }
        val root = JSONObject(json)
        if (!root.has("entries") && root.optString("_type") !in setOf("playlist", "multi_video")) return listOf(parseItem(root, url, authorized))
        visit(root, url, root.optString("title").takeIf(String::isNotBlank), authorized, 0)
        require(items.isNotEmpty()) { "This collection has no downloadable entries." }
        return items.toList()
    }

    private fun visit(root: JSONObject, url: String, title: String?, authorized: Boolean, depth: Int) {
        require(depth <= 8) { "This collection has too many nested levels." }
        if (!active.add(url)) { unavailable(url, url, title, 1, 1, "Repeated collection cycle"); return }
        try {
            val entries = root.optJSONArray("entries")
            if (entries == null || entries.length() == 0) {
                unavailable(url, url, title, 1, 1, "Empty or unavailable collection")
                return
            }
            require(entries.length() <= 2000) { "This collection has too many entries to inspect." }
            for (index in 0 until entries.length()) {
                require(++visited <= 2000) { "This collection has too many entries to inspect." }
                val entry = entries.optJSONObject(index)
                val position = entry?.optInt("playlist_index")?.takeIf { it > 0 } ?: index + 1
                if (entry == null) { unavailable(url, url, title, position, entries.length(), "Unavailable"); continue }
                val nested = entry.has("entries") || entry.optString("_type") in setOf("playlist", "multi_video")
                if (nested) {
                    val nestedUrl = sequenceOf(entry.optString("webpage_url"), entry.optString("url")).mapNotNull(WebLink::normalize).firstOrNull()
                    val nestedTitle = listOfNotNull(title, entry.optString("title").takeIf(String::isNotBlank)).joinToString(" / ")
                    if (nestedUrl == null && entry.optJSONArray("entries") == null) {
                        unavailable(url, url, nestedTitle, position, entries.length(), "Unavailable nested collection")
                        continue
                    }
                    val synthetic = "$url#collection-$position"
                    val key = nestedUrl ?: synthetic
                    if (entry.optJSONArray("entries")?.length() == 0) unavailable(key, url, nestedTitle, position, entries.length(), "Empty or unavailable collection")
                    else if (entry.optJSONArray("entries") != null) visit(entry, key, nestedTitle, authorized, depth + 1)
                    else if (loadNested == null) unavailable(key, url, nestedTitle, position, entries.length(), "Nested collection needs inspection")
                    else load(key, nestedTitle, authorized, depth + 1)
                    continue
                }
                val parsed = parseItem(entry, url, authorized)
                val inaccessible = entry.optString("availability").takeIf { it in setOf("private", "premium_only", "subscriber_only", "needs_auth") }
                val missingAddress = url.contains("#collection-") && parsed.url == url
                items += parsed.copy(collectionUrl = url, collectionTitle = title, collectionIndex = position,
                    collectionSize = entries.length(), unavailableReason = inaccessible ?: if (missingAddress) "Nested entry has no source link" else null)
            }
        } finally { active.remove(url) }
    }

    private fun load(url: String, title: String?, authorized: Boolean, depth: Int) {
        if (url in active) { unavailable(url, url, title, 1, 1, "Repeated collection cycle"); return }
        val root = runCatching {
            cache[url] ?: run {
                require(++loaded <= 16) { "This collection has too many nested sources." }
                val json = requireNotNull(loadNested).invoke(url)
                require(json.length <= 16 * 1024 * 1024)
                JSONObject(json).also { cache[url] = it }
            }
        }.getOrElse { error ->
            if (error is CancellationException || error !is Exception) throw error
            unavailable(url, url, title, 1, 1, "Nested collection could not be inspected")
            return
        }
        if (!root.has("entries")) {
            unavailable(url, url, title, 1, 1, "Nested collection did not return entries")
        } else visit(root, url, title, authorized, depth)
    }

    private fun unavailable(url: String, parent: String, title: String?, index: Int, size: Int, reason: String) {
        items += ResolvedMedia(url, MediaKind.VIDEO, backend = MediaBackend.YT_DLP, collectionUrl = parent,
            collectionTitle = title, collectionIndex = index, collectionSize = size, unavailableReason = reason)
    }
}
