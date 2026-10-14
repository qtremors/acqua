package dev.qtremors.acqua

import android.app.Application
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import coil3.request.crossfade
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.GifDecoder
import coil3.gif.AnimatedImageDecoder
import coil3.svg.SvgDecoder
import coil3.video.VideoFrameDecoder
import okio.Path.Companion.toOkioPath
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
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

class AcquaApp : Application(), SingletonImageLoader.Factory, Configuration.Provider,
    DownloadExecutionProvider, RenderedPageResolverDependencies {
    override fun attachBaseContext(base: android.content.Context) { super.attachBaseContext(dev.qtremors.acqua.settings.AppLanguages.wrap(base)) }
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
        val diagnosticStore = dev.qtremors.acqua.downloader.DownloadDiagnosticsStore(this)
        val crashHandler = Thread.getDefaultUncaughtExceptionHandler()
        if (crashHandler != null && crashHandler !is dev.qtremors.acqua.downloader.DiagnosticCrashHandler) {
            Thread.setDefaultUncaughtExceptionHandler(dev.qtremors.acqua.downloader.DiagnosticCrashHandler(
                diagnosticStore, { AppSettingsRepository(this).downloadDefaults().verboseDiagnostics }, crashHandler))
        }

        val abandonedDocuments = dev.qtremors.acqua.platform.storage.PendingDocumentRegistry.snapshot(this)
        val abandonedFolderTransfers = cacheDir.listFiles().orEmpty().filter {
            it.isFile && it.name.startsWith("folder-download-") && it.name.endsWith(".part")
        }
        applicationScope.launch {
            runCatching { diagnosticStore.load() }
            dev.qtremors.acqua.platform.storage.PendingDocumentRegistry.cleanup(this@AcquaApp, abandonedDocuments)
            abandonedFolderTransfers.forEach { it.delete() }
            PendingMediaStoreRegistry.cleanup(this@AcquaApp)
            YtDlpTemporaryFiles.cleanupAfterProcessRestart(this@AcquaApp)
        }

        SensitiveMemory.clearDelegate = { SingletonImageLoader.get(this).memoryCache?.clear() }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                applicationScope.launch {
                    runCatching { dependencies.ytDlpMaintenance.update(force = false) }
                        .onFailure { android.util.Log.w("AcquaApp", "Engine update check failed", dev.qtremors.acqua.downloader.SafeDiagnostics.redact(it)) }
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                SensitiveMemory.clear()
            }
        })
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(this, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizePercent(0.08)
                    .build()
            }
            .components {
                add(ThumbnailKeyer())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
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
