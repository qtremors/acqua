package dev.qtremors.acqua.data.network

import dev.qtremors.acqua.domain.MediaKind

import java.util.Locale

internal enum class DetectedMediaFormat(
    val kind: MediaKind,
    val mimeType: String,
    val fileExtension: String
) {
    JPEG(MediaKind.IMAGE, "image/jpeg", "jpg"),
    PNG(MediaKind.IMAGE, "image/png", "png"),
    GIF(MediaKind.IMAGE, "image/gif", "gif"),
    WEBP(MediaKind.IMAGE, "image/webp", "webp"),
    MP4(MediaKind.VIDEO, "video/mp4", "mp4")
}

internal object MediaContentDetector {
    fun detect(
        contentTypeHeader: String?,
        prefix: ByteArray,
        expectedVideo: Boolean
    ): DetectedMediaFormat? {
        val contentType = contentTypeHeader
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        val imageFormat = detectImageFormat(prefix)
        val hasMp4Header = hasMp4FileHeader(prefix)

        if (contentType.startsWith("audio/")) return null
        if (hasMp4Header && (expectedVideo || contentType.startsWith("video/"))) {
            return DetectedMediaFormat.MP4
        }
        if (imageFormat != null && !contentType.startsWith("video/")) {
            return imageFormat
        }
        return null
    }

    private fun hasMp4FileHeader(bytes: ByteArray): Boolean {
        if (bytes.size < 24 || bytes.asciiAt(4, 4) != "ftyp") return false
        val ftypSize = bytes.unsignedIntAt(0)
        if (ftypSize !in 16..bytes.size.toLong()) return false
        if (bytes.asciiAt(8, 4) in setOf("avif", "avis", "heic", "heix", "hevc", "mif1")) return false
        val nextOffset = ftypSize.toInt()
        if (nextOffset + 8 > bytes.size) return false
        val nextSize = bytes.unsignedIntAt(nextOffset)
        val nextType = bytes.asciiAt(nextOffset + 4, 4)
        return nextType.all { it.code in 0x20..0x7E } &&
            (nextSize == 0L || nextSize >= 8L)
    }

    private fun detectImageFormat(bytes: ByteArray): DetectedMediaFormat? {
        val jpeg = bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        val png = bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        )
        val gif = bytes.size >= 6 && bytes.asciiAt(0, 6) in setOf("GIF87a", "GIF89a")
        val webp = bytes.size >= 12 && bytes.asciiAt(0, 4) == "RIFF" && bytes.asciiAt(8, 4) == "WEBP"
        return when {
            jpeg -> DetectedMediaFormat.JPEG
            png -> DetectedMediaFormat.PNG
            gif -> DetectedMediaFormat.GIF
            webp -> DetectedMediaFormat.WEBP
            else -> null
        }
    }

    private fun ByteArray.asciiAt(offset: Int, length: Int): String =
        String(this, offset, length, Charsets.US_ASCII)

    private fun ByteArray.unsignedIntAt(offset: Int): Long =
        ((this[offset].toLong() and 0xFF) shl 24) or
            ((this[offset + 1].toLong() and 0xFF) shl 16) or
            ((this[offset + 2].toLong() and 0xFF) shl 8) or
            (this[offset + 3].toLong() and 0xFF)
}
