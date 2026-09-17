package dev.qtremors.acqua.data.network

import android.content.Context
import android.graphics.Bitmap
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.platform.image.BoundedBitmapDecoder
import java.io.File
import java.security.MessageDigest

class MediaPreviewCache(
    context: Context,
    private val downloader: HttpMediaClient
) {
    private val directory = File(context.applicationContext.cacheDir, CACHE_DIRECTORY)
    private val lock = Any()

    fun loadBitmap(item: ResolvedMedia, maxWidth: Int, maxHeight: Int): MediaPreviewBitmap? = synchronized(lock) {
        val cacheFile = File(directory, cacheKey(item))
        runCatching {
            prune()
            downloader.fetchPreviewToFile(item, cacheFile, MAX_FILE_BYTES)
            cacheFile.setLastModified(System.currentTimeMillis())
            prune()
            decodeSampled(cacheFile, maxWidth.coerceAtLeast(1), maxHeight.coerceAtLeast(1))
                ?: error("The cached preview is not a readable image.")
        }.onFailure {
            cacheFile.delete()
        }.getOrNull()
    }

    private fun decodeSampled(file: File, maxWidth: Int, maxHeight: Int): MediaPreviewBitmap? {
        val bounds = BoundedBitmapDecoder.bounds(file) ?: return null
        val bitmap = BoundedBitmapDecoder.decode(file, maxWidth, maxHeight) ?: return null
        return MediaPreviewBitmap(bitmap, bounds.width, bounds.height)
    }

    private fun prune() {
        if (!directory.isDirectory) return
        val cached = directory.listFiles()?.filter(File::isFile)?.sortedByDescending(File::lastModified).orEmpty()
        var retainedBytes = 0L
        cached.forEachIndexed { index, file ->
            retainedBytes += file.length()
            if (index >= MAX_FILE_COUNT || retainedBytes > MAX_CACHE_BYTES) file.delete()
        }
    }

    private fun cacheKey(item: ResolvedMedia): String {
        val identity = listOf(item.url, item.referer.orEmpty(), item.requestCookies.orEmpty()).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private companion object {
        const val CACHE_DIRECTORY = "media-previews"
        const val MAX_FILE_COUNT = 48
        const val MAX_FILE_BYTES = 8L * 1024L * 1024L
        const val MAX_CACHE_BYTES = 64L * 1024L * 1024L
    }
}

data class MediaPreviewBitmap(
    val bitmap: Bitmap,
    val sourceWidth: Int,
    val sourceHeight: Int
)
