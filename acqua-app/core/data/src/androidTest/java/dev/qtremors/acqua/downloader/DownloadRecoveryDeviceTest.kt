package dev.qtremors.acqua.downloader

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkManager
import dev.qtremors.acqua.data.history.HistoryRepository
import dev.qtremors.acqua.data.network.HttpMediaClient
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

class DeviceRecoveryApplication : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        dev.qtremors.acqua.platform.storage.PendingMediaStoreRegistry.cleanup(this)
        YtDlpTemporaryFiles.cleanupAfterProcessRestart(this)
    }
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(AcquaWorkerFactory(DownloadExecutor(
            this, AppSettingsRepository(this), HistoryRepository(this), HttpMediaClient()))).build()
}

/** Run preparation and recovery in separate instrumentation processes against a local fixture. */
@RunWith(AndroidJUnit4::class)
class DownloadRecoveryDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixtureState get() = context.getSharedPreferences("device_recovery_fixture", Context.MODE_PRIVATE)
    private fun fixtureUrl(): String {
        val url = InstrumentationRegistry.getArguments().getString("fixtureUrl")
        assumeTrue("Recovery checks require a local fixtureUrl and separate instrumentation processes.", url != null)
        return requireNotNull(url)
    }

    @Test fun prepareInterruptedTask() = runBlocking {
        val fixtureUrl = fixtureUrl()
        WorkManager.getInstance(context).cancelAllWork().result.get()
        val previous = HistoryRepository(context)
        previous.load().forEach { context.contentResolver.delete(android.net.Uri.parse(it.fileUri), null, null) }
        previous.clear()
        val settings = AppSettingsRepository(context)
        settings.setBaseFolder("AcquaDeviceCheck")
        settings.setCategorizeMedia(false)
        val name = "recovery_${UUID.randomUUID()}"
        val coordinator = DownloadWorkCoordinator(context)
        val id = coordinator.enqueueDirectConfigured(ResolvedMedia(fixtureUrl, MediaKind.IMAGE,
            title = name, downloadName = name, mimeType = "image/png", fileExtension = "png"),
            0, fixtureUrl, 1, YtDlpDownloadOptions(embedMetadata = false))
        assertTrue(fixtureState.edit().putString("id", id.toString()).putString("url", fixtureUrl)
            .putInt("pid", android.os.Process.myPid()).commit())
        withTimeout(60_000) {
            coordinator.observe(id).first { it.isActive && it.downloadedBytes >= 256 * 1024 && it.progress < 90 }
        }
        Unit
    }

    @Test fun recoverInterruptedTaskAndSkipDuplicate() = runBlocking {
        fixtureUrl()
        assertNotEquals(fixtureState.getInt("pid", 0), android.os.Process.myPid())
        val id = UUID.fromString(fixtureState.getString("id", null) ?: error("Run preparation first."))
        val coordinator = DownloadWorkCoordinator(context)
        val restored = withTimeout(60_000) {
            coordinator.observeQueue().first { queue -> queue.items.any { it.id == id.toString() && !it.isActive } }
        }.items.single { it.id == id.toString() }
        assertEquals(DownloadQueueState.SUCCEEDED, restored.state)
        assertEquals("AcquaDeviceCheck", restored.acceptedFolder)
        assertEquals(1, restored.artifactCount)
        val history = HistoryRepository(context)
        val saved = history.load().filter { it.url == fixtureState.getString("url", null) }
        assertEquals(1, saved.size)
        val request = DownloadRequestStore(context).input(id)!!.let(DownloadWorkData::decode)!!
        val duplicate = coordinator.enqueueDirectConfigured(request.media, 0, request.sourceUrl, 1,
            request.options.copy(skipArchived = true))
        val skipped = withTimeout(30_000) { coordinator.observe(duplicate).first { !it.isActive } }
        assertEquals(DownloadQueueState.SKIPPED, skipped.state)
        assertEquals(1, history.load().count { it.url == request.sourceUrl })
        WorkManager.getInstance(context).pruneWork().result.get()
        assertNull(WorkManager.getInstance(context).getWorkInfoById(id).get())
        assertTrue(coordinator.observeQueue().first().items.any { it.id == id.toString() })
        saved.forEach { context.contentResolver.delete(android.net.Uri.parse(it.fileUri), null, null) }
        history.clear()
        coordinator.forgetTask(id)
        coordinator.forgetTask(duplicate)
        Unit
    }
}
