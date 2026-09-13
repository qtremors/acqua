package dev.qtremors.acqua.downloader

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.annotation.RequiresApi
import dev.qtremors.acqua.domain.ResolvedMedia
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filterNotNull
import java.util.UUID
import java.util.concurrent.TimeUnit

class DownloadWorkCoordinator(context: Context) {
    private val applicationContext = context.applicationContext
    private val workManager = WorkManager.getInstance(context.applicationContext)
    private val userInitiatedScheduler = if (Build.VERSION.SDK_INT >= 34) {
        UserInitiatedDownloadScheduler(applicationContext)
    } else {
        null
    }

    fun enqueue(
        media: ResolvedMedia,
        outputMedia: ResolvedMedia,
        options: YtDlpDownloadOptions,
        sourceUrl: String
    ): UUID {
        val input = DownloadWorkData.processedRequest(media, outputMedia, options, sourceUrl)
        return enqueue(input)
    }

    private fun enqueue(input: androidx.work.Data): UUID {
        if (Build.VERSION.SDK_INT >= 34) {
            scheduleUserInitiated(input)?.let { return it }
        }
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(input)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(
            DOWNLOAD_WORK_QUEUE,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
        return request.id
    }

    fun enqueueDirect(media: ResolvedMedia, index: Int, sourceUrl: String, itemCount: Int = 1): UUID {
        val input = DownloadWorkData.directRequest(media, index, sourceUrl, itemCount)
        return enqueue(input)
    }

    fun observe(id: UUID): Flow<DownloadQueueItem> {
        return if (userInitiatedScheduler != null &&
            UserInitiatedDownloadRegistry.contains(applicationContext, id)
        ) {
            UserInitiatedDownloadRegistry.observe(applicationContext)
                .map { it[id] }
                .filterNotNull()
        } else {
            workManager.getWorkInfoByIdFlow(id).filterNotNull().map(::toQueueItem)
        }
    }

    fun observe(ids: List<UUID>): Flow<List<DownloadQueueItem>> =
        combine(ids.map(::observe)) { it.toList() }

    fun observeAll(): Flow<List<DownloadQueueItem>> {
        val workItems = workManager.getWorkInfosByTagFlow(WORK_TAG).map { jobs ->
            jobs.map(::toQueueItem)
        }
        val userItems = if (userInitiatedScheduler != null) {
            UserInitiatedDownloadRegistry.observe(applicationContext).map { it.values.toList() }
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
        return combine(workItems, userItems) { work, user -> work + user }
    }

    fun observeQueue(): Flow<DownloadQueueSnapshot> = observeAll().map { jobs ->
        DownloadQueueSnapshot(
            jobs.sortedBy(DownloadQueueItem::id)
        )
    }

    fun cancel(id: UUID) {
        if (Build.VERSION.SDK_INT >= 34 && userInitiatedScheduler != null &&
            UserInitiatedDownloadRegistry.contains(applicationContext, id)
        ) {
            cancelUserInitiated(id)
        } else {
            workManager.cancelWorkById(id)
        }
    }

    @RequiresApi(34)
    private fun scheduleUserInitiated(input: androidx.work.Data): UUID? =
        userInitiatedScheduler?.let { scheduler ->
            runCatching { scheduler.schedule(input) }.getOrNull()
        }

    @RequiresApi(34)
    private fun cancelUserInitiated(id: UUID) {
        userInitiatedScheduler?.cancel(id)
    }

    private fun toQueueItem(job: WorkInfo) = DownloadQueueItem(
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
        error = job.outputData.getString(DownloadWorkData.KEY_ERROR),
        phase = when (job.state) {
            WorkInfo.State.ENQUEUED -> if (job.runAttemptCount > 0) {
                DownloadWorkData.phase(job.progress)
            } else {
                DownloadPhase.QUEUED
            }
            WorkInfo.State.RUNNING,
            WorkInfo.State.BLOCKED -> DownloadWorkData.phase(job.progress)
            else -> DownloadPhase.COMPLETE
        }
    )

    private companion object {
        const val WORK_TAG = "acqua-yt-dlp-download"
        const val DOWNLOAD_WORK_QUEUE = "acqua-downloads"
    }
}
