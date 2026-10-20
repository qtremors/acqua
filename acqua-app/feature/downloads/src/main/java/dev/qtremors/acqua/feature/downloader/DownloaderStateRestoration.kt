package dev.qtremors.acqua.feature.downloader

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.acqua.data.settings.SettingsStore
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.MediaResolutionException
import dev.qtremors.acqua.domain.MediaResolutionFailure
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.resolver.instagram.AgeGateException
import dev.qtremors.acqua.resolver.instagram.ExpiredSessionException
import java.io.IOException

internal object DownloaderSavedState {
    const val URL = "downloader.url"
    const val CONTENT_TYPE = "downloader.content_type"
    const val SELECTION_KIND = "downloader.selection_kind"
    const val ENGINE = "downloader.engine"
    const val MAXIMUM_HEIGHT = "downloader.maximum_height"
    const val AUDIO_FORMAT = "downloader.audio_format"
    const val EMBED_METADATA = "downloader.embed_metadata"
    const val CROP_ARTWORK = "downloader.crop_artwork"
    const val ORIGINAL_METADATA = "downloader.original_metadata"
    const val EMBED_THUMBNAIL = "downloader.embed_thumbnail"
    const val COLLECTION_SELECTION = "downloader.collection_selection"
    const val PLAYLIST = "downloader.playlist"
}

internal fun Throwable.toMediaResolutionFailure(): MediaResolutionFailure = when (this) {
    is MediaResolutionException -> failure
    is ExpiredSessionException -> MediaResolutionFailure.SESSION_EXPIRED
    is AgeGateException -> MediaResolutionFailure.SESSION_REQUIRED
    is IOException -> MediaResolutionFailure.NETWORK
    is IllegalArgumentException -> MediaResolutionFailure.INVALID_LINK
    else -> MediaResolutionFailure.UNKNOWN
}

internal fun downloaderDefaultState(
    settings: SettingsStore,
    url: String = "",
    contentType: DownloadContentType? = null,
    selectionKind: MediaKind? = null
): DownloaderUiState {
    val preferences = settings.mediaProcessingSettings()
    val downloadSettings = settings.downloadSettings()
    val type = contentType ?: settings.lastDownloadContentType() ?: DownloadContentType.VIDEO
    val kind = selectionKind ?: if (type == DownloadContentType.AUDIO) MediaKind.AUDIO else MediaKind.VIDEO
    val selection = settings.lastDownloadSelection(kind)
    val options = selection?.options
    return DownloaderUiState(
        url = url,
        validity = when {
            url.isBlank() -> LinkValidity.EMPTY
            WebLink.normalize(url) != null -> LinkValidity.VALID
            else -> LinkValidity.INVALID
        },
        downloadContentType = type,
        selectedMediaKind = kind,
        downloadEngine = WebLink.preferredEngine(url, selection?.engine ?: settings.lastDownloadEngine()),
        maximumVideoHeight = options?.maximumVideoHeight ?: preferences.maximumVideoHeight,
        audioFormat = options?.audioFormat ?: preferences.audioFormat,
        embedMetadata = options?.embedMetadata ?: preferences.embedMetadata,
        embedThumbnail = options?.embedThumbnail ?: preferences.embedThumbnail,
        cropArtwork = options?.cropArtwork ?: preferences.cropArtwork,
        preserveOriginalMetadata = options?.preserveOriginalMetadata ?: preferences.preserveOriginalMetadata,
        filenamePattern = downloadSettings.filenamePattern,
        audioFilenamePattern = downloadSettings.audioFilenamePattern,
        advancedOptions = dev.qtremors.acqua.downloader.DownloadCaptions.normalized((options ?: settings.downloadDefaults()).copy(
            contentType = type, audioFormat = options?.audioFormat ?: preferences.audioFormat, recordHistory = settings.downloadDefaults().recordHistory,
            verboseDiagnostics = settings.downloadDefaults().verboseDiagnostics, network = settings.downloadDefaults().network,
            removeSegments = settings.downloadDefaults().removeSegments, formatPolicy = settings.downloadDefaults().formatPolicy))
    )
}

internal fun restoredDownloaderState(
    settings: SettingsStore,
    savedStateHandle: SavedStateHandle
): DownloaderUiState {
    val url = savedStateHandle[DownloaderSavedState.URL] ?: ""
    val contentType = savedStateHandle.get<String>(DownloaderSavedState.CONTENT_TYPE)
        ?.let { runCatching { DownloadContentType.valueOf(it) }.getOrNull() }
    val kind = savedStateHandle.get<String>(DownloaderSavedState.SELECTION_KIND)
        ?.let { runCatching { MediaKind.valueOf(it) }.getOrNull() }
    val initial = downloaderDefaultState(settings, url, contentType, kind)
    return initial.copy(
        downloadEngine = savedStateHandle.get<String>(DownloaderSavedState.ENGINE)
            ?.let { runCatching { DownloadEngine.valueOf(it) }.getOrNull() }
            ?: initial.downloadEngine,
        maximumVideoHeight = savedStateHandle[DownloaderSavedState.MAXIMUM_HEIGHT] ?: initial.maximumVideoHeight,
        audioFormat = savedStateHandle.get<String>(DownloaderSavedState.AUDIO_FORMAT)
            ?.let { runCatching { AudioOutputFormat.valueOf(it) }.getOrNull() }
            ?: initial.audioFormat,
        embedMetadata = savedStateHandle[DownloaderSavedState.EMBED_METADATA] ?: initial.embedMetadata,
        embedThumbnail = savedStateHandle[DownloaderSavedState.EMBED_THUMBNAIL] ?: initial.embedThumbnail,
        cropArtwork = savedStateHandle[DownloaderSavedState.CROP_ARTWORK] ?: initial.cropArtwork,
        preserveOriginalMetadata = savedStateHandle[DownloaderSavedState.ORIGINAL_METADATA] ?: initial.preserveOriginalMetadata,
        acceptedMediaTasks = savedStateHandle.get<HashMap<String, String>>("downloader.accepted").orEmpty(),
        hasRejectedItems = savedStateHandle["downloader.rejected"] ?: false,
        selectedCollectionItems = savedStateHandle.get<ArrayList<Int>>(DownloaderSavedState.COLLECTION_SELECTION)?.toSet(),
        downloadPlaylist = savedStateHandle[DownloaderSavedState.PLAYLIST] ?: true,
        advancedOptions = runCatching { dev.qtremors.acqua.downloader.DownloadOptionsCodec.decode(
            savedStateHandle.get<String>("downloader.advanced"), initial.advancedOptions) }.getOrDefault(initial.advancedOptions)
    )
}
