package dev.qtremors.acqua

import android.app.Application
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dev.qtremors.acqua.ui.image.AudioAlbumArtFetcher
import dev.qtremors.acqua.ui.image.ThumbnailKeyer
import dev.qtremors.acqua.ui.image.VideoThumbnailFetcher
import dev.qtremors.acqua.ui.security.SensitiveMemory

class AcquaApp : Application(), ImageLoaderFactory {
    val appSessionTracker = AppSessionTracker()

    override fun onCreate() {
        super.onCreate()

        SensitiveMemory.clearDelegate = { Coil.imageLoader(this).memoryCache?.clear() }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                SensitiveMemory.clear()
            }
        })
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.08)
                    .build()
            }
            .components {
                add(ThumbnailKeyer())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                }
                add(GifDecoder.Factory())
                add(SvgDecoder.Factory())
                add(VideoThumbnailFetcher.Factory())
                add(VideoThumbnailFetcher.KeyFactory())
                add(VideoFrameDecoder.Factory())
                add(AudioAlbumArtFetcher.Factory())
                add(AudioAlbumArtFetcher.KeyFactory())
            }
            .crossfade(true)
            .build()
    }
}
