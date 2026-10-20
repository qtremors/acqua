package dev.qtremors.acqua.downloader

import dev.qtremors.acqua.domain.ResolvedMedia
import org.json.JSONArray

enum class SegmentCategory(val apiName: String) {
    SPONSOR("sponsor"), INTRO("intro"), OUTRO("outro"), SELF_PROMOTION("selfpromo"),
    INTERACTION("interaction"), PREVIEW("preview"), NON_MUSIC("music_offtopic"), FILLER("filler")
}

class NoRemovableSegmentsException : IllegalStateException("No usable matching segments were removed. Turn removal off to save the original.")
class SegmentServiceException : java.io.IOException("The segment service could not provide a usable removal result. Try again or turn removal off.")

internal object DownloadSegments {
    const val MARKER = "ACQUA_SEGMENTS:"

    fun arguments(options: YtDlpDownloadOptions): List<Pair<String, String?>> {
        val categories = options.removeSegments
        if (categories.isEmpty()) return listOf("--no-sponsorblock" to null)
        require(!MediaTimeline.active(options)) { "Use segment removal without clipping, chapter splitting, or caption timing edits." }
        return listOf("--sponsorblock-remove" to categories.sortedBy { it.apiName }.joinToString(",") { it.apiName },
            "--force-keyframes-at-cuts" to null,
            "--print" to "after_move:$MARKER[%(duration)j,%(sponsorblock_chapters)j]")
    }

    fun verify(output: String, errors: String, media: ResolvedMedia, selected: Set<SegmentCategory>) {
        if (selected.isEmpty()) return
        if (errors.contains("remove the entire video", true)) throw NoRemovableSegmentsException()
        if (errors.lineSequence().any { it.contains("SponsorBlock", true) &&
                listOf("unable", "failed", "error").any { word -> it.contains(word, true) } }) throw SegmentServiceException()
        val raw = output.lineSequence().lastOrNull { it.startsWith(MARKER) }?.removePrefix(MARKER)
            ?: throw SegmentServiceException()
        if (raw.length > 262_144) throw SegmentServiceException()
        val row = runCatching { JSONArray(raw) }.getOrElse { throw SegmentServiceException() }
        val chapters = row.optJSONArray(1) ?: throw NoRemovableSegmentsException()
        if (chapters.length() > 2000) throw SegmentServiceException()
        val ranges = buildList {
            for (index in 0 until chapters.length()) {
                val item = chapters.optJSONObject(index) ?: throw SegmentServiceException()
                if (selected.none { it.apiName == item.optString("category") }) continue
                val start = item.optDouble("start_time", Double.NaN)
                val end = item.optDouble("end_time", Double.NaN)
                if (!start.isFinite() || !end.isFinite() || start < 0 || end <= start) throw SegmentServiceException()
                add(start to end)
            }
        }.sortedBy { it.first }
        if (ranges.isEmpty()) throw NoRemovableSegmentsException()
        val merged = mutableListOf<Pair<Double, Double>>()
        ranges.forEach { range ->
            val previous = merged.lastOrNull()
            if (previous != null && range.first <= previous.second) merged[merged.lastIndex] = previous.first to maxOf(previous.second, range.second)
            else merged += range
        }
        val removed = merged.sumOf { it.second - it.first }
        val finalDuration = row.optDouble(0, Double.NaN)
        if (!finalDuration.isFinite() || finalDuration <= 0) throw NoRemovableSegmentsException()
        if (media.durationSeconds > 0 && (removed >= media.durationSeconds ||
                kotlin.math.abs(media.durationSeconds - removed - finalDuration) > 2)) throw NoRemovableSegmentsException()
    }
}
