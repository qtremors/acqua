package dev.qtremors.acqua.platform.storage

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImageDownloadMetadataTest {
    @Test
    fun `download time replaces embedded camera dates and stores the source URL`() {
        val file = Files.createTempFile("acqua-metadata", ".jpg").toFile()
        try {
            file.outputStream().use { Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 95, it) }
            val original = ExifInterface(file)
            original.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2001:02:03 04:05:06")
            original.saveAttributes()
            ImageDownloadMetadata.apply(ExifInterface(file), SOURCE_URL, 1_704_067_200_123L, false)
            val written = ExifInterface(file)
            assertEquals("2024:01:01 00:00:00", written.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
            assertEquals("+00:00", written.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL))
            assertEquals("123", written.getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL))
            assertEquals(SOURCE_URL, written.getAttribute(ExifInterface.TAG_USER_COMMENT))

            ImageDownloadMetadata.apply(written, SOURCE_URL, 1_800_000_000_000L, true)
            assertEquals("2024:01:01 00:00:00", ExifInterface(file).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        } finally {
            file.delete()
        }
    }

    private companion object {
        const val SOURCE_URL = "https://example.com/post/123"
    }
}
