package dev.qtremors.acqua.downloader

import java.util.Locale

enum class DownloadContentType {
    VIDEO,
    AUDIO
}

enum class AudioOutputFormat(val preferenceValue: String) {
    ORIGINAL("original"),
    M4A("m4a"),
    MP3("mp3");

    companion object {
        fun fromPreference(value: String): AudioOutputFormat =
            entries.firstOrNull { it.preferenceValue == value } ?: ORIGINAL
    }
}

data class YtDlpDownloadOptions(
    val contentType: DownloadContentType = DownloadContentType.VIDEO,
    val maximumVideoHeight: Int = 0,
    val audioFormat: AudioOutputFormat = AudioOutputFormat.ORIGINAL,
    val embedMetadata: Boolean = true,
    val embedThumbnail: Boolean = true
)

data class YtDlpProgress(
    val percent: Float = 0f,
    val etaSeconds: Long = 0L,
    val statusLine: String = "",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L
) {
    companion object {
        fun fromCallback(percent: Float, etaSeconds: Long, statusLine: String): YtDlpProgress {
            val match = TOTAL_SIZE.find(statusLine)
            val total = match?.let {
                val value = it.groupValues[1].toDoubleOrNull() ?: return@let 0L
                (value * unitMultiplier(it.groupValues[2])).toLong().coerceAtLeast(0L)
            } ?: 0L
            val downloaded = if (total > 0L) {
                (total * (percent.coerceIn(0f, 100f) / 100.0)).toLong()
            } else 0L
            return YtDlpProgress(percent, etaSeconds, statusLine, downloaded, total)
        }

        private fun unitMultiplier(unit: String): Long = when (unit.uppercase(Locale.ROOT)) {
            "KIB", "KB" -> 1L shl 10
            "MIB", "MB" -> 1L shl 20
            "GIB", "GB" -> 1L shl 30
            "TIB", "TB" -> 1L shl 40
            else -> 1L
        }

        private val TOTAL_SIZE = Regex(
            """\bof\s+(?:~\s*)?([0-9]+(?:\.[0-9]+)?)\s*([KMGT]?i?B)\b""",
            RegexOption.IGNORE_CASE
        )
    }
}
