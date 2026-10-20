package dev.qtremors.acqua.downloader

import android.app.job.JobParameters

internal object DownloadStopPolicy {
    @androidx.annotation.RequiresApi(31)
    fun shouldRetry(reason: Int): Boolean = reason in setOf(
        JobParameters.STOP_REASON_QUOTA, JobParameters.STOP_REASON_TIMEOUT,
        JobParameters.STOP_REASON_CONSTRAINT_CONNECTIVITY,
        JobParameters.STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW,
        JobParameters.STOP_REASON_CONSTRAINT_CHARGING,
        JobParameters.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW,
        JobParameters.STOP_REASON_CONSTRAINT_DEVICE_IDLE,
        JobParameters.STOP_REASON_PREEMPT,
        JobParameters.STOP_REASON_DEVICE_STATE, JobParameters.STOP_REASON_SYSTEM_PROCESSING,
        JobParameters.STOP_REASON_ESTIMATED_APP_LAUNCH_TIME_CHANGED
    )
}
