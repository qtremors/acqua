package dev.qtremors.acqua.downloader

import dev.qtremors.acqua.domain.ResolvedMedia
import java.io.File
import java.util.Locale

/** All paths come from the validated private output set. No shell or network commands are used. */
internal class MediaTimelineProcessor(private val run: (List<String>) -> Unit, private val checkCancelled: () -> Unit = {}) {
    fun process(source: DownloadOutputSet, media: ResolvedMedia, options: YtDlpDownloadOptions): DownloadOutputSet {
        return try { processOutputs(source, media, options) }
        catch (cancelled: java.util.concurrent.CancellationException) { throw cancelled }
        catch (_: IllegalArgumentException) { throw MediaTimelineException() }
    }

    private fun processOutputs(source: DownloadOutputSet, media: ResolvedMedia, options: YtDlpDownloadOptions): DownloadOutputSet {
        if (!MediaTimeline.active(options)) return source
        val plan = MediaTimeline.plan(media, options)
        val original = source.artifacts.singleOrNull { !it.caption } ?: throw MediaTimelineException()
        val inputs = source.artifacts.filter(DownloadArtifact::caption).map { artifact ->
            require(artifact.file.extension == "srt" && artifact.file.length() <= MediaTimeline.MAX_CAPTION_BYTES &&
                artifact.suffix.matches(Regex("\\.[A-Za-z0-9_-]{1,70}")))
            artifact to MediaTimeline.parseSrt(artifact.file.readText(Charsets.UTF_8))
        }
        if (options.captionMode != CaptionMode.NONE && inputs.isEmpty()) throw MediaTimelineException()
        val directory = File(source.directory, "timeline")
        check(directory.mkdirs() || directory.isDirectory)
        val artifacts = plan.flatMapIndexed { index, range ->
            checkCancelled()
            val number = String.format(Locale.ROOT, "%03d", index + 1)
            val prefix = "clip-$number"
            val cut = if (MediaTimeline.hasSections(options)) File(directory, "part-$number.${original.file.extension}").also {
                run(cutArguments(original.file, it, range, options.contentType))
            } else original.file
            val subtitles = inputs.mapNotNull { (artifact, cues) ->
                val adjusted = MediaTimeline.cues(cues, range, options.captionTiming)
                if (adjusted.isEmpty()) return@mapNotNull null
                val srt = File(directory, "$prefix${artifact.suffix}.srt").apply { writeText(MediaTimeline.srt(adjusted), Charsets.UTF_8) }
                val format = options.captionFormat.takeUnless { it == CaptionFormat.ORIGINAL } ?: CaptionFormat.SRT
                val output = if (format == CaptionFormat.SRT) srt else File(directory, "$prefix${artifact.suffix}.${format.name.lowercase()}").also {
                    run(conversionArguments(srt, it))
                }
                artifact.copy(file = output) to srt
            }
            val chapters = File(directory, "$prefix.ffmetadata").apply {
                writeText(MediaTimeline.chapterMetadata(if (options.embedMetadata) MediaTimeline.chapters(media.chapters, range) else emptyList()))
            }
            val target = File(directory, "$prefix.${original.file.extension}")
            val embedding = options.captionMode in listOf(CaptionMode.EMBED, CaptionMode.BOTH)
            run(muxArguments(cut, target, if (embedding) subtitles.map { it.first.file } else emptyList(), chapters))
            check(target.isFile && target.length() > 0L) { "Timeline processing produced no media." }
            val sourceId = original.sourceId ?: media.sourceIdentity ?: media.url.hashCode().toString()
            val id = "$sourceId:clip:${index + 1}:${range.startSeconds}:${range.endSeconds}"
            val title = if (MediaTimeline.hasSections(options)) range.name.ifBlank { (index + 1).toString() } else original.title
            listOf(DownloadArtifact(target, sourceId = id, title = title)) +
                if (options.captionMode in listOf(CaptionMode.SEPARATE, CaptionMode.BOTH)) subtitles.map { it.first.copy(sourceId = id) } else emptyList()
        }
        return DownloadOutputSet(source.directory, artifacts)
    }

    companion object {
        private fun base(): List<String> = listOf("-nostdin", "-hide_banner", "-loglevel", "error", "-y")
        fun cutArguments(source: File, target: File, range: ClipRange, type: DownloadContentType): List<String> = buildList {
            addAll(base()); addAll(listOf("-i", source.absolutePath, "-ss", seconds(range.startSeconds), "-t", seconds(range.endSeconds - range.startSeconds)))
            addAll(listOf("-map", "0:v?", "-map", "0:a?", "-map", "0:t?", "-c", "copy", "-map_metadata", "0", "-map_chapters", "-1"))
            if (type == DownloadContentType.VIDEO) {
                if (target.extension == "webm") addAll(listOf("-c:v", "libvpx-vp9", "-deadline", "realtime", "-cpu-used", "5"))
                else addAll(listOf("-c:v", "libx264", "-preset", "fast", "-bf", "0"))
            }
            add(target.absolutePath)
        }
        fun conversionArguments(source: File, target: File): List<String> = base() + listOf("-i", source.absolutePath, target.absolutePath)
        fun muxArguments(source: File, target: File, subtitles: List<File>, chapters: File): List<String> = buildList {
            addAll(base()); addAll(listOf("-i", source.absolutePath))
            subtitles.forEach { addAll(listOf("-i", it.absolutePath)) }
            addAll(listOf("-f", "ffmetadata", "-i", chapters.absolutePath))
            addAll(listOf("-map", "0:v?", "-map", "0:a?", "-map", "0:t?"))
            subtitles.indices.forEach { addAll(listOf("-map", "${it + 1}:0")) }
            addAll(listOf("-map_metadata", "0", "-map_chapters", "${subtitles.size + 1}", "-c", "copy"))
            if (subtitles.isNotEmpty()) {
                addAll(listOf("-c:s", when (target.extension) { "mp4", "m4a", "mov" -> "mov_text"; "webm" -> "webvtt"; else -> if (subtitles.any { it.extension == "ass" }) "ass" else "srt" }))
                subtitles.forEachIndexed { index, file -> addAll(listOf("-metadata:s:s:$index", "language=${file.nameWithoutExtension.substringAfterLast('.')}")) }
            }
            add(target.absolutePath)
        }
        private fun seconds(value: Double): String = String.format(Locale.ROOT, "%.3f", value)
    }
}
