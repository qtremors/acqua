package dev.qtremors.acqua.downloader

import org.json.JSONArray

internal object DownloadChapterManifest {
    fun requestOptions(): List<Pair<String, String>> = listOf(
        "--print" to "after_move:ACQUA_CHAPTERS:[%(id)j,%(chapters)j]",
        "-o" to "chapter:%(title).100B [%(id)s]-%(section_number)03d-%(section_title).80B.%(ext)s")

    fun rows(output: String, chapters: Boolean): List<String> = if (!chapters) {
        output.lineSequence().filter { it.startsWith("ACQUA_OUTPUT:") }.map { it.removePrefix("ACQUA_OUTPUT:") }.toList()
    } else {
        val rows = output.lineSequence().filter { it.startsWith("ACQUA_CHAPTERS:") }.flatMap { line ->
            val record = JSONArray(line.removePrefix("ACQUA_CHAPTERS:"))
            val id = record.getString(0)
            val entries = record.optJSONArray(1) ?: JSONArray()
            (0 until entries.length()).asSequence().map { index ->
                val entry = entries.getJSONObject(index)
                JSONArray().put("$id:chapter:${index + 1}").put(entry.getString("filepath"))
                    .put(entry.optString("title")).toString()
            }
        }.toList()
        if (rows.isEmpty()) throw NoDownloadableChaptersException()
        rows
    }
}

internal class NoDownloadableChaptersException : IllegalStateException("This media has no downloadable chapters.")
