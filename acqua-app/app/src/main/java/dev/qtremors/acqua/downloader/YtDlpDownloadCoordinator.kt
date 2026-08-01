package dev.qtremors.acqua.downloader

import android.content.Context
import androidx.work.Constraints
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.qtremors.acqua.domain.ResolvedMedia
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filterNotNull
import java.util.UUID
import java.util.concurrent.TimeUnit

class YtDlpDownloadCoordinator(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun enqueue(
        media: ResolvedMedia,
        outputMedia: ResolvedMedia,
        options: YtDlpDownloadOptions,
        sourceUrl: String
    ): UUID {
        val input = DownloadWorkData.processedRequest(media, outputMedia, options, sourceUrl)
        val request = OneTimeWorkRequestBuilder<YtDlpDownloadWorker>()
            .setInputData(input)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(
            DIRECT_WORK_QUEUE,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
        return request.id
    }

    fun enqueueDirect(media: ResolvedMedia, index: Int, sourceUrl: String): UUID {
        val input = DownloadWorkData.directRequest(media, index, sourceUrl)
        val request = OneTimeWorkRequestBuilder<YtDlpDownloadWorker>()
            .setInputData(input)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(
            "$UNIQUE_WORK_PREFIX-${request.id}",
            ExistingWorkPolicy.KEEP,
            request
        )
        return request.id
    }

    fun observe(id: UUID): Flow<WorkInfo> = workManager.getWorkInfoByIdFlow(id).filterNotNull()

    fun observe(ids: List<UUID>): Flow<List<WorkInfo>> =
        combine(ids.map(::observe)) { it.toList() }

    fun observeAll(): Flow<List<WorkInfo>> = workManager.getWorkInfosByTagFlow(WORK_TAG)

    fun observeQueue(): Flow<DownloadQueueSnapshot> = observeAll().map { jobs ->
        DownloadQueueSnapshot(
            jobs.sortedBy { it.id.toString() }.map { job ->
                DownloadQueueItem(
                    id = job.id.toString(),
                    state = when (job.state) {
                        WorkInfo.State.ENQUEUED -> if (job.runAttemptCount > 0) {
                            DownloadQueueState.RETRYING
                        } else {
                            DownloadQueueState.QUEUED
                        }
                        WorkInfo.State.RUNNING,
                        WorkInfo.State.BLOCKED -> DownloadQueueState.RUNNING
                        WorkInfo.State.SUCCEEDED -> DownloadQueueState.SUCCEEDED
                        WorkInfo.State.FAILED -> DownloadQueueState.FAILED
                        WorkInfo.State.CANCELLED -> DownloadQueueState.CANCELLED
                    },
                    progress = job.progress.getFloat(DownloadWorkData.KEY_PROGRESS, 0f),
                    etaSeconds = job.progress.getLong(DownloadWorkData.KEY_ETA_SECONDS, 0L),
                    downloadedBytes = job.progress.getLong(DownloadWorkData.KEY_DOWNLOADED_BYTES, 0L),
                    totalBytes = job.progress.getLong(DownloadWorkData.KEY_TOTAL_BYTES, 0L),
                    error = job.outputData.getString(DownloadWorkData.KEY_ERROR)
                )
            }
        )
    }

    fun cancel(id: UUID) {
        workManager.cancelWorkById(id)
    }

    private companion object {
        const val WORK_TAG = "acqua-yt-dlp-download"
        const val UNIQUE_WORK_PREFIX = "acqua-yt-dlp"
        const val DIRECT_WORK_QUEUE = "acqua-direct-downloads"
    }
}
