package dev.qtremors.acqua.downloader

import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import java.util.UUID

internal class DownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(UserInitiatedDownloadScheduler.KEY_TASK_ID)
            ?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }
            ?: return
        if (UserInitiatedDownloadRegistry.contains(context, id)) {
            context.getSystemService(JobScheduler::class.java)
                .cancel(UserInitiatedDownloadScheduler.jobId(id))
        } else {
            runCatching { WorkManager.getInstance(context).cancelWorkById(id) }
        }
        UserInitiatedDownloadRegistry.update(
            context,
            DownloadQueueItem(
                id = id.toString(),
                state = DownloadQueueState.CANCELLED,
                phase = DownloadPhase.COMPLETE
            )
        )
    }
}
