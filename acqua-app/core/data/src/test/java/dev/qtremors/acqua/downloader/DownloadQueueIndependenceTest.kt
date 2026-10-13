package dev.qtremors.acqua.downloader

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DownloadQueueIndependenceTest {
    @Test fun `terminal checkpoint and active scheduler transition produce one task row`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = object : DownloadExecution {
            override suspend fun execute(request: DownloadWorkRequest, taskKey: String,
                onProgress: (YtDlpProgress) -> Unit, onPhase: (DownloadPhase) -> Unit) =
                DownloadExecutionResult(Uri.parse("content://downloads/first"), "item", "video/mp4", MediaKind.VIDEO)
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder()
            .setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor())
            .setWorkerFactory(AcquaWorkerFactory(executor)).build())
        val coordinator = DownloadWorkCoordinator(context)
        val id = coordinator.enqueueDirect(ResolvedMedia("https://example.com/video.mp4", MediaKind.VIDEO), 0, "https://example.com/post")
        DownloadTaskStore(context).record(listOf(DownloadQueueItem(id.toString(), DownloadQueueState.SUCCEEDED,
            createdAt = System.currentTimeMillis())))
        val queue = withTimeout(5000) { coordinator.observeQueue().first { it.items.any { task -> task.id == id.toString() } } }
        assertEquals(1, queue.items.count { it.id == id.toString() })
        assertTrue(queue.items.single { it.id == id.toString() }.isActive)
        coordinator.cancel(id)
        Unit
    }
    @Test fun `partial failures remain retryable after scheduler records are pruned`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = object : DownloadExecution {
            override suspend fun execute(request: DownloadWorkRequest, taskKey: String,
                onProgress: (YtDlpProgress) -> Unit, onPhase: (DownloadPhase) -> Unit): DownloadExecutionResult {
                DownloadRequestStore(context).recordPublished(request.publicationId!!, "first", Uri.parse("content://downloads/first"))
                error("permanent publication failure")
            }
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder()
            .setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor())
            .setWorkerFactory(AcquaWorkerFactory(executor)).build())
        val coordinator = DownloadWorkCoordinator(context)
        val media = ResolvedMedia("https://example.com/video.mp4", MediaKind.VIDEO, backend = dev.qtremors.acqua.domain.MediaBackend.YT_DLP,
            durationSeconds = 100, preciseDurationSeconds = 100.875,
            chapters = (0..99).map { dev.qtremors.acqua.domain.MediaChapter("Chapter $it " + "x".repeat(200), it.toDouble(), it + 1.0) })
        assertTrue(MediaTimelineSource.encode(media).length > 10240)
        val id = coordinator.enqueue(media, media, YtDlpDownloadOptions(splitChapters = true), "https://example.com/post")
        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(id)
        val terminal = withTimeout(5000) { coordinator.observeQueue().first { it.items.any { item -> item.id == id.toString() && !item.isActive } } }
        assertEquals(1, terminal.items.single { it.id == id.toString() }.artifactCount)
        val diagnostic = requireNotNull(coordinator.diagnostic(id))
        assertEquals(DownloadFailure.UNKNOWN, diagnostic.failure)
        assertEquals("worker:YT_DLP", diagnostic.backend)
        WorkManager.getInstance(context).pruneWork().result.get()
        assertNull(WorkManager.getInstance(context).getWorkInfoById(id).get())
        assertEquals(diagnostic, DownloadDiagnosticsStore(context).get(id.toString()))
        val restored = withTimeout(5000) { DownloadWorkCoordinator(context).observeQueue().first() }
        assertTrue(restored.items.single { it.id == id.toString() }.canRetry)
        val retry = coordinator.retry(id)!!
        assertEquals(id.toString(), DownloadRequestStore(context).input(retry)?.getString(DownloadWorkData.KEY_PUBLICATION_ID))
        val snapshot = DownloadRequestStore(context).resolve(androidx.work.Data.Builder().putString(DownloadWorkData.KEY_REQUEST_REF, retry.toString()).build())!!
        assertEquals(media.chapters, snapshot.media.chapters)
        assertEquals(media.preciseDurationSeconds, snapshot.media.preciseDurationSeconds)
        coordinator.cancel(retry)
    }

    @Test fun `a permanent failure cannot fail already accepted unrelated work`() = verify(failFirst = true)
    @Test fun `canceling the first task cannot cancel accepted siblings`() = verify(cancelIndex = 0)
    @Test fun `canceling a queued middle task cannot cancel following work`() = verify(cancelIndex = 1)

    private fun verify(failFirst: Boolean = false, cancelIndex: Int? = null) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executed = mutableListOf<Int>()
        val snapshots = mutableListOf<DownloadWorkRequest>()
        val executor = object : DownloadExecution {
            override suspend fun execute(request: DownloadWorkRequest, taskKey: String,
                onProgress: (YtDlpProgress) -> Unit, onPhase: (DownloadPhase) -> Unit): DownloadExecutionResult {
                executed += request.itemIndex
                snapshots += request
                if (failFirst && request.itemIndex == 0) error("permanent failure")
                return DownloadExecutionResult(Uri.parse("content://downloads/${request.itemIndex}"), "item", "video/mp4", MediaKind.VIDEO)
            }
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder()
            .setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor())
            .setWorkerFactory(AcquaWorkerFactory(executor)).build())
        val settings = AppSettingsRepository(context)
        settings.setBaseFolder("Accepted")
        settings.setEmbedMetadata(false)
        val coordinator = DownloadWorkCoordinator(context)
        val ids = (0..2).map { coordinator.enqueueDirect(ResolvedMedia("https://example.com/$it.mp4", MediaKind.VIDEO), it, "https://example.com/list", 3) }
        settings.setBaseFolder("Changed later")
        settings.setEmbedMetadata(true)
        assertTrue(coordinator.rename(ids.first(), "Accepted rename"))
        cancelIndex?.let { coordinator.cancel(ids[it]); WorkManager.getInstance(context).cancelWorkById(ids[it]).result.get() }
        val driver = requireNotNull(WorkManagerTestInitHelper.getTestDriver(context))
        ids.forEachIndexed { index, id -> if (index != cancelIndex) driver.setAllConstraintsMet(id) }
        val terminal = withTimeout(5000) { coordinator.observe(ids).first { jobs -> jobs.none { it.isActive } } }
        assertEquals((0..2).filter { it != cancelIndex }, executed)
        assertTrue(terminal.filterIndexed { index, _ -> index != cancelIndex && !(failFirst && index == 0) }.all { it.state == DownloadQueueState.SUCCEEDED })
        assertTrue(snapshots.all { it.storageSettings?.baseFolder == "Accepted" && !it.options.embedMetadata })
        if (cancelIndex != 0) assertEquals("Accepted rename", snapshots.first().media.downloadName)
        if (failFirst) {
            assertEquals(DownloadQueueState.FAILED, terminal.first().state)
            val retried = coordinator.retry(ids.first())!!
            assertEquals(retried, coordinator.retry(ids.first()))
            assertNotEquals(ids.first(), retried)
            assertEquals("Accepted rename", DownloadRequestStore(context).input(retried)?.let(DownloadWorkData::decode)?.media?.downloadName)
            coordinator.cancel(retried)
        }
        WorkManager.getInstance(context).pruneWork().result.get()
        Unit
    }
}
