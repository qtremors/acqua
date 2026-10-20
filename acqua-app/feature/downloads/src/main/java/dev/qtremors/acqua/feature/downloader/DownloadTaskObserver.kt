package dev.qtremors.acqua.feature.downloader

import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.downloader.DownloadFailure
import dev.qtremors.acqua.downloader.DownloadQueueGateway
import dev.qtremors.acqua.downloader.DownloadQueueState
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import java.util.UUID

internal class DownloadTaskObserver(
    private val downloads: DownloadQueueGateway,
    private val state: MutableStateFlow<DownloaderUiState>,
    private val events: SendChannel<DownloaderEvent>,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
    private val onFinished: (List<UUID>) -> Unit
) {
    suspend fun observe(
        workIds: List<UUID>,
        itemIndex: Int?,
        downloadedKind: MediaKind?,
        enqueueFailed: Boolean
    ) {
        val final = downloads.observe(workIds)
            .onEach { jobs ->
                val unfinished = jobs.filter { it.isActive }
                if (unfinished.isNotEmpty()) {
                    val progress = jobs.map {
                        if (it.state == DownloadQueueState.SUCCEEDED || it.state == DownloadQueueState.SKIPPED) 100f else it.progress
                    }.average().toFloat()
                    val knownSizeJobs = unfinished.filter { it.totalBytes > 0L }
                    val sizeJobs = knownSizeJobs.ifEmpty { unfinished }
                    state.value = state.value.copy(
                        isSaving = itemIndex == null,
                        savingItemIndex = itemIndex,
                        savingIndex = jobs.count { it.state == DownloadQueueState.SUCCEEDED },
                        downloadProgress = progress,
                        downloadEtaSeconds = unfinished.maxOfOrNull { it.etaSeconds } ?: 0L,
                        downloadedBytes = sizeJobs.sumOf { it.downloadedBytes },
                        totalBytes = knownSizeJobs.sumOf { it.totalBytes }
                    )
                }
            }
            .first { jobs -> jobs.none { it.isActive } }
        onFinished(workIds)
        val skipped = final.count { it.state == DownloadQueueState.SKIPPED }
        if (skipped > 0) events.send(DownloaderEvent.DownloadsSkipped(skipped))
        val failure = final.firstOrNull { it.state == DownloadQueueState.FAILED }
        when {
            final.isNotEmpty() && skipped == final.size && !enqueueFailed -> state.value = state.value.copy(
                isSaving = false, savingItemIndex = null, downloadProgress = 0f, downloadEtaSeconds = 0,
                downloadedBytes = 0, totalBytes = 0)
            failure != null || enqueueFailed -> {
                val downloadFailure = DownloadFailure.fromCode(failure?.error)
                val diagnostic = failure?.id?.let { raw ->
                    kotlinx.coroutines.withContext(ioDispatcher) {
                        runCatching { downloads.diagnostic(UUID.fromString(raw)) }.getOrNull()
                    }
                }
                state.value = state.value.copy(
                    isSaving = false,
                    savingItemIndex = null,
                    downloadProgress = 0f,
                    downloadEtaSeconds = 0L,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    error = DownloaderProblem.Download(downloadFailure, diagnostic),
                    showBrowserAction = WebLink.normalize(state.value.url) != null
                )
                if (itemIndex != null) events.send(DownloaderEvent.ItemSaveFailed(downloadFailure))
            }
            final.any { it.state == DownloadQueueState.CANCELLED } -> state.value = state.value.copy(
                isSaving = false,
                savingItemIndex = null,
                downloadProgress = 0f,
                downloadEtaSeconds = 0L,
                downloadedBytes = 0L,
                totalBytes = 0L
            )
            itemIndex != null -> {
                state.value = state.value.copy(
                    savingItemIndex = null,
                    savedItemIndex = itemIndex,
                    lastDownloadedKind = downloadedKind,
                    downloadProgress = 100f,
                    downloadedBytes = 0L,
                    totalBytes = 0L
                )
                events.send(DownloaderEvent.ItemSaved(requireNotNull(downloadedKind)))
                delay(2000)
                state.value = state.value.copy(savedItemIndex = null, downloadProgress = 0f)
            }
            else -> {
                state.value = state.value.copy(
                    isSaving = false,
                    saved = true,
                    lastDownloadedKind = downloadedKind,
                    downloadProgress = 100f,
                    downloadEtaSeconds = 0L,
                    downloadedBytes = 0L,
                    totalBytes = 0L
                )
                events.send(DownloaderEvent.DownloadComplete(downloadedKind))
                delay(3000)
                state.value = state.value.copy(saved = false, downloadProgress = 0f)
            }
        }
    }
}
