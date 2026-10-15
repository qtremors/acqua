package dev.qtremors.acqua.feature.downloader

import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaResolutionGateway
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.downloader.DownloadFailure
import dev.qtremors.acqua.downloader.DownloadQueueGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.UUID

internal class PendingDownloadController(
    private val state: MutableStateFlow<DownloaderUiState>,
    private val downloads: DownloadQueueGateway,
    private val resolution: MediaResolutionGateway,
    private val scope: CoroutineScope,
    private val enqueueProcessed: (ResolvedMedia, String, DownloaderUiState) -> UUID,
    private val enqueueDirect: (ResolvedMedia, Int, String, Int, DownloaderUiState) -> UUID,
    private val recordLink: suspend (String, ResolvedMedia?, Boolean) -> Unit = { _, _, _ -> }
) {
    private var job: Job? = null

    fun cancel() { job?.cancel() }

    fun save() {
        try {
            downloads.savePending(state.value.url)
        } catch (_: Exception) {
            state.value = state.value.copy(error = DownloaderProblem.Download(DownloadFailure.STORAGE))
        }
    }

    fun download(
        browserSessionsEnabled: Boolean,
        browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>
    ) {
        if (state.value.isBatchRunning) return
        val snapshot = state.value
        val inputLinks = WebLink.extractAll(snapshot.url).toSet()
        val links = snapshot.pendingLinks.filter { it.url in inputLinks }
        state.value = state.value.copy(isBatchRunning = true)
        job = scope.launch {
            try {
                links.forEach { link ->
                    currentCoroutineContext().ensureActive()
                    val accepted = link.acceptedItems.toMutableMap()
                    var totalEntries = link.totalEntries
                    try {
                        downloads.updatePending(link.copy(status = "Inspecting"))
                        recordLink(link.url, null, snapshot.advancedOptions.recordHistory)
                        val items = resolution.resolveConfigured(
                            link.url, browserSessionsEnabled, engine = snapshot.downloadEngine,
                            sessionProfileId = snapshot.advancedOptions.cookieProfileId,
                            browserResolver = browserResolver
                        )
                        require(items.isNotEmpty()) { "No media was found." }
                        recordLink(link.url, items.firstOrNull(), snapshot.advancedOptions.recordHistory)
                        totalEntries = items.size
                        var rejected = false
                        items.forEachIndexed { index, item ->
                            currentCoroutineContext().ensureActive()
                            if (item.unavailableReason != null) { rejected = true; return@forEachIndexed }
                            try {
                                val key = "${item.collectionUrl ?: WebLink.mediaAssetIdentity(item.url)}#${item.collectionIndex}"
                                val previous = accepted[key]
                                if (previous == null) {
                                    val id = if (item.backend == MediaBackend.YT_DLP) {
                                        enqueueProcessed(item, link.url, snapshot)
                                    } else enqueueDirect(item, index, link.url, items.size, snapshot)
                                    accepted[key] = id.toString()
                                } else if (state.value.downloadQueue.items.any { it.id == previous && it.canRetry }) {
                                    downloads.retry(UUID.fromString(previous))?.let { accepted[key] = it.toString() }
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                rejected = true
                            }
                            downloads.updatePending(link.copy(
                                status = if (!rejected) "Queued" else if (accepted.isEmpty()) "Failed to queue" else "Partially queued",
                                acceptedItems = accepted.toMap(), totalEntries = totalEntries
                            ))
                        }
                        downloads.updatePending(link.copy(
                            status = if (!rejected) "Queued" else if (accepted.isEmpty()) "Failed to queue" else "Partially queued",
                            acceptedItems = accepted.toMap(), totalEntries = totalEntries))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        downloads.updatePending(link.copy(
                            status = if (accepted.isEmpty()) "Failed to queue" else "Partially queued",
                            acceptedItems = accepted.toMap(), totalEntries = totalEntries
                        ))
                    }
                }
            } finally {
                state.value = state.value.copy(isBatchRunning = false)
            }
        }
    }
}
