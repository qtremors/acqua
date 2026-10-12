package dev.qtremors.acqua.downloader

import dev.qtremors.acqua.domain.ResolvedMedia
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

interface DownloadQueueGateway {
    fun enqueue(media: ResolvedMedia, outputMedia: ResolvedMedia, options: YtDlpDownloadOptions, sourceUrl: String): UUID
    fun enqueueDirect(media: ResolvedMedia, index: Int, sourceUrl: String, itemCount: Int = 1): UUID
    fun observe(ids: List<UUID>): Flow<List<DownloadQueueItem>>
    fun observeQueue(): Flow<DownloadQueueSnapshot>
    fun cancel(id: UUID)
    fun rename(id: UUID, name: String): Boolean
    fun retry(id: UUID): UUID? = null
    fun diagnostic(id: UUID): DownloadDiagnostic? = null
    fun observePending(): Flow<List<PendingLink>> = flowOf(emptyList())
    fun savePending(text: String) {}
    fun updatePending(link: PendingLink) {}
    fun removePending(url: String) {}
    fun forgetTask(id: UUID) {}
    fun observeArchive(): Flow<List<DownloadArchiveEntry>> = flowOf(emptyList())
    fun removeArchive(key: String) {}
    fun observePresets(): Flow<List<DownloadPreset>> = flowOf(emptyList())
    fun savePreset(preset: DownloadPreset) {}
    fun removePreset(name: String) {}
    fun enqueueDirectConfigured(media: ResolvedMedia, index: Int, sourceUrl: String, itemCount: Int,
        options: YtDlpDownloadOptions): UUID = enqueueDirect(media, index, sourceUrl, itemCount)
}
