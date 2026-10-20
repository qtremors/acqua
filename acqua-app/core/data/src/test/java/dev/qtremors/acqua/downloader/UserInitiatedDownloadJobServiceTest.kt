package dev.qtremors.acqua.downloader

import android.app.Application
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.net.Uri
import android.os.PersistableBundle
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowJobService
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = UserInitiatedDownloadJobServiceTest.ExecutionApplication::class,
    shadows = [UserInitiatedDownloadJobServiceTest.RecordingJobService::class])
class UserInitiatedDownloadJobServiceTest {
    private val context = ApplicationProvider.getApplicationContext<ExecutionApplication>()

    @Test fun `connectivity timeout and quota stops persist retry checkpoints before cancelling execution`() {
        for (reason in listOf(JobParameters.STOP_REASON_CONSTRAINT_CONNECTIVITY, JobParameters.STOP_REASON_TIMEOUT, JobParameters.STOP_REASON_QUOTA)) {
            val started = LinkedBlockingQueue<Unit>()
            val cancelledState = LinkedBlockingQueue<DownloadQueueItem>()
            val id = request()
            context.downloadExecution = object : DownloadExecution {
                override suspend fun execute(request: DownloadWorkRequest, taskKey: String, onProgress: (YtDlpProgress) -> Unit,
                    onPhase: (DownloadPhase) -> Unit): DownloadExecutionResult {
                    onProgress(YtDlpProgress(percent = 25f, downloadedBytes = 25, totalBytes = 100))
                    onPhase(DownloadPhase.PROCESSING)
                    started.put(Unit)
                    try { awaitCancellation() } finally {
                        onProgress(YtDlpProgress(percent = 99f, downloadedBytes = 99, totalBytes = 100))
                        onPhase(DownloadPhase.FINALIZING)
                        cancelledState.put(UserInitiatedDownloadRegistry.observe(context).value.getValue(id))
                    }
                }
            }
            val controller = Robolectric.buildService(UserInitiatedDownloadJobService::class.java).create()
            try {
                val params = parameters(id, reason)
                assertTrue(controller.get().onStartJob(params))
                assertNotNull(started.poll(2, TimeUnit.SECONDS))
                assertTrue(controller.get().onStopJob(params))
                val checkpoint = requireNotNull(cancelledState.poll(2, TimeUnit.SECONDS))
                assertEquals(DownloadQueueState.RETRYING, checkpoint.state)
                assertEquals(DownloadPhase.QUEUED, checkpoint.phase)
                assertEquals(25L, checkpoint.downloadedBytes)
                UserInitiatedDownloadRegistry.reload(context)
                assertEquals(checkpoint, UserInitiatedDownloadRegistry.observe(context).value[id])
                assertEquals(DownloadFailure.PLATFORM_LIMIT, DownloadDiagnosticsStore(context).get(id.toString())?.failure)
            } finally { controller.destroy() }
        }
    }

    @Test fun `timeouts and transient HTTP failures stop after two automatic retries and cannot restart terminal tasks`() {
      for (failure in listOf(SocketTimeoutException("fixture timeout"),
          dev.qtremors.acqua.data.network.MediaHttpException(503), dev.qtremors.acqua.data.network.MediaHttpException(429))) {
        val id = request()
        val calls = LinkedBlockingQueue<Unit>()
        context.downloadExecution = execution {
            calls.put(Unit)
            throw failure
        }
        val controller = Robolectric.buildService(UserInitiatedDownloadJobService::class.java).create()
        try {
            val params = parameters(id)
            val shadow = Shadow.extract<RecordingJobService>(controller.get())
            for (attempt in 0..2) {
                assertTrue(controller.get().onStartJob(params))
                assertNotNull(calls.poll(2, TimeUnit.SECONDS))
                assertEquals(attempt < 2, shadow.finished.poll(2, TimeUnit.SECONDS))
                assertEquals(if (attempt < 2) DownloadQueueState.RETRYING else DownloadQueueState.FAILED,
                    UserInitiatedDownloadRegistry.observe(context).value[id]?.state)
            }
            assertFalse(controller.get().onStartJob(params))
            assertTrue(calls.isEmpty())
            val diagnostic = requireNotNull(DownloadDiagnosticsStore(context).get(id.toString()))
            assertEquals(DownloadFailure.NETWORK, diagnostic.failure)
            assertEquals("job:DIRECT", diagnostic.backend)
        } finally { controller.destroy() }
      }
    }

