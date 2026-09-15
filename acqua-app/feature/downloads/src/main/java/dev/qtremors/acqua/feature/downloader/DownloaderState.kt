package dev.qtremors.acqua.feature.downloader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import dev.qtremors.acqua.data.history.HistoryEntry
import dev.qtremors.acqua.data.history.HistoryRepository
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.MediaResolutionService
import dev.qtremors.acqua.domain.MediaResolutionException
import dev.qtremors.acqua.domain.MediaResolutionFailure
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.platform.storage.MediaStorage
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.downloader.DownloadFailure
import dev.qtremors.acqua.downloader.DownloadQueueSnapshot
import dev.qtremors.acqua.downloader.FilenameFormatter
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions
import dev.qtremors.acqua.downloader.DownloadWorkCoordinator
import dev.qtremors.acqua.downloader.DownloadWorkData
import dev.qtremors.acqua.downloader.YtDlpEngine
import dev.qtremors.acqua.downloader.YtDlpFormatSelector
import dev.qtremors.acqua.resolver.instagram.AgeGateException
import dev.qtremors.acqua.resolver.instagram.ExpiredSessionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.io.IOException

enum class LinkValidity { EMPTY, VALID, INVALID }

data class DownloaderUiState(
    val url: String = "",
    val validity: LinkValidity = LinkValidity.EMPTY,
    val isResolving: Boolean = false,
    val isSaving: Boolean = false,
    val error: DownloaderProblem? = null,
    val media: List<ResolvedMedia>? = null,
    val saved: Boolean = false,
    val lastDownloadedKind: MediaKind? = null,
    val savingIndex: Int = 0,
    val showBrowserAction: Boolean = false,
    val savingItemIndex: Int? = null,
    val savedItemIndex: Int? = null,
    val downloadContentType: DownloadContentType = DownloadContentType.VIDEO,
    val maximumVideoHeight: Int = 0,
    val audioFormat: AudioOutputFormat = AudioOutputFormat.ORIGINAL,
    val embedMetadata: Boolean = true,
    val embedThumbnail: Boolean = true,
    val downloadProgress: Float = 0f,
    val downloadEtaSeconds: Long = 0L,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadEngine: DownloadEngine = DownloadEngine.YT_DLP,
    val activeDownloadCount: Int = 0,
    val downloadQueue: DownloadQueueSnapshot = DownloadQueueSnapshot(),
    val resolutionFailure: MediaResolutionFailure? = null,
    val filenamePattern: String = FilenameFormatter.DEFAULT_PATTERN,
    val audioFilenamePattern: String = FilenameFormatter.DEFAULT_AUDIO_PATTERN
)

sealed interface DownloaderProblem {
    data class Resolution(val failure: MediaResolutionFailure) : DownloaderProblem
    data class Download(val failure: DownloadFailure) : DownloaderProblem
}

internal data class AutomaticRequest(
    val url: String,
    val browserSessionsEnabled: Boolean,
    val revision: Int,
    val engine: DownloadEngine
)

sealed interface DownloaderEvent {
    data object ResolutionComplete : DownloaderEvent
    data class DownloadComplete(val mediaKind: MediaKind?) : DownloaderEvent
    data class ItemSaved(val mediaKind: MediaKind) : DownloaderEvent
    data class ItemSaveFailed(val failure: DownloadFailure) : DownloaderEvent
}
