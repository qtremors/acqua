package dev.qtremors.acqua.downloader

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowJobScheduler

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [DownloadSchedulingRollbackTest.RejectingJobScheduler::class])
class DownloadSchedulingRollbackTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val platform = context.getSystemService(JobScheduler::class.java)
    private val schedulerShadow = Shadow.extract<RejectingJobScheduler>(platform)

    @Test fun `platform exceptions and rejection remove provisional registrations and names`() {
        val scheduler = UserInitiatedDownloadScheduler(context)
        for (throws in listOf(false, true)) {
            schedulerShadow.throwOnSchedule = throws
            val id = UUID.randomUUID()
            if (throws) assertThrows(IllegalStateException::class.java) { scheduler.schedule(Data.EMPTY, id) }
            else assertNull(scheduler.schedule(Data.EMPTY, id))
            UserInitiatedDownloadRegistry.reload(context)
            assertFalse(UserInitiatedDownloadRegistry.contains(context, id))
            assertFalse(DownloadNameOverrides.rename(context, id.toString(), "Orphan"))
            assertTrue(platform.allPendingJobs.isEmpty())
        }
        assertEquals(2, schedulerShadow.attempts.size)
    }

    @Test fun `a rejected native job falls back to exactly one real request`() = verifyFallback(throws = false)
    @Test fun `a throwing native scheduler falls back to exactly one real request`() = verifyFallback(throws = true)

    private fun verifyFallback(throws: Boolean) = runBlocking {
        var executions = 0
        val execution = object : DownloadExecution {
            override suspend fun execute(request: DownloadWorkRequest, taskKey: String,
                onProgress: (YtDlpProgress) -> Unit, onPhase: (DownloadPhase) -> Unit): DownloadExecutionResult {
                executions++
                return DownloadExecutionResult(Uri.parse("content://downloads/result"), "Result", "image/jpeg", MediaKind.IMAGE)
            }
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder()
            .setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor())
            .setWorkerFactory(AcquaWorkerFactory(execution)).build())
        schedulerShadow.throwOnSchedule = throws
        val coordinator = DownloadWorkCoordinator(context)
        val id = coordinator.enqueueDirect(ResolvedMedia("https://example.com/photo.jpg", MediaKind.IMAGE),
            0, "https://example.com/post")
        assertEquals(1, schedulerShadow.attempts.size)
        assertTrue(schedulerShadow.attempts.single().isUserInitiated)
        UserInitiatedDownloadRegistry.reload(context)
        assertFalse(UserInitiatedDownloadRegistry.contains(context, id))
        assertTrue(DownloadNameOverrides.rename(context, id.toString(), "Fallback name"))
        assertEquals(listOf(id), WorkManager.getInstance(context).getWorkInfosByTag("acqua-yt-dlp-download").get().map { it.id })
        assertEquals(listOf(id.toString()), withTimeout(5_000) {
            coordinator.observeQueue().first { it.items.any { task -> task.id == id.toString() } }
        }.items.map { it.id })
        requireNotNull(WorkManagerTestInitHelper.getTestDriver(context)).setAllConstraintsMet(id)
        val completed = withTimeout(5_000) { coordinator.observeQueue().first { it.items.any { task -> task.id == id.toString() && !task.isActive } } }
        assertEquals(1, executions)
        assertEquals(1, completed.items.size)
        assertEquals(DownloadQueueState.SUCCEEDED, completed.items.single().state)
        WorkManager.getInstance(context).pruneWork().result.get()
        Unit
    }

    @Implements(className = "android.app.JobSchedulerImpl", isInAndroidSdk = false)
    class RejectingJobScheduler : ShadowJobScheduler.ShadowJobSchedulerImpl() {
        var throwOnSchedule = false
        val attempts = mutableListOf<JobInfo>()
        @Implementation
        override fun schedule(job: JobInfo): Int {
            attempts += job
            if (throwOnSchedule) throw IllegalStateException("Injected scheduling failure")
            return JobScheduler.RESULT_FAILURE
        }
    }
}