    @Test fun `explicit cancellation is final even when a connectivity stop races afterward`() {
        val id = request()
        val started = LinkedBlockingQueue<Unit>()
        val cancelled = LinkedBlockingQueue<DownloadQueueState>()
        context.downloadExecution = object : DownloadExecution {
            override suspend fun execute(request: DownloadWorkRequest, taskKey: String, onProgress: (YtDlpProgress) -> Unit,
                onPhase: (DownloadPhase) -> Unit): DownloadExecutionResult {
                started.put(Unit)
                try { awaitCancellation() } finally {
                    cancelled.put(UserInitiatedDownloadRegistry.observe(context).value.getValue(id).state)
                }
            }
        }
        val controller = Robolectric.buildService(UserInitiatedDownloadJobService::class.java).create()
        try {
            val params = parameters(id, JobParameters.STOP_REASON_CONSTRAINT_CONNECTIVITY)
            assertTrue(controller.get().onStartJob(params))
            assertNotNull(started.poll(2, TimeUnit.SECONDS))
            UserInitiatedDownloadRegistry.update(context, DownloadQueueItem(id.toString(), DownloadQueueState.CANCELLED, phase = DownloadPhase.COMPLETE))
            assertFalse(controller.get().onStopJob(params))
            assertEquals(DownloadQueueState.CANCELLED, cancelled.poll(2, TimeUnit.SECONDS))
            assertFalse(controller.get().onStartJob(params))
        } finally { controller.destroy() }
    }

    @Test fun `HTTP failures are final and late platform stops preserve successful outcomes`() {
        for (success in listOf(false, true)) {
            val id = request()
            context.downloadExecution = execution {
                if (!success) throw dev.qtremors.acqua.data.network.MediaHttpException(403)
                DownloadExecutionResult(Uri.parse("content://fixture/media"), "Fixture", "video/mp4", MediaKind.VIDEO)
            }
            val controller = Robolectric.buildService(UserInitiatedDownloadJobService::class.java).create()
            try {
                val params = parameters(id, JobParameters.STOP_REASON_TIMEOUT)
                val shadow = Shadow.extract<RecordingJobService>(controller.get())
                assertTrue(controller.get().onStartJob(params))
                assertEquals(false, shadow.finished.poll(2, TimeUnit.SECONDS))
                val expected = if (success) DownloadQueueState.SUCCEEDED else DownloadQueueState.FAILED
                assertFalse(controller.get().onStopJob(params))
                assertEquals(expected, UserInitiatedDownloadRegistry.observe(context).value[id]?.state)
                assertFalse(controller.get().onStartJob(params))
            } finally { controller.destroy() }
        }
    }

    @Test fun `notification permission errors retain a failure report without starting execution`() {
        val id = request()
        var executed = false
        context.downloadExecution = execution { executed = true; error("Must not execute") }
        val controller = Robolectric.buildService(UserInitiatedDownloadJobService::class.java).create()
        try {
            Shadow.extract<RecordingJobService>(controller.get()).rejectNotification = true
            assertFalse(controller.get().onStartJob(parameters(id)))
            assertFalse(executed)
            assertEquals(DownloadQueueState.FAILED, UserInitiatedDownloadRegistry.observe(context).value[id]?.state)
            assertEquals(DownloadFailure.STORAGE, DownloadDiagnosticsStore(context).get(id.toString())?.failure)
        } finally { controller.destroy() }
    }

