package dev.qtremors.acqua.feature.downloader

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.acqua.data.settings.SettingsStore
import dev.qtremors.acqua.domain.DownloadEngine
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
    const val ENGINE = "downloader.engine"
    const val MAXIMUM_HEIGHT = "downloader.maximum_height"
    const val AUDIO_FORMAT = "downloader.audio_format"
    const val EMBED_METADATA = "downloader.embed_metadata"
    const val EMBED_THUMBNAIL = "downloader.embed_thumbnail"
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
    contentType: DownloadContentType? = null
): DownloaderUiState {
    val preferences = settings.mediaProcessingSettings()
    val downloadSettings = settings.downloadSettings()
    return DownloaderUiState(
        url = url,
        validity = when {
            url.isBlank() -> LinkValidity.EMPTY
            WebLink.normalize(url) != null -> LinkValidity.VALID
            else -> LinkValidity.INVALID
        },
        downloadContentType = contentType ?: settings.lastDownloadContentType() ?: DownloadContentType.VIDEO,
        downloadEngine = WebLink.preferredEngine(url, settings.lastDownloadEngine()),
        maximumVideoHeight = preferences.maximumVideoHeight,
        audioFormat = preferences.audioFormat,
        embedMetadata = preferences.embedMetadata,
        embedThumbnail = preferences.embedThumbnail,
        filenamePattern = downloadSettings.filenamePattern,
        audioFilenamePattern = downloadSettings.audioFilenamePattern
    )
}

internal fun restoredDownloaderState(
    settings: SettingsStore,
    savedStateHandle: SavedStateHandle
): DownloaderUiState {
    val url = savedStateHandle[DownloaderSavedState.URL] ?: ""
    val contentType = savedStateHandle.get<String>(DownloaderSavedState.CONTENT_TYPE)
        ?.let { runCatching { DownloadContentType.valueOf(it) }.getOrNull() }
    val initial = downloaderDefaultState(settings, url, contentType)
    return initial.copy(
        downloadEngine = savedStateHandle.get<String>(DownloaderSavedState.ENGINE)
            ?.let { runCatching { DownloadEngine.valueOf(it) }.getOrNull() }
            ?: initial.downloadEngine,
        maximumVideoHeight = savedStateHandle[DownloaderSavedState.MAXIMUM_HEIGHT] ?: initial.maximumVideoHeight,
        audioFormat = savedStateHandle.get<String>(DownloaderSavedState.AUDIO_FORMAT)
            ?.let { runCatching { AudioOutputFormat.valueOf(it) }.getOrNull() }
            ?: initial.audioFormat,
        embedMetadata = savedStateHandle[DownloaderSavedState.EMBED_METADATA] ?: initial.embedMetadata,
        embedThumbnail = savedStateHandle[DownloaderSavedState.EMBED_THUMBNAIL] ?: initial.embedThumbnail
    )
}
