package dev.qtremors.acqua.ui.image

import coil.key.Keyer
import coil.request.Options
import java.io.File

data class ThumbnailKey(
    val path: String,
    val extension: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val contentUri: String? = null
) {
    val file: File get() = File(path)

    val identityKey: ThumbnailIdentityKey
        get() = ThumbnailIdentityKey(
            source = contentUri ?: path,
            extension = extension,
            sizeBytes = sizeBytes,
            lastModifiedMillis = lastModifiedMillis
        )

    val cacheKey: String
        get() = identityKey.cacheKey

    fun variantKey(sizePx: Int): ThumbnailVariantKey =
        ThumbnailVariantKey(identityKey, ThumbnailTargetSize.bucket(sizePx))

    val type: ThumbnailType
        get() = when (extension.lowercase()) {
            in ImageExtensions -> ThumbnailType.Image
            in VideoExtensions -> ThumbnailType.Video
            in AudioExtensions -> ThumbnailType.Audio
            "pdf" -> ThumbnailType.Pdf
            "apk" -> ThumbnailType.Apk
            else -> ThumbnailType.Unsupported
        }

    companion object {
        val ImageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif", "svg")
        val VideoExtensions = setOf("mp4", "mkv", "webm", "avi", "mov", "flv", "3gp", "ts", "m4v")
        val AudioExtensions = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma")

        fun from(file: File): ThumbnailKey =
            ThumbnailKey(
                path = file.absolutePath,
                extension = file.extension.lowercase(),
                sizeBytes = file.length(),
                lastModifiedMillis = file.lastModified()
            )
    }
}

data class ThumbnailIdentityKey(
    val source: String,
    val extension: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long
) {
    val cacheKey: String
        get() = "thumbnail:$source:$extension:$sizeBytes:$lastModifiedMillis"
}

data class ThumbnailVariantKey(
    val identity: ThumbnailIdentityKey,
    val sizeBucketPx: Int
) {
    val cacheKey: String
        get() = "${identity.cacheKey}:$sizeBucketPx"
}

enum class ThumbnailType {
    Image,
    Video,
    Audio,
    Pdf,
    Apk,
    Unsupported
}

class ThumbnailKeyer : Keyer<ThumbnailKey> {
    override fun key(data: ThumbnailKey, options: Options): String {
        val sizePx = ThumbnailTargetSize.fromOptions(options)
        return data.variantKey(sizePx).cacheKey
    }
}
