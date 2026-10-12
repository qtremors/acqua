package dev.qtremors.acqua.downloader

import android.app.PendingIntent
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import androidx.annotation.RequiresApi
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive

internal class UserInitiatedDownloadJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runningJobs = ConcurrentHashMap<Int, Job>()

    @RequiresApi(34)
    override fun onStartJob(params: JobParameters): Boolean {
        val id = params.extras.getString(UserInitiatedDownloadScheduler.KEY_TASK_ID)
            ?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }
            ?: return false
        if (UserInitiatedDownloadRegistry.observe(applicationContext).value[id]?.isActive == false) return false
        val input = DownloadWorkData.fromPersistableBundle(params.extras)
        val request = DownloadRequestStore(applicationContext).resolve(input) ?: run {
            runCatching { DownloadDiagnosticsStore(applicationContext).record(id.toString(),
                IllegalArgumentException("Missing or invalid download snapshot"), DownloadFailure.INVALID_REQUEST, DownloadPhase.QUEUED, "job") }
            update(
                id,
                DownloadQueueState.FAILED,
                error = DownloadFailure.INVALID_REQUEST.code,
                phase = DownloadPhase.COMPLETE
            )
            return false
        }
        val notifications = prepareNotifications(params, id, request) ?: return false

        val job = serviceScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            val transcript = DiagnosticTranscript(request.options.verboseDiagnostics)
            var failurePhase = DownloadPhase.QUEUED
            val throttle = ProgressUpdateThrottle()
            val executionJob = kotlinx.coroutines.currentCoroutineContext()[Job]!!
            try {
                DownloadExecutionGate.run(maximum = { dev.qtremors.acqua.data.settings.AppSettingsRepository(applicationContext).maximumConcurrentDownloads }) {
                    var latest = DownloadQueueItem(
                        id = id.toString(),
                        state = DownloadQueueState.RUNNING,
                        phase = DownloadPhase.TRANSFER
                    )
                    if (UserInitiatedDownloadRegistry.observe(applicationContext).value[id]?.state == DownloadQueueState.CANCELLED) {
                        throw CancellationException("Task was cancelled")
                    }
                    UserInitiatedDownloadRegistry.update(applicationContext, latest, starting = true)
                    val completed = (application as DownloadExecutionProvider).downloadExecution.execute(
                        request = request,
                        taskKey = id.toString(),
                        onProgress = progress@{ progress ->
                            if (!executionJob.isActive) return@progress
                            latest = latest.copy(
                                progress = progress.percent.coerceIn(0f, 100f),
                                etaSeconds = progress.etaSeconds.coerceAtLeast(0L),
                                downloadedBytes = progress.downloadedBytes.coerceAtLeast(0L),
                                totalBytes = progress.totalBytes.coerceAtLeast(0L)
                            )
                            if (throttle.shouldEmit(progress)) {
                                transcript.append(progress.statusLine)
                                UserInitiatedDownloadRegistry.update(applicationContext, latest)
                                notifications.update(
                                    request.media.title,
                                    latest.progress,
                                    latest.etaSeconds,
                                    latest.downloadedBytes,
                                    latest.totalBytes
                                )
                            }
                        },
                        onPhase = phase@{ phase ->
                            if (!executionJob.isActive) return@phase
                            latest = latest.copy(phase = phase)
                            failurePhase = phase
                            transcript.append(phase.name)
                            UserInitiatedDownloadRegistry.update(applicationContext, latest)
                        }
                    )
                    executionJob.ensureActive()
                    latest = latest.copy(
                        state = if (completed.skipped) DownloadQueueState.SKIPPED else DownloadQueueState.SUCCEEDED,
                        artifactCount = completed.artifactUris.size,
                        progress = 100f,
                        phase = DownloadPhase.COMPLETE
                    )
                    UserInitiatedDownloadRegistry.update(applicationContext, latest)
                    if (completed.skipped) notifications.cancel() else notifications.complete(
                        completed.title,
                        completed.uri,
                        completed.mimeType,
                        completed.mediaKind
                    )
                    jobFinished(params, false)
                }
            } catch (cancelled: CancellationException) {
                val stored = UserInitiatedDownloadRegistry.observe(applicationContext).value[id]
                if (stored?.state != DownloadQueueState.CANCELLED &&
                    stored?.state != DownloadQueueState.RETRYING
                ) {
                    update(id, DownloadQueueState.CANCELLED, phase = DownloadPhase.COMPLETE)
                }
                if (runningJobs[params.jobId] == executionJob || !runningJobs.containsKey(params.jobId)) notifications.cancel()
            } catch (error: Exception) {
                if (!executionJob.isActive) return@launch
                val failure = error.toDownloadFailure()
                runCatching { DownloadDiagnosticsStore(applicationContext).record(id.toString(), error, failure, failurePhase, "job:${request.media.backend.name}", transcript) }
                val attempts = applicationContext.getSharedPreferences("download_attempts", MODE_PRIVATE)
                val attempt = attempts.getInt(id.toString(), 0)
                val retry = failure.retryable && attempt < 2
                attempts.edit().putInt(id.toString(), attempt + 1).commit()
                if (!retry) DownloadNameOverrides.finish(applicationContext, id.toString())
                update(
                    id,
                    if (retry) DownloadQueueState.RETRYING else DownloadQueueState.FAILED,
                    error = failure.code,
                    phase = if (retry) DownloadPhase.QUEUED else DownloadPhase.COMPLETE
                )
                notifications.cancel()
                if (!retry) notifications.failed(failure)
                jobFinished(params, retry)
            } finally {
                runningJobs.remove(params.jobId, executionJob)
            }
        }
        runningJobs.put(params.jobId, job)?.cancel()
        job.start()
        return true
    }

    @RequiresApi(34)
    private fun prepareNotifications(params: JobParameters, id: UUID, request: DownloadWorkRequest): DownloadNotificationController? {
        val notifications = DownloadNotificationController(this, id, cancelIntent(id))
        return try {
            notifications.ensureChannel()
            setNotification(params, notificationId(id),
                notifications.progressNotification(request.media.title, 0f, 0L, 0L, 0L), JOB_END_NOTIFICATION_POLICY_REMOVE)
            notifications
        } catch (error: RuntimeException) {
            val failure = error.toDownloadFailure()
            runCatching { DownloadDiagnosticsStore(applicationContext).record(id.toString(), error, failure, DownloadPhase.QUEUED, "job") }
            update(id, DownloadQueueState.FAILED, error = failure.code, phase = DownloadPhase.COMPLETE)
            notifications.failed(failure)
            null
        }
    }

    @RequiresApi(34)
    override fun onStopJob(params: JobParameters): Boolean {
        val id = params.extras.getString(UserInitiatedDownloadScheduler.KEY_TASK_ID)
            ?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }
            ?: return false
        val retryForPlatformLimit = DownloadStopPolicy.shouldRetry(params.stopReason)
        if (UserInitiatedDownloadRegistry.observe(applicationContext).value[id]?.isActive == false) {
            runningJobs.remove(params.jobId)?.cancel()
            return false
        }
        if (retryForPlatformLimit) {
            update(
                id,
                DownloadQueueState.RETRYING,
                error = DownloadFailure.PLATFORM_LIMIT.code,
                phase = DownloadPhase.QUEUED
            )
            runCatching { DownloadDiagnosticsStore(applicationContext).record(id.toString(),
                IllegalStateException("Android stopped the download (reason ${params.stopReason})"),
                DownloadFailure.PLATFORM_LIMIT, DownloadPhase.QUEUED, "job") }
        }
        else update(id, DownloadQueueState.CANCELLED, phase = DownloadPhase.COMPLETE)
        runningJobs.remove(params.jobId)?.cancel()
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

}
