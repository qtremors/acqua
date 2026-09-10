package dev.qtremors.acqua.domain

enum class MediaKind {
    IMAGE,
    VIDEO,
    AUDIO
}

enum class MediaBackend {
    DIRECT,
    YT_DLP
}

enum class DownloadEngine {
    ACQUA,
    YT_DLP
}

data class MediaFormatOption(
    val id: String,
    val extension: String,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val fps: Int = 0,
    val audioBitrateKbps: Int = 0,
    val totalBitrateKbps: Int = 0,
    val fileSize: Long? = null
) {
    val hasVideo: Boolean get() = !videoCodec.isNullOrBlank() && videoCodec != "none"
    val hasAudio: Boolean get() = !audioCodec.isNullOrBlank() && audioCodec != "none"
}

data class ResolvedMedia(
    val url: String,
    val kind: MediaKind,
    val thumbnailUrl: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val fileSize: Long? = null,
    val username: String? = null,
    val referer: String? = null,
    val requestCookies: String? = null,
    val explicitBrowserSessionAuthorized: Boolean = false,
    val mimeType: String? = null,
    val fileExtension: String? = null,
    val backend: MediaBackend = MediaBackend.DIRECT,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationSeconds: Int = 0,
    val formats: List<MediaFormatOption> = emptyList(),
    val sourceTimestampMillis: Long? = null
) {
    val isVideo: Boolean get() = kind == MediaKind.VIDEO
    val isAudio: Boolean get() = kind == MediaKind.AUDIO
    val previewUrl: String? get() = thumbnailUrl ?: url.takeIf { kind == MediaKind.IMAGE }
}
