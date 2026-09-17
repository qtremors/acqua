package dev.qtremors.acqua

import android.app.Application
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
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
import dev.qtremors.acqua.downloader.YtDlpTemporaryFiles
import dev.qtremors.acqua.downloader.DownloadExecution
import dev.qtremors.acqua.downloader.DownloadExecutionProvider
import dev.qtremors.acqua.platform.storage.PendingMediaStoreRegistry
import dev.qtremors.acqua.data.session.InstagramSessionStore
import dev.qtremors.acqua.data.session.SavedWebsiteRepository
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.resolver.web.RenderedPageResolverDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AcquaApp : Application(), ImageLoaderFactory, Configuration.Provider,
    DownloadExecutionProvider, RenderedPageResolverDependencies {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val appSessionTracker = AppSessionTracker()
    val dependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { MainDependencies(this) }
    override val downloadExecution: DownloadExecution get() = dependencies.downloadExecutor
    override val resolverSettings: AppSettingsRepository get() = dependencies.settings
    override val resolverSavedWebsites: SavedWebsiteRepository get() = dependencies.savedWebsites
    override val resolverInstagramSessions: InstagramSessionStore get() = dependencies.instagramSessions

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(dependencies.workerFactory)
            .setJobSchedulerJobIdRange(WORK_MANAGER_JOB_ID_MIN, WORK_MANAGER_JOB_ID_MAX)
            .build()

    override fun onCreate() {
        super.onCreate()

        applicationScope.launch {
            PendingMediaStoreRegistry.cleanup(this@AcquaApp)
            YtDlpTemporaryFiles.cleanupAfterProcessRestart(this@AcquaApp)
        }

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

    private companion object {
        const val WORK_MANAGER_JOB_ID_MIN = 0x00001000
        const val WORK_MANAGER_JOB_ID_MAX = 0x0fffffff
    }
}
