package dev.qtremors.acqua.resolver.instagram

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal object InstagramEmbeddedJson {
    private val scriptBlock = Regex("<script\\b[^>]*>(.*?)</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val mediaKeys = listOf("xdt_shortcode_media", "shortcode_media")

    fun findShortcodeMedia(html: String): JSONObject? {
        var singleItemFallback: JSONObject? = null
        for (match in scriptBlock.findAll(html)) {
            val raw = decodeEntities(match.groupValues[1]).trim()
                .removePrefix("<!--")
                .removeSuffix("-->")
                .trim()
            if (!raw.startsWith('{') && !raw.startsWith('[')) continue
            val root = runCatching { JSONTokener(raw).nextValue() }.getOrNull() ?: continue
            val queue = ArrayDeque<Any>()
            queue.add(root)
            var visited = 0
            while (queue.isNotEmpty() && visited++ < MAX_VISITED_NODES) {
                when (val value = queue.removeFirst()) {
                    is JSONObject -> {
                        mediaKeys.forEach { key ->
                            value.optJSONObject(key)?.let { return it }
                        }
                        if (value.optJSONObject("edge_sidecar_to_children") != null) return value
                        if (singleItemFallback == null && value.has("is_video") &&
                            value.optString("display_url").startsWith("http")
                        ) {
                            singleItemFallback = value
                        }
                        val keys = value.keys()
                        while (keys.hasNext()) {
                            when (val child = value.opt(keys.next())) {
                                is JSONObject, is JSONArray -> queue.add(child)
                            }
                        }
                    }
                    is JSONArray -> {
                        for (index in 0 until value.length()) {
                            when (val child = value.opt(index)) {
                                is JSONObject, is JSONArray -> queue.add(child)
                            }
                        }
                    }
                }
            }
        }
        return singleItemFallback
    }

    private fun decodeEntities(value: String): String = value
        .replace("&quot;", "\"")
        .replace("&#34;", "\"")
        .replace("&#x22;", "\"", ignoreCase = true)
        .replace("&amp;", "&")

    private const val MAX_VISITED_NODES = 25_000
}
