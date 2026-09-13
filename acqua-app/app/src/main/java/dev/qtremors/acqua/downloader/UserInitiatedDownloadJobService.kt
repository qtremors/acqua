package dev.qtremors.acqua.downloader

import android.app.PendingIntent
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import androidx.annotation.RequiresApi
import dev.qtremors.acqua.AcquaApp
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class UserInitiatedDownloadJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runningJobs = ConcurrentHashMap<Int, Job>()

    @RequiresApi(34)
    override fun onStartJob(params: JobParameters): Boolean {
        val id = params.extras.getString(UserInitiatedDownloadScheduler.KEY_TASK_ID)
            ?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }
            ?: return false
        val input = DownloadWorkData.fromPersistableBundle(params.extras)
        val request = DownloadWorkData.decode(input) ?: run {
            update(
                id,
                DownloadQueueState.FAILED,
                error = DownloadFailure.INVALID_REQUEST.code,
                phase = DownloadPhase.COMPLETE
            )
            return false
        }
        val notifications = DownloadNotificationController(
            this,
            id,
            cancelIntent(id)
        )
        notifications.ensureChannel()
        setNotification(
            params,
            notificationId(id),
            notifications.progressNotification(request.media.title, 0f, 0L, 0L, 0L),
            JOB_END_NOTIFICATION_POLICY_REMOVE
        )

        runningJobs[params.jobId] = serviceScope.launch {
            SERIAL_EXECUTION.withLock {
                var latest = DownloadQueueItem(
                    id = id.toString(),
                    state = DownloadQueueState.RUNNING,
                    phase = DownloadPhase.TRANSFER
                )
                UserInitiatedDownloadRegistry.update(applicationContext, latest)
                try {
                    val completed = (application as AcquaApp).dependencies.downloadExecutor.execute(
                        request = request,
                        taskKey = id.toString(),
                        onProgress = { progress ->
                            latest = latest.copy(
                                progress = progress.percent.coerceIn(0f, 100f),
                                etaSeconds = progress.etaSeconds.coerceAtLeast(0L),
                                downloadedBytes = progress.downloadedBytes.coerceAtLeast(0L),
                                totalBytes = progress.totalBytes.coerceAtLeast(0L)
                            )
                            UserInitiatedDownloadRegistry.update(applicationContext, latest)
                            notifications.update(
                                request.media.title,
                                latest.progress,
                                latest.etaSeconds,
                                latest.downloadedBytes,
                                latest.totalBytes
                            )
                        },
                        onPhase = { phase ->
                            latest = latest.copy(phase = phase)
                            UserInitiatedDownloadRegistry.update(applicationContext, latest)
                        }
                    )
                    latest = latest.copy(
                        state = DownloadQueueState.SUCCEEDED,
                        progress = 100f,
                        phase = DownloadPhase.COMPLETE
                    )
                    UserInitiatedDownloadRegistry.update(applicationContext, latest)
                    notifications.complete(
                        completed.title,
                        completed.uri,
                        completed.mimeType,
                        completed.mediaKind
                    )
                    jobFinished(params, false)
                } catch (cancelled: CancellationException) {
                    val stored = UserInitiatedDownloadRegistry.observe(applicationContext).value[id]
                    if (stored?.state != DownloadQueueState.CANCELLED &&
                        stored?.state != DownloadQueueState.RETRYING
                    ) {
                        update(id, DownloadQueueState.CANCELLED, phase = DownloadPhase.COMPLETE)
                    }
                    notifications.cancel()
                } catch (error: Exception) {
                    val failure = error.toDownloadFailure()
                    update(
                        id,
                        DownloadQueueState.FAILED,
                        error = failure.code,
                        phase = DownloadPhase.COMPLETE
                    )
                    notifications.cancel()
                    jobFinished(params, failure.retryable)
                } finally {
                    runningJobs.remove(params.jobId)
                }
            }
        }
        return true
    }

    @RequiresApi(34)
    override fun onStopJob(params: JobParameters): Boolean {
        runningJobs.remove(params.jobId)?.cancel()
        val id = params.extras.getString(UserInitiatedDownloadScheduler.KEY_TASK_ID)
            ?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }
            ?: return false
        val retryForPlatformLimit = params.stopReason == JobParameters.STOP_REASON_QUOTA ||
            params.stopReason == JobParameters.STOP_REASON_TIMEOUT
        if (retryForPlatformLimit) {
            update(
                id,
                DownloadQueueState.RETRYING,
                error = DownloadFailure.PLATFORM_LIMIT.code,
                phase = DownloadPhase.QUEUED
            )
        }
        return retryForPlatformLimit
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun update(
        id: UUID,
        state: DownloadQueueState,
        error: String? = null,
        phase: DownloadPhase
    ) {
        val existing = UserInitiatedDownloadRegistry.observe(applicationContext).value[id]
        UserInitiatedDownloadRegistry.update(
            applicationContext,
            (existing ?: DownloadQueueItem(id.toString(), state)).copy(
                state = state,
                error = error,
                phase = phase
            )
        )
    }

    private fun cancelIntent(id: UUID): PendingIntent = PendingIntent.getBroadcast(
        this,
        notificationId(id),
        Intent(this, DownloadCancelReceiver::class.java)
            .putExtra(UserInitiatedDownloadScheduler.KEY_TASK_ID, id.toString()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun notificationId(id: UUID): Int = id.hashCode() and Int.MAX_VALUE

    private companion object {
        val SERIAL_EXECUTION = Mutex()
    }
}
