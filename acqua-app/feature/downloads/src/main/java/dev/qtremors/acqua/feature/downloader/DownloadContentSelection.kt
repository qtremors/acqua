package dev.qtremors.acqua.feature.downloader

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.MediaResolutionGateway
import dev.qtremors.acqua.domain.PlaylistLink
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.WebLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal fun DownloaderUiState.resolutionUrl(): String? =
    if (!downloadPlaylist && PlaylistLink.isPlaylist(url)) PlaylistLink.videoUrl(url) else WebLink.normalize(url)

internal fun DownloaderUiState.availableCollectionIndices(): Set<Int> = media.orEmpty().indices.filter {
    media?.get(it)?.unavailableReason == null
}.toSet()

internal fun DownloaderUiState.selectedIndices(): Set<Int> =
    selectedCollectionItems?.intersect(availableCollectionIndices()) ?: availableCollectionIndices()

internal fun DownloaderUiState.selectedSource(): ResolvedMedia? = selectedIndices().singleOrNull()?.let { media?.get(it) }

internal fun DownloaderUiState.hasSourceDetails(index: Int): Boolean =
    media?.getOrNull(index)?.let { it.collectionUrl == null || index in inspectedCollectionItems } == true

internal class DownloadContentSelection(
    private val state: MutableStateFlow<DownloaderUiState>,
    private val saved: SavedStateHandle,
    private val resolution: MediaResolutionGateway,
    private val scope: CoroutineScope
) {
    private var job: Job? = null

    fun cancel() { job?.cancel() }

    fun selectPlaylist(playlist: Boolean, indices: Set<Int>?) {
        val current = state.value
        val changedScope = playlist != current.downloadPlaylist
        val changedEntries = indices != current.selectedCollectionItems
        if (!changedScope && !changedEntries) return
        cancel()
        saved[DownloaderSavedState.PLAYLIST] = playlist
        saved[DownloaderSavedState.COLLECTION_SELECTION] = indices?.let { ArrayList(it) }
        state.value = current.copy(downloadPlaylist = playlist, selectedCollectionItems = indices,
            media = if (changedScope) null else current.media, inspectingCollectionIndex = null,
            inspectedCollectionItems = if (changedScope) emptySet() else current.inspectedCollectionItems,
            advancedOptions = current.advancedOptions.copy(clipStartSeconds = null, clipEndSeconds = null, clipRanges = emptyList(), captionTiming = dev.qtremors.acqua.downloader.CaptionTiming(),
                splitChapters = false, videoFormatId = null, audioFormatId = null))
    }

    fun inspect(index: Int, browserSessions: Boolean, browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>) {
        val snapshot = state.value
        val item = snapshot.media?.getOrNull(index) ?: return
        if (item.unavailableReason != null || snapshot.hasSourceDetails(index) || snapshot.isResolving) return
        cancel()
        job = scope.launch {
            state.value = state.value.copy(inspectingCollectionIndex = index, collectionInspectionFailed = false)
            runCatching {
                resolution.resolveConfigured(PlaylistLink.videoUrl(item.url) ?: item.url, browserSessions,
                    engine = DownloadEngine.YT_DLP, sessionProfileId = snapshot.advancedOptions.cookieProfileId, browserResolver = browserResolver).single()
            }.onSuccess { inspected ->
                if (state.value.url != snapshot.url || state.value.media?.getOrNull(index)?.url != item.url) return@onSuccess
                val entries = state.value.media.orEmpty().toMutableList()
                entries[index] = inspected.copy(collectionUrl = item.collectionUrl, collectionTitle = item.collectionTitle,
                    collectionIndex = item.collectionIndex, collectionSize = item.collectionSize,
                    thumbnailUrl = inspected.thumbnailUrl ?: item.thumbnailUrl)
                state.value = state.value.copy(media = entries, inspectingCollectionIndex = null,
                    inspectedCollectionItems = state.value.inspectedCollectionItems + index)
            }.onFailure { failure ->
                if (failure is CancellationException) throw failure
                state.value = state.value.copy(inspectingCollectionIndex = null, collectionInspectionFailed = true)
            }
        }
    }
}
