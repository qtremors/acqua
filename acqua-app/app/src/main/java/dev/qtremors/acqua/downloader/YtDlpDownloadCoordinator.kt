package dev.qtremors.acqua.downloader

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.qtremors.acqua.domain.ResolvedMedia
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import java.util.UUID

class YtDlpDownloadCoordinator(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun enqueue(
        media: ResolvedMedia,
        outputMedia: ResolvedMedia,
        options: YtDlpDownloadOptions,
        sourceUrl: String
    ): UUID {
        val input = Data.Builder()
            .putString(YtDlpDownloadWorker.KEY_MEDIA_URL, media.url)
            .putString(YtDlpDownloadWorker.KEY_SOURCE_URL, sourceUrl)
            .putString(YtDlpDownloadWorker.KEY_CONTENT_TYPE, options.contentType.name)
            .putString(YtDlpDownloadWorker.KEY_AUDIO_FORMAT, options.audioFormat.name)
            .putInt(YtDlpDownloadWorker.KEY_MAXIMUM_VIDEO_QUALITY, options.maximumVideoHeight)
            .putBoolean(YtDlpDownloadWorker.KEY_EMBED_METADATA, options.embedMetadata)
            .putBoolean(YtDlpDownloadWorker.KEY_EMBED_THUMBNAIL, options.embedThumbnail)
            .putBoolean(
                YtDlpDownloadWorker.KEY_BROWSER_SESSION_AUTHORIZED,
                media.explicitBrowserSessionAuthorized
            )
            .putInt(YtDlpDownloadWorker.KEY_MEDIA_WIDTH, media.width)
            .putInt(YtDlpDownloadWorker.KEY_MEDIA_HEIGHT, media.height)
            .putInt(YtDlpDownloadWorker.KEY_OUTPUT_WIDTH, outputMedia.width)
            .putInt(YtDlpDownloadWorker.KEY_OUTPUT_HEIGHT, outputMedia.height)
            .apply {
                media.thumbnailUrl?.let { putString(YtDlpDownloadWorker.KEY_THUMBNAIL_URL, it) }
                media.username?.let { putString(YtDlpDownloadWorker.KEY_USERNAME, it) }
                media.title?.let { putString(YtDlpDownloadWorker.KEY_TITLE, it) }
            }
            .build()
        val request = OneTimeWorkRequestBuilder<YtDlpDownloadWorker>()
            .setInputData(input)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
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

    fun cancel(id: UUID) {
        workManager.cancelWorkById(id)
    }

    private companion object {
        const val WORK_TAG = "acqua-yt-dlp-download"
        const val UNIQUE_WORK_PREFIX = "acqua-yt-dlp"
    }
}
