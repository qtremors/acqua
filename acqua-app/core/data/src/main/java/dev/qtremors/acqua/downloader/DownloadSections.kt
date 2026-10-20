package dev.qtremors.acqua.downloader

import dev.qtremors.acqua.domain.ResolvedMedia

internal object DownloadSections {
    fun arguments(media: ResolvedMedia, options: YtDlpDownloadOptions): List<Pair<String, String?>> {
        require(media.unavailableReason == null) { "This entry is unavailable." }
        val start = options.clipStartSeconds
        val end = options.clipEndSeconds
        options.captionTiming.validated()
        require((start == null) == (end == null)) { "Enter both clip boundaries." }
        require(listOf(start != null, options.clipRanges.isNotEmpty(), options.splitChapters).count { it } <= 1) { "Choose clips or chapter splitting." }
        MediaTimeline.validateRanges(options.clipRanges, MediaTimeline.duration(media))
        if (start != null && end != null) {
            require(start.isFinite() && end.isFinite() && start >= 0 && end > start &&
                (MediaTimeline.duration(media) == 0.0 || end <= MediaTimeline.duration(media))) { "Clip boundaries are outside the media duration." }
        }
        // Download once, then produce named local cuts with matching captions and chapter metadata.
        return emptyList()
    }

}
