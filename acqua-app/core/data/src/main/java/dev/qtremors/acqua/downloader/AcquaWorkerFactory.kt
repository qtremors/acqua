package dev.qtremors.acqua.downloader

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters

class AcquaWorkerFactory(
    private val downloadExecutor: DownloadExecution
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? = when (workerClassName) {
        DownloadWorker::class.java.name ->
            DownloadWorker(appContext, workerParameters, downloadExecutor)
        else -> null
    }
}
