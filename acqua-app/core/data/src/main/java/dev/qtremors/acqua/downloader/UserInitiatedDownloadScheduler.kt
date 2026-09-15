package dev.qtremors.acqua.downloader

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.annotation.RequiresApi
import androidx.work.Data
import java.util.UUID

internal class UserInitiatedDownloadScheduler(context: Context) {
    private val applicationContext = context.applicationContext
    private val scheduler = applicationContext.getSystemService(JobScheduler::class.java)

    @RequiresApi(34)
    fun schedule(input: Data): UUID? {
        val id = UUID.randomUUID()
        val extras = DownloadWorkData.toPersistableBundle(input).apply {
            putString(KEY_TASK_ID, id.toString())
        }
        val network = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val info = JobInfo.Builder(
            jobId(id),
            ComponentName(applicationContext, UserInitiatedDownloadJobService::class.java)
        )
            .setUserInitiated(true)
            .setRequiredNetwork(network)
            .setBackoffCriteria(10_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .setExtras(extras)
            .build()
        UserInitiatedDownloadRegistry.update(
            applicationContext,
            DownloadQueueItem(id.toString(), DownloadQueueState.QUEUED)
        )
        return if (scheduler.schedule(info) == JobScheduler.RESULT_SUCCESS) {
            id
        } else {
            UserInitiatedDownloadRegistry.remove(applicationContext, id)
            null
        }
    }

    @RequiresApi(34)
    fun cancel(id: UUID) {
        scheduler.cancel(jobId(id))
        UserInitiatedDownloadRegistry.update(
            applicationContext,
            DownloadQueueItem(
                id = id.toString(),
                state = DownloadQueueState.CANCELLED,
                phase = DownloadPhase.COMPLETE
            )
        )
    }

    companion object {
        const val KEY_TASK_ID = "acqua_task_id"

        fun jobId(id: UUID): Int = (id.hashCode() and JOB_ID_MASK) or JOB_ID_PREFIX

        private const val JOB_ID_MASK = 0x0fffffff
        private const val JOB_ID_PREFIX = 0x50000000
    }
}