    @Test fun `two native jobs have independent execution notification and cancellation while another waits`() {
        val settings = dev.qtremors.acqua.data.settings.AppSettingsRepository(context)
        settings.setMaximumConcurrentDownloads(2)
        val ids = List(3) { request() }
        val started = LinkedBlockingQueue<UUID>()
        val stopped = LinkedBlockingQueue<UUID>()
        context.downloadExecution = object : DownloadExecution {
            override suspend fun execute(request: DownloadWorkRequest, taskKey: String, onProgress: (YtDlpProgress) -> Unit,
                onPhase: (DownloadPhase) -> Unit): DownloadExecutionResult {
                val id = UUID.fromString(taskKey)
                started.put(id)
                try { awaitCancellation() } finally { stopped.put(id) }
            }
        }
        val controller = Robolectric.buildService(UserInitiatedDownloadJobService::class.java).create()
        try {
            for (id in ids.take(2)) {
                assertTrue(controller.get().onStartJob(parameters(id)))
                assertEquals(id, started.poll(2, TimeUnit.SECONDS))
            }
            assertTrue(controller.get().onStartJob(parameters(ids[2])))
            assertEquals(DownloadQueueState.QUEUED, UserInitiatedDownloadRegistry.observe(context).value[ids[2]]?.state)
            val notifications = Shadow.extract<RecordingJobService>(controller.get()).notificationIds
            assertEquals(3, notifications.values.distinct().size)
            UserInitiatedDownloadRegistry.update(context, DownloadQueueItem(ids[0].toString(), DownloadQueueState.CANCELLED, phase = DownloadPhase.COMPLETE))
            assertFalse(controller.get().onStopJob(parameters(ids[0], JobParameters.STOP_REASON_USER)))
            assertEquals(ids[0], stopped.poll(2, TimeUnit.SECONDS))
            assertEquals(ids[2], started.poll(2, TimeUnit.SECONDS))
            assertEquals(DownloadQueueState.RUNNING, UserInitiatedDownloadRegistry.observe(context).value[ids[1]]?.state)
        } finally {
            controller.destroy()
            repeat(2) { stopped.poll(2, TimeUnit.SECONDS) }
            settings.setMaximumConcurrentDownloads(1)
        }
    }

    private fun request(): UUID {
        val id = UUID.randomUUID()
        val media = ResolvedMedia("https://example.com/fixture.mp4", MediaKind.VIDEO)
        DownloadRequestStore(context).put(id, DownloadWorkData.directRequest(media, 0, media.url))
        UserInitiatedDownloadRegistry.update(context, DownloadQueueItem(id.toString(), DownloadQueueState.QUEUED))
        return id
    }

    private fun parameters(id: UUID, reason: Int = JobParameters.STOP_REASON_UNDEFINED): JobParameters =
        Shadow.newInstanceOf(JobParameters::class.java).also {
            ReflectionHelpers.setField(it, "jobId", UserInitiatedDownloadScheduler.jobId(id))
            ReflectionHelpers.setField(it, "extras", PersistableBundle().apply {
                putString(UserInitiatedDownloadScheduler.KEY_TASK_ID, id.toString())
                putString(DownloadWorkData.KEY_REQUEST_REF, id.toString())
            })
            ReflectionHelpers.setField(it, "mStopReason", reason)
        }

    private fun execution(block: () -> DownloadExecutionResult) = object : DownloadExecution {
        override suspend fun execute(request: DownloadWorkRequest, taskKey: String, onProgress: (YtDlpProgress) -> Unit,
            onPhase: (DownloadPhase) -> Unit) = block()
    }

    class ExecutionApplication : Application(), DownloadExecutionProvider {
        override lateinit var downloadExecution: DownloadExecution
    }

    @Implements(JobService::class)
    class RecordingJobService : ShadowJobService() {
        val finished = LinkedBlockingQueue<Boolean>()
        val notificationIds = java.util.concurrent.ConcurrentHashMap<Int, Int>()
        var rejectNotification = false
        @Implementation override fun setNotification(params: JobParameters, id: Int, notification: android.app.Notification, policy: Int) {
            if (rejectNotification) throw SecurityException("fixture notification permission denied")
            notificationIds[params.jobId] = id
            super.setNotification(params, id, notification, policy)
        }
        @Implementation override fun jobFinished(params: JobParameters, needsReschedule: Boolean) {
            finished.put(needsReschedule)
        }
    }
}
