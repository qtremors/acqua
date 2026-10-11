package dev.qtremors.acqua.platform.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.roundToInt

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

    fun decode(file: File, targetWidth: Int, targetHeight: Int, fill: Boolean = false): Bitmap? {
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sample = sampleSize(bounds, targetWidth, targetHeight, fill) ?: return null
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null
        return fitDecoded(bitmap, targetWidth, targetHeight, fill)
    }

    fun decode(bytes: ByteArray, targetWidth: Int, targetHeight: Int, fill: Boolean = false): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val sample = sampleSize(bounds, targetWidth, targetHeight, fill) ?: return null
        val bitmap = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null
        return fitDecoded(bitmap, targetWidth, targetHeight, fill)
    }

    fun scale(bitmap: Bitmap, targetWidth: Int, targetHeight: Int, fill: Boolean = false): Bitmap? {
        if (bitmap.isRecycled || !validSource(bitmap.width, bitmap.height)) return null
        val widthLimit = targetWidth.coerceIn(1, MAX_TARGET_DIMENSION)
        val heightLimit = targetHeight.coerceIn(1, MAX_TARGET_DIMENSION)
        val widthScale = widthLimit.toFloat() / bitmap.width
        val heightScale = heightLimit.toFloat() / bitmap.height
        val requestedScale = if (fill) maxOf(widthScale, heightScale) else minOf(widthScale, heightScale)
        val scale = minOf(1f, requestedScale, MAX_TARGET_DIMENSION.toFloat() / bitmap.width,
            MAX_TARGET_DIMENSION.toFloat() / bitmap.height)
        if (scale >= 1f && isTargetBounded(bitmap)) return bitmap
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        if (width.toLong() * height > MAX_TARGET_PIXELS) return null
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun sampleSize(bounds: BitmapFactory.Options, targetWidth: Int, targetHeight: Int, fill: Boolean): Int? {
        if (!validSource(bounds.outWidth, bounds.outHeight)) return null
        val widthLimit = targetWidth.coerceIn(1, MAX_TARGET_DIMENSION)
        val heightLimit = targetHeight.coerceIn(1, MAX_TARGET_DIMENSION)
        val widthScale = widthLimit.toFloat() / bounds.outWidth
        val heightScale = heightLimit.toFloat() / bounds.outHeight
        val fit = minOf(1f, if (fill) maxOf(widthScale, heightScale) else minOf(widthScale, heightScale))
        val fittedWidth = (bounds.outWidth * fit).roundToInt().coerceAtLeast(1)
        val fittedHeight = (bounds.outHeight * fit).roundToInt().coerceAtLeast(1)
        var sample = 1
        // Keep enough source pixels for the displayed image. Sampling until it is
        // smaller than the requested bounds makes Compose upscale a blurry bitmap.
        while (bounds.outWidth / (sample * 2) >= fittedWidth &&
            bounds.outHeight / (sample * 2) >= fittedHeight) {
            sample *= 2
        }
        while (
            bounds.outWidth / sample > MAX_TARGET_DIMENSION ||
            bounds.outHeight / sample > MAX_TARGET_DIMENSION ||
            (bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > MAX_TARGET_PIXELS
        ) {
            if (sample >= 1 shl 16) return null
            sample *= 2
        }
        return sample
    }

    private fun fitDecoded(bitmap: Bitmap, targetWidth: Int, targetHeight: Int, fill: Boolean): Bitmap? {
        val fitted = scale(bitmap, targetWidth, targetHeight, fill)
        if (fitted !== bitmap) bitmap.recycle()
        return fitted
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
