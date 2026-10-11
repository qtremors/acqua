package dev.qtremors.acqua.downloader

/** Caption export and embedding have different container requirements. */
object DownloadCaptions {
    private val language = Regex("[A-Za-z0-9_-]{1,35}")

    fun canEmbed(options: YtDlpDownloadOptions): Boolean = options.contentType != DownloadContentType.AUDIO || options.audioFormat == AudioOutputFormat.M4A

    fun normalized(options: YtDlpDownloadOptions): YtDlpDownloadOptions {
        val mode = if (!canEmbed(options) && options.captionMode in listOf(CaptionMode.EMBED, CaptionMode.BOTH)) CaptionMode.SEPARATE else options.captionMode
        val embedding = mode == CaptionMode.EMBED || mode == CaptionMode.BOTH
        val format = when {
            !embedding && MediaTimeline.active(options) && options.captionFormat == CaptionFormat.ORIGINAL -> CaptionFormat.SRT
            !embedding -> options.captionFormat
            options.contentType == DownloadContentType.VIDEO && options.videoContainer == VideoContainer.WEBM -> CaptionFormat.VTT
            MediaTimeline.active(options) && options.captionFormat == CaptionFormat.ORIGINAL -> CaptionFormat.SRT
            options.captionFormat == CaptionFormat.LRC -> CaptionFormat.SRT
            options.captionFormat == CaptionFormat.ASS && (options.contentType == DownloadContentType.AUDIO || options.videoContainer == VideoContainer.MP4) -> CaptionFormat.SRT
            else -> options.captionFormat
        }
        return options.copy(captionMode = mode, captionFormat = format)
    }

    internal fun arguments(options: YtDlpDownloadOptions): List<Pair<String, String?>> = buildList {
        if (options.captionMode == CaptionMode.NONE) return@buildList
        val embedding = options.captionMode == CaptionMode.EMBED || options.captionMode == CaptionMode.BOTH
        require(!embedding || canEmbed(options)) { "Embedding audio captions requires M4A output. Use separate captions for other audio formats." }
        require(!embedding || options.captionFormat != CaptionFormat.LRC) { "LRC is a separate caption format." }
        require(!embedding || options.captionFormat != CaptionFormat.ASS ||
            (options.contentType == DownloadContentType.VIDEO && options.videoContainer in listOf(VideoContainer.AUTO, VideoContainer.MKV))) {
            "Embedding ASS captions requires MKV video output."
        }
        require(!embedding || options.contentType != DownloadContentType.VIDEO || options.videoContainer != VideoContainer.WEBM || options.captionFormat == CaptionFormat.VTT) {
            "Embedding captions in WebM requires VTT format."
        }
        val languages = options.captionLanguages.split(',').map(String::trim).filter(String::isNotEmpty)
        require(languages.size in 1..100 && languages.all { it == "all" || language.matches(it) }) { "Enter caption language codes separated by commas." }
        add("--sub-langs" to languages.distinct().joinToString(","))
        add("--write-subs" to null)
        if (options.automaticCaptions) add("--write-auto-subs" to null)
        if (options.captionFormat != CaptionFormat.ORIGINAL) add("--convert-subs" to options.captionFormat.name.lowercase())
        if (embedding) add("--embed-subs" to null)
    }
}
