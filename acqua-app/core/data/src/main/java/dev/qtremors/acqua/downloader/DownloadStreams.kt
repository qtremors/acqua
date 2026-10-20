package dev.qtremors.acqua.downloader

import dev.qtremors.acqua.domain.MediaFormatOption
import dev.qtremors.acqua.domain.ResolvedMedia
import org.json.JSONArray
import org.json.JSONObject

class SourceStreamsChangedException : IllegalStateException("Source streams changed. Fetch the link again and review the stream selections.")

internal object DownloadStreams {
    private val identifier = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")

    fun validate(options: YtDlpDownloadOptions) {
        require(options.additionalAudioFormatIds.size <= 8 && ids(options).all(identifier::matches)) { "Invalid stream selection." }
        require(options.contentType == DownloadContentType.VIDEO || (!options.silentVideo && options.additionalAudioFormatIds.isEmpty() && !options.templateAudioMultistreams)) {
            "Multiple audio tracks and silent video require video output."
        }
        require(!options.silentVideo || (options.audioFormatId == null && options.additionalAudioFormatIds.isEmpty() &&
            (options.videoFormatId == null || !options.videoFormatIncludesAudio))) { "Silent video requires a video-only stream." }
        require(options.additionalAudioFormatIds.isEmpty() || options.videoContainer in listOf(VideoContainer.AUTO, VideoContainer.MKV)) {
            "Multiple audio tracks require MKV output."
        }
    }

    fun ids(options: YtDlpDownloadOptions): Set<String> = (listOfNotNull(options.videoFormatId, options.audioFormatId) + options.additionalAudioFormatIds).toSet()

    fun accepted(media: ResolvedMedia, options: YtDlpDownloadOptions): YtDlpDownloadOptions {
        validate(options)
        return options.copy(additionalAudioFormatIds = options.additionalAudioFormatIds.toSet(),
            acceptedStreams = ids(options).mapNotNull { id -> media.formats.firstOrNull { it.id == id } })
    }

    fun rebound(media: ResolvedMedia, options: YtDlpDownloadOptions): YtDlpDownloadOptions {
        val ids = ids(options)
        if (ids.isEmpty()) return options
        if (options.acceptedStreams.map { it.id }.toSet() != ids) throw SourceStreamsChangedException()
        val replacements = options.acceptedStreams.associate { previous ->
            val equivalent = media.formats.filter { matches(previous, it) }
            val chosen = equivalent.firstOrNull { it.id == previous.id } ?: equivalent.singleOrNull() ?: throw SourceStreamsChangedException()
            previous.id to chosen.id
        }
        if (replacements.values.distinct().size != replacements.size) throw SourceStreamsChangedException()
        return options.copy(videoFormatId = options.videoFormatId?.let(replacements::getValue),
            audioFormatId = options.audioFormatId?.let(replacements::getValue),
            additionalAudioFormatIds = options.additionalAudioFormatIds.map(replacements::getValue).toSet())
    }

    private fun matches(old: MediaFormatOption, fresh: MediaFormatOption): Boolean =
        old.videoCodec == fresh.videoCodec && old.audioCodec == fresh.audioCodec && old.extension == fresh.extension &&
            old.width == fresh.width && old.height == fresh.height && old.fps == fresh.fps && old.language == fresh.language &&
            old.audioChannels == fresh.audioChannels && old.audioBitrateKbps == fresh.audioBitrateKbps && (old.hasVideo || old.hasAudio)

    fun <T> recover(media: ResolvedMedia, options: YtDlpDownloadOptions, inspect: () -> ResolvedMedia,
        download: (ResolvedMedia, YtDlpDownloadOptions) -> T): T = runCatching { download(media, options) }.getOrElse { error ->
        if (error is java.util.concurrent.CancellationException || error !is Exception) throw error
        val message = error.message.orEmpty()
        if (listOf("requested format is not available", "HTTP Error 403", "HTTP Error 410", "URL has expired").none { message.contains(it, true) }) throw error
        val fresh = inspect()
        if (media.sourceIdentity != null && fresh.sourceIdentity != media.sourceIdentity) throw SourceStreamsChangedException()
        download(fresh, rebound(fresh, options))
    }

    fun encode(streams: List<MediaFormatOption>): JSONArray = JSONArray(streams.map { stream -> JSONObject()
        .put("id", stream.id).put("extension", stream.extension).put("video", stream.videoCodec).put("audio", stream.audioCodec)
        .put("width", stream.width).put("height", stream.height).put("fps", stream.fps).put("language", stream.language).put("channels", stream.audioChannels)
        .put("abr", stream.audioBitrateKbps).put("tbr", stream.totalBitrateKbps).put("size", stream.fileSize) })

    fun decode(streams: JSONArray): List<MediaFormatOption> {
        require(streams.length() <= 10)
        return (0 until streams.length()).map { index -> streams.getJSONObject(index).let { stream ->
            MediaFormatOption(stream.getString("id").also { require(identifier.matches(it)) }, stream.getString("extension").also { require(it.length <= 20) },
                stream.optString("video").takeIf { it.isNotEmpty() && it != "null" }, stream.optString("audio").takeIf { it.isNotEmpty() && it != "null" },
                stream.getInt("width").also { require(it in 0..16384) }, stream.getInt("height").also { require(it in 0..16384) },
                stream.getInt("fps").also { require(it in 0..1000) },
                audioBitrateKbps = stream.getInt("abr").also { require(it in 0..1_000_000) }, totalBitrateKbps = stream.getInt("tbr").also { require(it in 0..1_000_000) },
                fileSize = if (stream.has("size")) stream.getLong("size").also { require(it >= 0) } else null,
                language = stream.optString("language").takeIf { it.isNotEmpty() && it != "null" },
                audioChannels = stream.getInt("channels").also { require(it in 0..64) })
        } }
    }
}
