package dev.qtremors.acqua.feature.downloader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import dev.qtremors.acqua.data.history.HistoryEntry
import dev.qtremors.acqua.data.history.HistoryRepository
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.MediaResolutionService
import dev.qtremors.acqua.domain.MediaResolutionException
import dev.qtremors.acqua.domain.MediaResolutionFailure
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.platform.storage.MediaStorage
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.downloader.DownloadQueueSnapshot
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions
import dev.qtremors.acqua.downloader.YtDlpDownloadCoordinator
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
    val error: String? = null,
    val media: List<ResolvedMedia>? = null,
    val saved: Boolean = false,
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
    val resolutionFailure: MediaResolutionFailure? = null
)

private data class AutomaticRequest(
    val url: String,
    val browserSessionsEnabled: Boolean,
    val revision: Int,
    val engine: DownloadEngine
)

sealed interface DownloaderEvent {
    data object ResolutionComplete : DownloaderEvent
    data object DownloadComplete : DownloaderEvent
    data object ItemSaved : DownloaderEvent
    data class ItemSaveFailed(val message: String) : DownloaderEvent
}

class DownloaderViewModel(
    private val history: HistoryRepository,
    private val resolution: MediaResolutionService,
    private val storage: MediaStorage,
    private val settings: AppSettingsRepository,
    private val ytDlpEngine: YtDlpEngine,
    private val ytDlpDownloads: YtDlpDownloadCoordinator
) : ViewModel() {
    private val mutableState = MutableStateFlow(defaultState())
    val state = mutableState.asStateFlow()
    private val mutableEvents = Channel<DownloaderEvent>(Channel.BUFFERED)
    val events = mutableEvents.receiveAsFlow()
    private var resolutionJob: Job? = null
    private var downloadJob: Job? = null
    private var itemDownloadJob: Job? = null
    private var lastAutomaticRequest: AutomaticRequest? = null
    private var activeWorkIds: List<UUID> = emptyList()

    init {
        viewModelScope.launch {
            ytDlpDownloads.observeQueue().collectLatest { queue ->
                val active = queue.activeItems
                if (active.isNotEmpty()) activeWorkIds = active.mapNotNull {
                    runCatching { UUID.fromString(it.id) }.getOrNull()
                }
                mutableState.value = mutableState.value.copy(
                    activeDownloadCount = queue.activeCount,
                    downloadQueue = queue,
                    downloadedBytes = queue.aggregateDownloadedBytes,
                    totalBytes = queue.aggregateTotalBytes,
                    downloadProgress = if (active.isNotEmpty()) {
                        queue.aggregateProgress
                    } else {
                        mutableState.value.downloadProgress
                    }
                )
            }
        }
    }

    fun setInitialUrl(url: String) {
        if (mutableState.value.url.isEmpty() && url.isNotBlank()) updateUrl(url)
    }

    fun updateUrl(value: String) {
        ytDlpEngine.cancelAll()
        resolutionJob?.cancel()
        downloadJob?.cancel()
        itemDownloadJob?.cancel()
        activeWorkIds = emptyList()
        lastAutomaticRequest = null
        mutableState.value = defaultState(
            url = value,
            contentType = if (WebLink.isYouTubeMusicUrl(value)) DownloadContentType.AUDIO else null
        )
    }

    fun seedResolvedMedia(url: String, media: List<ResolvedMedia>) {
        updateUrl(url)
        if (media.isNotEmpty()) {
            mutableState.value = mutableState.value.copy(
                media = media,
                isResolving = false,
                downloadEngine = if (media.all { it.backend == MediaBackend.DIRECT }) {
                    DownloadEngine.ACQUA
                } else {
                    DownloadEngine.YT_DLP
                }
            )
        }
    }

    fun setDownloadContentType(value: DownloadContentType) {
        settings.setLastDownloadContentType(value)
        mutableState.value = mutableState.value.copy(downloadContentType = value)
    }

    fun setDownloadEngine(value: DownloadEngine) {
        val snapshot = mutableState.value
        if (value == snapshot.downloadEngine ||
            snapshot.isSaving || snapshot.savingItemIndex != null
        ) return
        ytDlpEngine.cancelAll()
        resolutionJob?.cancel()
        lastAutomaticRequest = null
        settings.setLastDownloadEngine(value)
        mutableState.value = snapshot.copy(
            downloadEngine = value,
            isResolving = false,
            error = null,
            media = null,
            saved = false,
            showBrowserAction = false,
            resolutionFailure = null,
            downloadProgress = 0f,
            downloadEtaSeconds = 0L,
            downloadedBytes = 0L,
            totalBytes = 0L
        )
    }

    fun setMaximumVideoHeight(value: Int) {
        val sanitized = value.coerceAtLeast(0)
        settings.setMaximumVideoHeight(sanitized)
        mutableState.value = mutableState.value.copy(maximumVideoHeight = sanitized)
    }

    fun setAudioFormat(value: AudioOutputFormat) {
        settings.setAudioFormat(value)
        mutableState.value = mutableState.value.copy(audioFormat = value)
    }

    fun setEmbedMetadata(enabled: Boolean) {
        settings.setEmbedMetadata(enabled)
        mutableState.value = mutableState.value.copy(embedMetadata = enabled)
    }

    fun setEmbedThumbnail(enabled: Boolean) {
        settings.setEmbedThumbnail(enabled)
        mutableState.value = mutableState.value.copy(embedThumbnail = enabled)
    }

    fun cancelActiveDownload() {
        activeWorkIds.forEach(ytDlpDownloads::cancel)
        activeWorkIds = emptyList()
        mutableState.value = mutableState.value.copy(
            isSaving = false,
            downloadProgress = 0f,
            downloadEtaSeconds = 0L,
            downloadedBytes = 0L,
            totalBytes = 0L
        )
    }

    fun cancelDownload(id: String) {
        runCatching { UUID.fromString(id) }.getOrNull()?.let(ytDlpDownloads::cancel)
    }

    fun dismissError() {
        mutableState.value = mutableState.value.copy(
            error = null,
            showBrowserAction = false,
            resolutionFailure = null
        )
    }

    fun resolve(
        browserSessionsEnabled: Boolean,
        requestRevision: Int,
        browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>
    ) {
        val snapshot = mutableState.value
        if (snapshot.validity != LinkValidity.VALID) return
        val normalizedUrl = WebLink.normalize(snapshot.url) ?: return
        val requestKey = AutomaticRequest(
            normalizedUrl,
            browserSessionsEnabled,
            requestRevision,
            snapshot.downloadEngine
        )
        if (snapshot.media != null && lastAutomaticRequest == null) {
            lastAutomaticRequest = requestKey
            return
        }
        if (requestKey == lastAutomaticRequest) return
        lastAutomaticRequest = requestKey
        ytDlpEngine.cancelAll()
        resolutionJob?.cancel()
        resolutionJob = viewModelScope.launch {
            delay(350)
            val url = WebLink.normalize(mutableState.value.url) ?: return@launch
            recordLink(url)
            mutableState.value = mutableState.value.copy(
                isResolving = true,
                media = null,
                error = null,
                resolutionFailure = null,
                saved = false,
                showBrowserAction = false
            )
            try {
                val media = resolution.resolve(
                    url,
                    browserSessionsEnabled,
                    false,
                    snapshot.downloadEngine,
                    browserResolver = browserResolver
                )
                mutableState.value = mutableState.value.copy(
                    isResolving = false,
                    media = media
                )
                mutableEvents.send(DownloaderEvent.ResolutionComplete)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (lastAutomaticRequest == requestKey) lastAutomaticRequest = null
                showResolutionError(error)
            }
        }
    }

    fun downloadAll(
        browserSessionsEnabled: Boolean,
        browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>
    ) {
        if (mutableState.value.isSaving || mutableState.value.savingItemIndex != null) return
        ytDlpEngine.cancelAll()
        resolutionJob?.cancel()
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            val url = WebLink.normalize(mutableState.value.url) ?: return@launch
            mutableState.value = mutableState.value.copy(
                error = null,
                showBrowserAction = false,
                resolutionFailure = null
            )
            val items = mutableState.value.media ?: try {
                mutableState.value = mutableState.value.copy(isResolving = true)
                recordLink(url)
                resolution.resolve(
                    url,
                    browserSessionsEnabled,
                    false,
                    mutableState.value.downloadEngine,
                    browserResolver = browserResolver
                ).also {
                    mutableState.value = mutableState.value.copy(
                        isResolving = false,
                        media = it
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showResolutionError(error)
                return@launch
            }

            mutableState.value = mutableState.value.copy(isSaving = true, savingIndex = 0)
            val workIds = if (items.size == 1 && items.single().backend == MediaBackend.YT_DLP) {
                listOf(enqueueYtDlpDownload(items.single(), url))
            } else {
                items.mapIndexed { index, item -> ytDlpDownloads.enqueueDirect(item, index, url) }
            }
            observeDownloads(workIds)
        }
    }

    fun downloadOne(item: ResolvedMedia, index: Int, width: Int, height: Int) {
        if (mutableState.value.isSaving || mutableState.value.savingItemIndex != null) return
        itemDownloadJob?.cancel()
        itemDownloadJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(savingItemIndex = index, savedItemIndex = null)
            val sourceUrl = WebLink.normalize(mutableState.value.url).orEmpty()
            val mediaWithDimensions = if (item.isVideo) item else item.copy(width = width, height = height)
            val workId = if (item.backend == MediaBackend.YT_DLP) {
                enqueueYtDlpDownload(mediaWithDimensions, sourceUrl)
            } else {
                ytDlpDownloads.enqueueDirect(mediaWithDimensions, index, sourceUrl)
            }
            observeDownloads(listOf(workId), itemIndex = index)
        }
    }

    private suspend fun recordLink(url: String) = withContext(Dispatchers.IO) {
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
                isDownloaded = false
            )
        )
    }

    private fun showResolutionError(error: Exception) {
        val failure = when (error) {
            is MediaResolutionException -> error.failure
            is ExpiredSessionException -> MediaResolutionFailure.SESSION_EXPIRED
            is AgeGateException -> MediaResolutionFailure.SESSION_REQUIRED
            is IOException -> MediaResolutionFailure.NETWORK
            is IllegalArgumentException -> MediaResolutionFailure.INVALID_LINK
            else -> MediaResolutionFailure.UNKNOWN
        }
        mutableState.value = mutableState.value.copy(
            isResolving = false,
            error = error.message ?: "An unexpected error occurred",
            resolutionFailure = failure,
            showBrowserAction = failure != MediaResolutionFailure.INVALID_LINK
        )
    }

    private fun enqueueYtDlpDownload(media: ResolvedMedia, sourceUrl: String): UUID {
        val snapshot = mutableState.value
        val options = YtDlpDownloadOptions(
            contentType = snapshot.downloadContentType,
            maximumVideoHeight = snapshot.maximumVideoHeight,
            audioFormat = snapshot.audioFormat,
            embedMetadata = snapshot.embedMetadata,
            embedThumbnail = snapshot.embedThumbnail
        )
        val outputMedia = if (options.contentType == DownloadContentType.AUDIO) {
            media.copy(width = 0, height = 0)
        } else {
            YtDlpFormatSelector.selectedVideoFormat(media.formats, options.maximumVideoHeight)
                ?.let { selected -> media.copy(width = selected.width, height = selected.height) }
                ?: media
        }
        return ytDlpDownloads.enqueue(media, outputMedia, options, sourceUrl).also {
            activeWorkIds = listOf(it)
        }
    }

    private suspend fun observeDownloads(workIds: List<UUID>, itemIndex: Int? = null) {
        activeWorkIds = workIds
        val final = ytDlpDownloads.observe(workIds)
            .onEach { jobs ->
                val unfinished = jobs.filterNot { it.state.isFinished }
                if (unfinished.isNotEmpty()) {
                    val progress = jobs.map {
                        if (it.state == WorkInfo.State.SUCCEEDED) 100f
                        else it.progress.getFloat(DownloadWorkData.KEY_PROGRESS, 0f)
                    }.average().toFloat()
                    val knownSizeJobs = unfinished.filter {
                        it.progress.getLong(DownloadWorkData.KEY_TOTAL_BYTES, 0L) > 0L
                    }
                    val sizeJobs = knownSizeJobs.ifEmpty { unfinished }
                    mutableState.value = mutableState.value.copy(
                        isSaving = itemIndex == null,
                        savingItemIndex = itemIndex,
                        savingIndex = jobs.count { it.state == WorkInfo.State.SUCCEEDED },
                        downloadProgress = progress,
                        downloadEtaSeconds = unfinished.maxOfOrNull {
                            it.progress.getLong(DownloadWorkData.KEY_ETA_SECONDS, 0L)
                        } ?: 0L,
                        downloadedBytes = sizeJobs.sumOf {
                            it.progress.getLong(DownloadWorkData.KEY_DOWNLOADED_BYTES, 0L)
                        },
                        totalBytes = knownSizeJobs.sumOf {
                            it.progress.getLong(DownloadWorkData.KEY_TOTAL_BYTES, 0L)
                        }
                    )
                }
            }
            .first { jobs -> jobs.all { it.state.isFinished } }
        if (activeWorkIds == workIds) activeWorkIds = emptyList()
        val failure = final.firstOrNull { it.state == WorkInfo.State.FAILED }
        when {
            failure != null -> {
                val message = failure.outputData.getString(DownloadWorkData.KEY_ERROR)
                    ?: "The background download failed."
                mutableState.value = mutableState.value.copy(
                    isSaving = false,
                    savingItemIndex = null,
                    downloadProgress = 0f,
                    downloadEtaSeconds = 0L,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    error = message,
                    showBrowserAction = WebLink.normalize(mutableState.value.url) != null
                )
                if (itemIndex != null) mutableEvents.send(DownloaderEvent.ItemSaveFailed(message))
            }
            final.any { it.state == WorkInfo.State.CANCELLED } -> mutableState.value = mutableState.value.copy(
                isSaving = false,
                savingItemIndex = null,
                downloadProgress = 0f,
                downloadEtaSeconds = 0L,
                downloadedBytes = 0L,
                totalBytes = 0L
            )
            itemIndex != null -> {
                mutableState.value = mutableState.value.copy(
                    savingItemIndex = null,
                    savedItemIndex = itemIndex,
                    downloadProgress = 100f,
                    downloadedBytes = 0L,
                    totalBytes = 0L
                )
                mutableEvents.send(DownloaderEvent.ItemSaved)
                delay(2000)
                mutableState.value = mutableState.value.copy(savedItemIndex = null)
            }
            else -> {
                mutableState.value = mutableState.value.copy(
                    isSaving = false,
                    saved = true,
                    downloadProgress = 100f,
                    downloadEtaSeconds = 0L,
                    downloadedBytes = 0L,
                    totalBytes = 0L
                )
                mutableEvents.send(DownloaderEvent.DownloadComplete)
            }
        }
    }

    private fun defaultState(
        url: String = "",
        contentType: DownloadContentType? = null
    ): DownloaderUiState {
        val preferences = settings.mediaProcessingSettings()
        return DownloaderUiState(
            url = url,
            validity = when {
                url.isBlank() -> LinkValidity.EMPTY
                WebLink.normalize(url) != null -> LinkValidity.VALID
                else -> LinkValidity.INVALID
            },
            downloadContentType = contentType ?: settings.lastDownloadContentType() ?: DownloadContentType.VIDEO,
            downloadEngine = settings.lastDownloadEngine(),
            maximumVideoHeight = preferences.maximumVideoHeight,
            audioFormat = preferences.audioFormat,
            embedMetadata = preferences.embedMetadata,
            embedThumbnail = preferences.embedThumbnail
        )
    }

    override fun onCleared() {
        ytDlpEngine.cancelAll()
        super.onCleared()
    }
}
