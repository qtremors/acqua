package dev.qtremors.acqua.downloader

import dev.qtremors.acqua.domain.MediaFormatOption

object YtDlpFormatSelector {
    fun video(maximumQuality: Int, portrait: Boolean): String = if (maximumQuality <= 0) {
        "bv*+ba/b"
    } else {
        val dimension = if (portrait) "width" else "height"
        "bv*[$dimension<=?$maximumQuality]+ba/b[$dimension<=?$maximumQuality]"
    }

    fun selectedVideoFormat(
        formats: List<MediaFormatOption>,
        maximumQuality: Int
    ): MediaFormatOption? = formats.asSequence()
        .filter { it.hasVideo && it.qualityDimension > 0 }
        .filter { maximumQuality <= 0 || it.qualityDimension <= maximumQuality }
        .maxWithOrNull(compareBy<MediaFormatOption> { it.qualityDimension }.thenBy { it.totalBitrateKbps })

    val MediaFormatOption.qualityDimension: Int
        get() = when {
            width > 0 && height > 0 -> minOf(width, height)
            height > 0 -> height
            else -> width
        }

    const val AUDIO = "ba/b"
}
