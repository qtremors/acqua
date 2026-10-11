package dev.qtremors.acqua.downloader

import dev.qtremors.acqua.domain.MediaChapter
import dev.qtremors.acqua.domain.ResolvedMedia
import java.util.Locale
import kotlin.math.roundToLong

data class ClipRange(val startSeconds: Double, val endSeconds: Double, val name: String = "")
data class CaptionTiming(val offsetSeconds: Double = 0.0, val scale: Double = 1.0) {
    fun validated(): CaptionTiming = apply {
        require(offsetSeconds.isFinite() && offsetSeconds in -3600.0..3600.0 && scale.isFinite() && scale in 0.5..2.0) { "Invalid caption timing." }
    }
    val adjusted: Boolean get() = offsetSeconds != 0.0 || scale != 1.0
}
data class CaptionCue(val startSeconds: Double, val endSeconds: Double, val text: String)
class MediaTimelineException : IllegalArgumentException("Could not produce the selected clips or caption timeline. Review the ranges and caption choices.")

object MediaTimeline {
    const val MAX_CLIPS = 20
    const val MAX_CAPTION_BYTES = 2 * 1024 * 1024
    fun duration(media: ResolvedMedia): Double = media.preciseDurationSeconds ?: media.durationSeconds.toDouble()
    fun hasSections(options: YtDlpDownloadOptions): Boolean = options.clipRanges.isNotEmpty() || options.clipStartSeconds != null || options.splitChapters
    fun active(options: YtDlpDownloadOptions): Boolean = hasSections(options) || options.captionMode != CaptionMode.NONE && options.captionTiming.adjusted

    fun validateRanges(ranges: List<ClipRange>, duration: Double = 0.0, maximum: Int = MAX_CLIPS) {
        require(ranges.size <= maximum)
        ranges.forEachIndexed { index, range ->
            require(range.startSeconds.isFinite() && range.endSeconds.isFinite() && range.startSeconds >= 0 && range.endSeconds - range.startSeconds >= 0.001 &&
                (duration <= 0 || range.endSeconds <= duration) && (index == 0 || ranges[index - 1].endSeconds <= range.startSeconds)) { "Clip ranges must be ordered, nonoverlapping, and within the duration." }
            require(range.name.length <= 80 && range.name.none(Char::isISOControl)) { "Invalid clip name." }
        }
    }

    fun plan(media: ResolvedMedia, options: YtDlpDownloadOptions): List<ClipRange> {
        DownloadSections.arguments(media, options)
        val ranges = when {
            options.splitChapters -> media.chapters.map { ClipRange(it.startSeconds, it.endSeconds, it.title.map { char -> if (char.isISOControl()) ' ' else char }.joinToString("").take(80)) }
                .also { if (it.isEmpty()) throw NoDownloadableChaptersException() }
            options.clipRanges.isNotEmpty() -> options.clipRanges
            options.clipStartSeconds != null -> listOf(ClipRange(options.clipStartSeconds, options.clipEndSeconds!!))
            else -> listOf(ClipRange(0.0, duration(media).takeIf { it > 0 } ?: Double.MAX_VALUE))
        }
        validateRanges(ranges, duration(media), if (options.splitChapters) 200 else MAX_CLIPS)
        return ranges
    }

    fun sourceOptions(options: YtDlpDownloadOptions): YtDlpDownloadOptions {
        if (!active(options)) return options
        val embedding = options.captionMode in listOf(CaptionMode.EMBED, CaptionMode.BOTH)
        return options.copy(clipStartSeconds = null, clipEndSeconds = null, clipRanges = emptyList(), splitChapters = false,
            captionTiming = CaptionTiming(), captionMode = if (options.captionMode == CaptionMode.NONE) CaptionMode.NONE else CaptionMode.SEPARATE,
            captionFormat = CaptionFormat.SRT,
            videoContainer = if (embedding && options.videoContainer == VideoContainer.AUTO) VideoContainer.MKV else options.videoContainer)
    }

    fun chapters(chapters: List<MediaChapter>, range: ClipRange): List<MediaChapter> = chapters.mapNotNull { chapter ->
        val start = maxOf(chapter.startSeconds, range.startSeconds) - range.startSeconds
        val end = minOf(chapter.endSeconds, range.endSeconds) - range.startSeconds
        if (end > start) MediaChapter(chapter.title, start, end) else null
    }

    fun cues(cues: List<CaptionCue>, range: ClipRange, timing: CaptionTiming): List<CaptionCue> {
        timing.validated()
        return cues.mapNotNull { cue ->
            val start = maxOf(cue.startSeconds * timing.scale + timing.offsetSeconds, range.startSeconds) - range.startSeconds
            val end = minOf(cue.endSeconds * timing.scale + timing.offsetSeconds, range.endSeconds) - range.startSeconds
            if (end > start) cue.copy(startSeconds = start, endSeconds = end) else null
        }
    }

    fun parseSrt(text: String): List<CaptionCue> {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_CAPTION_BYTES && '\u0000' !in text)
        val blocks = text.removePrefix("\uFEFF").replace("\r\n", "\n").trim().split(Regex("\n{2,}"))
        if (text.isBlank()) return emptyList()
        require(blocks.size <= 20000)
        return blocks.map { block ->
            val lines = block.lines().let { if (it.first().matches(Regex("[0-9]+"))) it.drop(1) else it }
            val times = lines.first().split(" --> ")
            require(times.size == 2 && lines.size >= 2)
            val start = time(times[0]); val end = time(times[1])
            require(end > start)
            CaptionCue(start, end, lines.drop(1).joinToString("\n"))
        }
    }

    fun srt(cues: List<CaptionCue>): String = cues.mapIndexed { index, cue ->
        "${index + 1}\n${clock(cue.startSeconds)} --> ${clock(cue.endSeconds)}\n${cue.text}\n"
    }.joinToString("\n")

    fun chapterMetadata(chapters: List<MediaChapter>): String = buildString {
        append(";FFMETADATA1\n")
        chapters.forEach { chapter ->
            val title = chapter.title.replace("\\", "\\\\").replace("=", "\\=").replace(";", "\\;").replace("#", "\\#").replace("\n", " ").replace("\r", " ")
            append("[CHAPTER]\nTIMEBASE=1/1000\nSTART=${(chapter.startSeconds * 1000).roundToLong()}\nEND=${(chapter.endSeconds * 1000).roundToLong()}\ntitle=$title\n")
        }
    }

    private fun time(value: String): Double {
        val match = Regex("([0-9]{1,3}):([0-9]{2}):([0-9]{2})[,.]([0-9]{3})").matchEntire(value.trim()) ?: throw MediaTimelineException()
        val values = match.groupValues.drop(1).map(String::toInt)
        require(values[1] < 60 && values[2] < 60)
        return values[0] * 3600.0 + values[1] * 60 + values[2] + values[3] / 1000.0
    }
    private fun clock(seconds: Double): String {
        val millis = (seconds * 1000).roundToLong().coerceAtLeast(0)
        return String.format(Locale.ROOT, "%02d:%02d:%02d,%03d", millis / 3600000, millis / 60000 % 60, millis / 1000 % 60, millis % 1000)
    }
}
