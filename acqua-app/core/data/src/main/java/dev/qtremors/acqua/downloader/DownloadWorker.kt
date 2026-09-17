package dev.qtremors.acqua.downloader

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

internal class DownloadWorker(
    appContext: Context,
    parameters: WorkerParameters,
    private val executor: DownloadExecution
) : CoroutineWorker(appContext, parameters) {
    private val notifications = DownloadNotificationController(appContext, id)
    private val progressThrottle = ProgressUpdateThrottle()

    override suspend fun doWork(): Result {
        val request = DownloadWorkData.decode(inputData)
            ?: return Result.failure(DownloadWorkData.error(DownloadFailure.INVALID_REQUEST.code))
        notifications.ensureChannel()
        setForeground(notifications.foregroundInfo(request.media.title, 0f, 0L, 0L, 0L))

        return try {
            var latestProgress = YtDlpProgress()
            var latestPhase = DownloadPhase.TRANSFER
            val completed = executor.execute(
                request = request,
                taskKey = id.toString(),
                onProgress = { progress ->
                    latestProgress = progress
                    if (progressThrottle.shouldEmit(progress)) {
                        publishProgress(request, progress, force = true)
                    }
                },
                onPhase = { phase ->
                    latestPhase = phase
                    setProgressAsync(
                        DownloadWorkData.progress(
                            latestProgress.percent,
                            latestProgress.etaSeconds,
                            latestProgress.downloadedBytes,
                            latestProgress.totalBytes,
                            phase
                        )
                    )
                    if (phase == DownloadPhase.PROCESSING) {
                        setForegroundAsync(
                            notifications.foregroundInfo(
                                request.media.title,
                                latestProgress.percent,
                                latestProgress.etaSeconds,
                                latestProgress.downloadedBytes,
                                latestProgress.totalBytes,
                                phase
                            )
                        )
                    }
                }
            )
            publishProgress(request, latestProgress, latestPhase, force = true)
            notifications.complete(
                title = completed.title,
                uri = completed.uri,
                mimeType = completed.mimeType,
                mediaKind = completed.mediaKind
            )
            Result.success()
        } catch (cancelled: CancellationException) {
            notifications.cancel()
            throw cancelled
        } catch (error: Throwable) {
            notifications.cancel()
            if (error is VirtualMachineError || error is ThreadDeath) throw error
            val failure = error.toDownloadFailure()
            Log.e(TAG, "Background download failed (${failure.code})", SafeDiagnostics.redact(error))
            if (failure.retryable && runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.failure(DownloadWorkData.error(failure.code))
            }
        }
    }

    private fun publishProgress(
        request: DownloadWorkRequest,
        progress: YtDlpProgress,
        phase: DownloadPhase = DownloadPhase.TRANSFER,
        force: Boolean = false
    ) {
        if (!force && !progressThrottle.shouldEmit(progress)) return
        val percent = progress.percent.coerceIn(0f, 100f)
        setProgressAsync(
            DownloadWorkData.progress(
                percent,
                progress.etaSeconds,
                progress.downloadedBytes,
                progress.totalBytes,
                phase
            )
        )
        notifications.update(
            request.media.title,
            percent,
            progress.etaSeconds,
            progress.downloadedBytes,
            progress.totalBytes
        )
    }

    private companion object {
        const val TAG = "DownloadWorker"
        const val MAX_RETRIES = 2
    }
}

internal class ProgressUpdateThrottle(
    private val minimumIntervalNanos: Long = TimeUnit.MILLISECONDS.toNanos(350),
    private val minimumByteDelta: Long = 256L * 1024L,
    private val clockNanos: () -> Long = System::nanoTime
) {
    private var lastNanos = Long.MIN_VALUE
    private var lastBytes = 0L
    private var lastPercent = -1

    fun shouldEmit(progress: YtDlpProgress): Boolean {
        val now = clockNanos()
        val percent = progress.percent.toInt().coerceIn(0, 100)
        val enoughTime = lastNanos == Long.MIN_VALUE || now - lastNanos >= minimumIntervalNanos
        val enoughBytes = progress.downloadedBytes - lastBytes >= minimumByteDelta
        val meaningfulPercent = percent != lastPercent
        val terminal = percent >= 100
        if (!terminal && !(enoughTime && (enoughBytes || meaningfulPercent))) return false
        lastNanos = now
        lastBytes = progress.downloadedBytes
        lastPercent = percent
        return true
    }
}
