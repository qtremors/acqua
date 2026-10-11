package dev.qtremors.acqua.ui.image

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.CancellationSignal
import android.util.Size
import androidx.test.core.app.ApplicationProvider
import coil3.BitmapImage
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.Scale
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Resetter
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowMediaMetadataRetriever

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [AudioAlbumArtFetcherTest.RetrieverShadow::class, AudioAlbumArtFetcherTest.ResolverShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AudioAlbumArtFetcherTest {
    @Before
    fun resetArtwork() {
        RetrieverShadow.picture = null
        ResolverShadow.thumbnailCalls = 0
    }

    @Test
    fun `cropped cover preview keeps enough detail on the shorter side`() = runBlocking {
        val cover = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        RetrieverShadow.picture = ByteArrayOutputStream().use { stream ->
            cover.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
        cover.recycle()
        val image = fetch(Scale.FILL)!!
        try {
            assertEquals(533, image.width)
            assertEquals(300, image.height)
            assertEquals(0, ResolverShadow.thumbnailCalls)
        } finally {
            image.recycle()
        }
    }

    @Test
    fun `embedded cover is displayed at requested detail before any cached thumbnail`() = runBlocking {
        val cover = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
        cover.eraseColor(Color.RED)
        RetrieverShadow.picture = ByteArrayOutputStream().use { stream ->
            cover.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
        cover.recycle()
        val image = fetch()!!
        try {
            assertEquals(300, image.width)
            assertEquals(300, image.height)
            assertEquals(Color.RED, image.getPixel(150, 150))
            assertEquals(0, ResolverShadow.thumbnailCalls)
        } finally {
            image.recycle()
        }
    }

    @Test
    fun `missing or unreadable embedded covers fall back to the system thumbnail`() = runBlocking {
        for (picture in listOf(null, byteArrayOf(0, 1, 2))) {
            RetrieverShadow.picture = picture
            val image = fetch()
            assertNotNull(image)
            image!!.recycle()
        }
        assertEquals(2, ResolverShadow.thumbnailCalls)
    }

    private suspend fun fetch(scale: Scale = Scale.FIT): Bitmap? {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = AudioAlbumArtFetcher(
            File(context.cacheDir, "cover.m4a"),
            Options(context, size = coil3.size.Size(300, 300), scale = scale),
            contentUri = "content://media/external/audio/media/1"
        ).fetch() as? ImageFetchResult
        return (result?.image as? BitmapImage)?.bitmap
    }

    @Implements(MediaMetadataRetriever::class)
    class RetrieverShadow : ShadowMediaMetadataRetriever() {
        @Implementation
        fun getEmbeddedPicture(): ByteArray? = picture

        companion object {
            var picture: ByteArray? = null
            @JvmStatic @Resetter fun resetPicture() { picture = null }
        }
    }

    @Implements(ContentResolver::class)
    class ResolverShadow : ShadowContentResolver() {
        @Implementation
        fun loadThumbnail(uri: Uri, size: Size, signal: CancellationSignal?): Bitmap {
            thumbnailCalls++
            return Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        }

        companion object {
            var thumbnailCalls = 0
            @JvmStatic @Resetter fun resetCalls() { thumbnailCalls = 0 }
        }
    }
}
