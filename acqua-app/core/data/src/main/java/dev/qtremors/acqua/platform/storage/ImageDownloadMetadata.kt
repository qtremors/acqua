package dev.qtremors.acqua.platform.storage

import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal object ImageDownloadMetadata {
    fun apply(exif: ExifInterface, sourceUrl: String, timestamp: Long, preserveOriginal: Boolean) {
        if (!preserveOriginal) {
            val date = Date(timestamp)
            val formatted = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(date)
            listOf(ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED).forEach {
                exif.setAttribute(it, formatted)
            }
            listOf(ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_DIGITIZED).forEach {
                exif.setAttribute(it, "+00:00")
            }
            listOf(ExifInterface.TAG_SUBSEC_TIME, ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, ExifInterface.TAG_SUBSEC_TIME_DIGITIZED).forEach {
                exif.setAttribute(it, (timestamp % 1_000L).toString().padStart(3, '0'))
            }
        }
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, sourceUrl)
        exif.saveAttributes()
    }
}
