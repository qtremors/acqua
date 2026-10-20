package dev.qtremors.acqua.feature.downloader

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.acqua.data.settings.LastDownloadSelection
import dev.qtremors.acqua.data.settings.SettingsStore
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.downloader.DownloadOptionsCodec
import kotlinx.coroutines.flow.MutableStateFlow

internal class DownloadSelectionMemory(
    private val state: MutableStateFlow<DownloaderUiState>,
    private val saved: SavedStateHandle,
    private val settings: SettingsStore
) {
    var kind: MediaKind = saved.get<String>(DownloaderSavedState.SELECTION_KIND)
        ?.let { runCatching { MediaKind.valueOf(it) }.getOrNull() } ?: state.value.selectionKind()
        private set

    fun remember() {
        val snapshot = state.value
        val options = snapshot.advancedOptions.copy(
            contentType = if (kind == MediaKind.AUDIO) DownloadContentType.AUDIO else DownloadContentType.VIDEO,
            maximumVideoHeight = snapshot.maximumVideoHeight, audioFormat = snapshot.audioFormat,
            embedMetadata = snapshot.embedMetadata, embedThumbnail = snapshot.embedThumbnail,
            cropArtwork = snapshot.cropArtwork, preserveOriginalMetadata = snapshot.preserveOriginalMetadata,
            captionMode = snapshot.advancedOptions.captionMode
        )
        settings.rememberDownloadSelection(kind, LastDownloadSelection(snapshot.downloadEngine, options))
        saveInputs()
    }

    fun adopt(kind: MediaKind) {
        this.kind = kind
        state.value = state.value.copy(selectedMediaKind = kind)
        saveInputs()
    }

    fun restore(kind: MediaKind) {
        if (kind == this.kind) return
        val snapshot = state.value
        val type = when (kind) {
            MediaKind.AUDIO -> DownloadContentType.AUDIO
            MediaKind.VIDEO -> DownloadContentType.VIDEO
            else -> snapshot.downloadContentType
        }
        val remembered = downloaderDefaultState(settings, snapshot.url, type, kind)
        state.value = snapshot.withDownloadChoices(remembered.copy(advancedOptions = remembered.advancedOptions.copy(
            cookieProfileId = snapshot.advancedOptions.cookieProfileId, cookieProfileFingerprint = snapshot.advancedOptions.cookieProfileFingerprint)))
            .copy(downloadContentType = type)
        adopt(kind)
    }

    private fun saveInputs() {
        val snapshot = state.value
        saved[DownloaderSavedState.SELECTION_KIND] = kind.name
        saved[DownloaderSavedState.CONTENT_TYPE] = snapshot.downloadContentType.name
        saved[DownloaderSavedState.ENGINE] = snapshot.downloadEngine.name
        saved[DownloaderSavedState.MAXIMUM_HEIGHT] = snapshot.maximumVideoHeight
        saved[DownloaderSavedState.AUDIO_FORMAT] = snapshot.audioFormat.name
        saved[DownloaderSavedState.EMBED_METADATA] = snapshot.embedMetadata
        saved[DownloaderSavedState.EMBED_THUMBNAIL] = snapshot.embedThumbnail
        saved[DownloaderSavedState.CROP_ARTWORK] = snapshot.cropArtwork
        saved[DownloaderSavedState.ORIGINAL_METADATA] = snapshot.preserveOriginalMetadata
        saved["downloader.advanced"] = DownloadOptionsCodec.encode(snapshot.advancedOptions)
    }
}

internal fun DownloaderUiState.selectionKind(): MediaKind = when {
    selectedMediaKind != null -> selectedMediaKind
    !media.isNullOrEmpty() && media.all { it.kind == MediaKind.IMAGE } -> MediaKind.IMAGE
    downloadContentType == DownloadContentType.AUDIO -> MediaKind.AUDIO
    else -> MediaKind.VIDEO
}

internal fun DownloaderUiState.withDownloadChoices(choices: DownloaderUiState): DownloaderUiState = copy(
    maximumVideoHeight = choices.maximumVideoHeight, audioFormat = choices.audioFormat,
    embedMetadata = choices.embedMetadata, embedThumbnail = choices.embedThumbnail,
    cropArtwork = choices.cropArtwork, preserveOriginalMetadata = choices.preserveOriginalMetadata,
    advancedOptions = choices.advancedOptions.copy(recordHistory = advancedOptions.recordHistory,
        verboseDiagnostics = advancedOptions.verboseDiagnostics, network = advancedOptions.network, removeSegments = advancedOptions.removeSegments,
        formatPolicy = advancedOptions.formatPolicy)
)
