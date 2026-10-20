package dev.qtremors.acqua.platform.image

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BoundedBitmapDecoderTest {
    @Test
    fun `square crop retains detail on the shorter source axis`() {
        val bitmap = BoundedBitmapDecoder.decode(cover(1280, 720), 500, 500, fill = true)!!
        try {
            assertEquals(889, bitmap.width)
            assertEquals(500, bitmap.height)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun `landscape cover fills the requested preview instead of being undersampled`() {
        val bytes = cover(1280, 720)
        val bitmap = BoundedBitmapDecoder.decode(bytes, 500, 500)!!
        try {
            assertEquals(500, bitmap.width)
            assertEquals(281, bitmap.height)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun `file previews retain requested detail and small covers are not enlarged`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "square-cover.png")
        try {
            file.writeBytes(cover(640, 640))
            val bitmap = BoundedBitmapDecoder.decode(file, 300, 300)!!
            try {
                assertEquals(300, bitmap.width)
                assertEquals(300, bitmap.height)
            } finally {
                bitmap.recycle()
            }
            val small = BoundedBitmapDecoder.decode(cover(80, 80), 300, 300)!!
            try {
                assertEquals(80, small.width)
                assertEquals(80, small.height)
            } finally {
                small.recycle()
            }
            assertNull(BoundedBitmapDecoder.decode(byteArrayOf(0, 1, 2), 300, 300))
        } finally {
            file.delete()
        }
    }

    private fun cover(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
