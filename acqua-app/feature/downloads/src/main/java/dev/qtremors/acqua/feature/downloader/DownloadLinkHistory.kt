package dev.qtremors.acqua.feature.downloader

import dev.qtremors.acqua.data.history.HistoryEntry
import dev.qtremors.acqua.data.history.HistoryWriter
import dev.qtremors.acqua.domain.ResolvedMedia
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class DownloadLinkHistory(private val history: HistoryWriter, private val ioDispatcher: CoroutineDispatcher) {
    suspend fun record(url: String, media: ResolvedMedia? = null,
        recordHistory: Boolean): Unit = withContext(ioDispatcher) {
        if (!recordHistory) return@withContext
        history.add(
            HistoryEntry(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                url = url,
                fileName = "",
                fileUri = "",
                isVideo = false,
                mimeType = "",
                sizeBytes = 0,
                isDownloaded = false,
                title = media?.title, author = media?.artist ?: media?.username, sourceIdentity = media?.sourceIdentity
            )
        )
    }

}
