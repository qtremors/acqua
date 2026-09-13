package dev.qtremors.acqua.downloader

import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.UUID

internal class DownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(UserInitiatedDownloadScheduler.KEY_TASK_ID)
            ?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }
            ?: return
        context.getSystemService(JobScheduler::class.java)
            .cancel(UserInitiatedDownloadScheduler.jobId(id))
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
