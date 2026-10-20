package dev.qtremors.acqua.downloader

import dev.qtremors.acqua.domain.ResolvedMedia

/** Exact stream identifiers never become a free-form command expression. */
internal object DownloadAdvancedOptions {
    private val identifier = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")

    fun arguments(media: ResolvedMedia, options: YtDlpDownloadOptions): List<Pair<String, String?>> {
        DownloadStreams.validate(options)
        options.videoFormatId?.also { require(identifier.matches(it)) { "Invalid video format." } }
        options.audioFormatId?.also { require(identifier.matches(it)) { "Invalid audio format." } }
        val selector = options.formatSelectorOverride?.let(CommandTemplateParser::validateFormat) ?: options.formatPolicy.selector(media, options)
        return buildList {
            addAll(DownloadSections.arguments(media, options))
            add("-f" to selector)
            addAll(options.formatPolicy.sortingArguments())
            addAll(DownloadSegments.arguments(options))
            addAll(CommandTemplateOutput.arguments(options))
            if (options.additionalAudioFormatIds.isNotEmpty() || options.templateAudioMultistreams) add("--audio-multistreams" to null)
            val embedCaptions = options.captionMode == CaptionMode.EMBED || options.captionMode == CaptionMode.BOTH
            val container = if (options.videoContainer == VideoContainer.AUTO && (embedCaptions || options.additionalAudioFormatIds.isNotEmpty())) VideoContainer.MKV else options.videoContainer
            if (options.contentType == DownloadContentType.VIDEO && container != VideoContainer.AUTO) {
                add("--merge-output-format" to container.name.lowercase())
                add("--remux-video" to container.name.lowercase())
            }
            addAll(DownloadCaptions.arguments(options))
        }
    }
}
