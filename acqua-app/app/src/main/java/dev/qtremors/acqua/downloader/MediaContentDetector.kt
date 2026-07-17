package dev.qtremors.acqua.downloader

import java.util.Locale

internal enum class DetectedMediaKind { IMAGE, VIDEO }

internal object MediaContentDetector {
    fun detect(
        contentTypeHeader: String?,
        prefix: ByteArray,
        expectedVideo: Boolean
    ): DetectedMediaKind? {
        val contentType = contentTypeHeader
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        val hasImageSignature = isRecognizedImage(prefix)
        val hasMp4Signature = isCompleteMp4Start(prefix)

        if (contentType.startsWith("audio/")) return null
        if (hasMp4Signature && (expectedVideo || contentType.startsWith("video/"))) {
            return DetectedMediaKind.VIDEO
        }
        if (hasImageSignature && !contentType.startsWith("video/")) {
            return DetectedMediaKind.IMAGE
        }
        return null
    }

    private fun isCompleteMp4Start(bytes: ByteArray): Boolean {
        if (bytes.size < 12 || bytes.asciiAt(4, 4) != "ftyp") return false
        return bytes.asciiAt(8, 4) !in setOf("avif", "avis", "heic", "heix", "hevc", "mif1")
    }

    private fun isRecognizedImage(bytes: ByteArray): Boolean {
        val jpeg = bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        val png = bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        )
        val gif = bytes.size >= 6 && bytes.asciiAt(0, 6) in setOf("GIF87a", "GIF89a")
        val webp = bytes.size >= 12 && bytes.asciiAt(0, 4) == "RIFF" && bytes.asciiAt(8, 4) == "WEBP"
        return jpeg || png || gif || webp
    }

    private fun ByteArray.asciiAt(offset: Int, length: Int): String =
        String(this, offset, length, Charsets.US_ASCII)
}
