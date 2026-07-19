package dev.qtremors.acqua.downloader

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
    val statusLine: String = ""
)
