package dev.qtremors.acqua.feature.downloader

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.acqua.data.settings.SettingsStore
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.CollectionSelection
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.downloader.CaptionMode
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.downloader.DownloadQueueGateway
import dev.qtremors.acqua.downloader.YtDlpFormatSelector
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

internal class DownloadSubmissionController(
    private val state: MutableStateFlow<DownloaderUiState>,
    private val savedState: SavedStateHandle,
    private val downloads: DownloadQueueGateway,
    private val settings: SettingsStore? = null
) {
    fun selectCollectionItem(index: Int) {
        val snapshot = state.value
        if (index !in snapshot.media.orEmpty().indices) return
        if (snapshot.media?.get(index)?.unavailableReason != null) return
        val selected = snapshot.selectedCollectionItems ?: snapshot.media.orEmpty().indices.filter {
            snapshot.media?.get(it)?.unavailableReason == null }.toSet()
        selectCollection(if (index in selected) selected - index else selected + index)
    }

    fun selectCollectionRange(value: String): Boolean {
        val selected = CollectionSelection.parse(value, state.value.media.orEmpty().size) ?: return false
        selectCollection(selected)
        return true
    }

    fun selectAllCollectionItems(selected: Boolean) = selectCollection(if (selected) null else emptySet())

    fun selectPreviewItem(index: Int, beginSelection: Boolean) {
        val snapshot = state.value
        if (index !in snapshot.availableCollectionIndices()) return
        val selected = snapshot.selectedCollectionItems.orEmpty()
        selectPreviewItems(if (beginSelection || index !in selected) selected + index else selected - index)
    }

    fun selectPreviewItems(selected: Set<Int>?) {
        val snapshot = state.value
        if (snapshot.isResolving || snapshot.isSaving || snapshot.savingItemIndex != null || snapshot.isBatchRunning) return
        selectCollection(selected?.intersect(snapshot.availableCollectionIndices())?.takeIf { it.isNotEmpty() })
        state.value = state.value.copy(saved = false)
    }

    private fun selectCollection(selected: Set<Int>?) {
        savedState[DownloaderSavedState.COLLECTION_SELECTION] = selected?.let { ArrayList(it) }
        val current = state.value
        val options = if (current.selectedCollectionItems == selected) current.advancedOptions else
            current.advancedOptions.copy(clipStartSeconds = null, clipEndSeconds = null, clipRanges = emptyList(), captionTiming = dev.qtremors.acqua.downloader.CaptionTiming(), splitChapters = false,
                videoFormatId = null, audioFormatId = null)
        savedState["downloader.advanced"] = dev.qtremors.acqua.downloader.DownloadOptionsCodec.encode(options)
        state.value = current.copy(selectedCollectionItems = selected, advancedOptions = options)
    }

    fun enqueue(media: ResolvedMedia, sourceUrl: String, snapshot: DownloaderUiState = state.value): UUID {
        val selected = rememberedSnapshot(media, snapshot)
        val selectedIds = listOfNotNull(snapshot.advancedOptions.videoFormatId, snapshot.advancedOptions.audioFormatId) + selected.advancedOptions.additionalAudioFormatIds
        if (selectedIds.any { id -> media.formats.none { it.id == id } }) throw dev.qtremors.acqua.downloader.SourceStreamsChangedException()
        val options = options(selected).copy(
            videoFormatId = snapshot.advancedOptions.videoFormatId.takeIf { media.formats.any { format -> format.id == it } },
            audioFormatId = snapshot.advancedOptions.audioFormatId.takeIf { media.formats.any { format -> format.id == it } }
        )
        val outputMedia = if (options.contentType == DownloadContentType.AUDIO) {
            media.copy(width = 0, height = 0)
        } else {
            (media.formats.firstOrNull { it.id == options.videoFormatId }
                ?: YtDlpFormatSelector.selectedVideoFormat(media.formats, options.maximumVideoHeight))
                ?.let { selected -> media.copy(width = selected.width, height = selected.height) }
                ?: media
        }
        return downloads.enqueue(media, outputMedia, options, sourceUrl)
    }

    fun enqueueDirect(media: ResolvedMedia, index: Int, sourceUrl: String, count: Int,
        snapshot: DownloaderUiState = state.value): UUID =
        downloads.enqueueDirectConfigured(media, index, sourceUrl, count, options(rememberedSnapshot(media, snapshot)))

    private fun rememberedSnapshot(media: ResolvedMedia, snapshot: DownloaderUiState): DownloaderUiState {
        val kind = when {
            media.kind == MediaKind.IMAGE -> MediaKind.IMAGE
            media.isAudio || snapshot.downloadContentType == DownloadContentType.AUDIO -> MediaKind.AUDIO
            else -> MediaKind.VIDEO
        }
        if (kind == snapshot.selectionKind()) return snapshot
        val store = settings ?: return snapshot
        val type = if (kind == MediaKind.AUDIO) DownloadContentType.AUDIO else DownloadContentType.VIDEO
        return snapshot.withDownloadChoices(downloaderDefaultState(store, snapshot.url, type, kind))
            .copy(downloadContentType = type)
    }

    private fun options(snapshot: DownloaderUiState) = snapshot.advancedOptions.copy(
        contentType = snapshot.downloadContentType, maximumVideoHeight = snapshot.maximumVideoHeight,
        audioFormat = snapshot.audioFormat, embedMetadata = snapshot.embedMetadata,
        embedThumbnail = snapshot.embedThumbnail, cropArtwork = snapshot.cropArtwork,
        preserveOriginalMetadata = snapshot.preserveOriginalMetadata)

    fun downloadedKind(media: ResolvedMedia): MediaKind {
        if (media.backend != MediaBackend.YT_DLP) return media.kind
        return if (state.value.downloadContentType == DownloadContentType.AUDIO) MediaKind.AUDIO else MediaKind.VIDEO
    }
}
