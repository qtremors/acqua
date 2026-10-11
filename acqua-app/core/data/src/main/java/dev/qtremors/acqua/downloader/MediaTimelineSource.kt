package dev.qtremors.acqua.downloader

import dev.qtremors.acqua.domain.MediaChapter
import dev.qtremors.acqua.domain.ResolvedMedia
import org.json.JSONArray
import org.json.JSONObject

/** Accepted chapter catalogs stay in the private snapshot, outside WorkManager's extras size limit. */
internal object MediaTimelineSource {
    fun encode(media: ResolvedMedia): String {
        require(media.chapters.size <= 200)
        return JSONObject().put("version", 1).put("duration", media.preciseDurationSeconds)
            .put("chapters", JSONArray(media.chapters.map { JSONArray().put(it.title.take(256)).put(it.startSeconds).put(it.endSeconds) }))
            .toString().also { require(it.toByteArray(Charsets.UTF_8).size <= 256 * 1024) }
    }

    fun restore(text: String?, media: ResolvedMedia): ResolvedMedia {
        if (text == null) return media
        require(text.toByteArray(Charsets.UTF_8).size <= 256 * 1024)
        val root = JSONObject(text)
        require(root.getInt("version") == 1)
        val duration = if (root.has("duration")) root.getDouble("duration").also { require(it.isFinite() && it >= 0) } else null
        val chapters = root.getJSONArray("chapters")
        require(chapters.length() <= 200)
        return media.copy(preciseDurationSeconds = duration, chapters = (0 until chapters.length()).map { index ->
            val row = chapters.getJSONArray(index)
            val start = row.getDouble(1); val end = row.getDouble(2)
            require(row.length() == 3 && start.isFinite() && end.isFinite() && start >= 0 && end > start)
            MediaChapter(row.getString(0).also { require(it.length <= 256) }, start, end)
        })
    }
}
