package dev.qtremors.acqua.domain

enum class MediaKind {
    IMAGE,
    VIDEO
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
    val fileExtension: String? = null
) {
    val isVideo: Boolean get() = kind == MediaKind.VIDEO
    val previewUrl: String? get() = thumbnailUrl ?: url.takeIf { kind == MediaKind.IMAGE }
}
