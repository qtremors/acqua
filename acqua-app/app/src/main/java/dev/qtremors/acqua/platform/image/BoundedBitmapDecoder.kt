package dev.qtremors.acqua.platform.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

object BoundedBitmapDecoder {
    private const val MAX_SOURCE_DIMENSION = 65_535
    private const val MAX_SOURCE_PIXELS = 100_000_000L
    private const val MAX_TARGET_DIMENSION = 2_048
    private const val MAX_TARGET_PIXELS = 4_194_304L

    fun bounds(file: File): ImageBounds? {
        if (!file.isFile) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return if (validSource(options.outWidth, options.outHeight)) {
            ImageBounds(options.outWidth, options.outHeight)
        } else null
    }

    fun decode(file: File, targetWidth: Int, targetHeight: Int): Bitmap? {
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sample = sampleSize(bounds, targetWidth, targetHeight) ?: return null
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )?.takeIf(::isTargetBounded)
    }

    fun decode(bytes: ByteArray, targetWidth: Int, targetHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val sample = sampleSize(bounds, targetWidth, targetHeight) ?: return null
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )?.takeIf(::isTargetBounded)
    }

    fun scale(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap? {
        if (bitmap.isRecycled || !validSource(bitmap.width, bitmap.height)) return null
        val widthLimit = targetWidth.coerceIn(1, MAX_TARGET_DIMENSION)
        val heightLimit = targetHeight.coerceIn(1, MAX_TARGET_DIMENSION)
        val scale = minOf(1f, widthLimit.toFloat() / bitmap.width, heightLimit.toFloat() / bitmap.height)
        if (scale >= 1f && isTargetBounded(bitmap)) return bitmap
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        if (width.toLong() * height > MAX_TARGET_PIXELS) return null
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun sampleSize(bounds: BitmapFactory.Options, targetWidth: Int, targetHeight: Int): Int? {
        if (!validSource(bounds.outWidth, bounds.outHeight)) return null
        val widthLimit = targetWidth.coerceIn(1, MAX_TARGET_DIMENSION)
        val heightLimit = targetHeight.coerceIn(1, MAX_TARGET_DIMENSION)
        var sample = 1
        while (
            bounds.outWidth / sample > widthLimit ||
            bounds.outHeight / sample > heightLimit ||
            (bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > MAX_TARGET_PIXELS
        ) {
            if (sample >= 1 shl 16) return null
            sample *= 2
        }
        return sample
    }

    private fun validSource(width: Int, height: Int): Boolean =
        width in 1..MAX_SOURCE_DIMENSION &&
            height in 1..MAX_SOURCE_DIMENSION &&
            width.toLong() * height <= MAX_SOURCE_PIXELS

    private fun isTargetBounded(bitmap: Bitmap): Boolean =
        bitmap.width <= MAX_TARGET_DIMENSION &&
            bitmap.height <= MAX_TARGET_DIMENSION &&
            bitmap.width.toLong() * bitmap.height <= MAX_TARGET_PIXELS
}

data class ImageBounds(val width: Int, val height: Int)
