package dev.qtremors.acqua.feature.downloader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.qtremors.acqua.data.history.HistoryEntry
import dev.qtremors.acqua.data.history.HistoryWriter
import dev.qtremors.acqua.data.settings.SettingsStore
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.MediaResolutionGateway
import dev.qtremors.acqua.domain.MediaResolutionFailure
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.downloader.DownloadFailure
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions
import dev.qtremors.acqua.downloader.DownloadQueueGateway
import dev.qtremors.acqua.downloader.DownloadQueueState
import dev.qtremors.acqua.downloader.YtDlpCancellation
import dev.qtremors.acqua.downloader.YtDlpFormatSelector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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

class DownloaderViewModel(
    private val history: HistoryWriter,
    private val resolution: MediaResolutionGateway,
    private val settings: SettingsStore,
    private val ytDlpEngine: YtDlpCancellation,
    private val ytDlpDownloads: DownloadQueueGateway,
    private val savedStateHandle: SavedStateHandle,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val mutableState = MutableStateFlow(restoredDownloaderState(settings, savedStateHandle))
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
                    } else if (mutableState.value.isSaving || mutableState.value.savingItemIndex != null) {
                        mutableState.value.downloadProgress
                    } else {
                        0f
                    }
                )
            }
        }
    }

    fun setInitialUrl(url: String) {
        if (mutableState.value.url.isEmpty() && url.isNotBlank()) updateUrl(url)
    }

    fun updateUrl(value: String) {
        savedStateHandle[DownloaderSavedState.URL] = value
        ytDlpEngine.cancelAll()
        resolutionJob?.cancel()
        downloadJob?.cancel()
        itemDownloadJob?.cancel()
        activeWorkIds = emptyList()
        lastAutomaticRequest = null
        mutableState.value = downloaderDefaultState(
            settings = settings,
            url = value,
            contentType = if (WebLink.isYouTubeMusicUrl(value)) DownloadContentType.AUDIO else null
        )
    }

    fun seedResolvedMedia(url: String, media: List<ResolvedMedia>) {
        updateUrl(url)
        if (media.isNotEmpty()) {
            val isAudioOnly = media.all { it.isAudio }
            mutableState.value = mutableState.value.copy(
                media = media,
                isResolving = false,
                downloadContentType = if (isAudioOnly) DownloadContentType.AUDIO else mutableState.value.downloadContentType,
                downloadEngine = if (media.all { it.backend == MediaBackend.DIRECT }) {
                    DownloadEngine.ACQUA
                } else {
                    DownloadEngine.YT_DLP
                }
            )
        }
    }

    fun setDownloadContentType(value: DownloadContentType) {
        savedStateHandle[DownloaderSavedState.CONTENT_TYPE] = value.name
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
        savedStateHandle[DownloaderSavedState.ENGINE] = value.name
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
        savedStateHandle[DownloaderSavedState.MAXIMUM_HEIGHT] = sanitized
        mutableState.value = mutableState.value.copy(maximumVideoHeight = sanitized)
    }

    fun setAudioFormat(value: AudioOutputFormat) {
        settings.setAudioFormat(value)
        savedStateHandle[DownloaderSavedState.AUDIO_FORMAT] = value.name
        mutableState.value = mutableState.value.copy(audioFormat = value)
    }

    fun setEmbedMetadata(enabled: Boolean) {
        settings.setEmbedMetadata(enabled)
        savedStateHandle[DownloaderSavedState.EMBED_METADATA] = enabled
        mutableState.value = mutableState.value.copy(embedMetadata = enabled)
    }

    fun setEmbedThumbnail(enabled: Boolean) {
        settings.setEmbedThumbnail(enabled)
        savedStateHandle[DownloaderSavedState.EMBED_THUMBNAIL] = enabled
        mutableState.value = mutableState.value.copy(embedThumbnail = enabled)
    }

    fun updateMediaDimensions(index: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val snapshot = mutableState.value
        val media = snapshot.media ?: return
        val item = media.getOrNull(index) ?: return
        if (item.isVideo || item.width == width && item.height == height) return
        mutableState.value = snapshot.copy(
            media = media.mapIndexed { itemIndex, resolvedMedia ->
                if (itemIndex == index) resolvedMedia.copy(width = width, height = height) else resolvedMedia
            }
        )
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
                val isAudioOnly = media.isNotEmpty() && media.all { it.isAudio }
                mutableState.value = mutableState.value.copy(
                    isResolving = false,
                    media = media,
                    downloadContentType = if (isAudioOnly) DownloadContentType.AUDIO else snapshot.downloadContentType,
                    filenamePattern = settings.downloadSettings().filenamePattern,
                    audioFilenamePattern = settings.downloadSettings().audioFilenamePattern
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

            mutableState.value = mutableState.value.copy(
                isSaving = true,
                savingIndex = 0,
                lastDownloadedKind = null
            )
            val isProcessedDownload = items.size == 1 && items.single().backend == MediaBackend.YT_DLP
            val workIds = if (isProcessedDownload) {
                listOf(enqueueYtDlpDownload(items.single(), url))
            } else {
                items.mapIndexed { index, item -> ytDlpDownloads.enqueueDirect(item, index, url, items.size) }
            }
            val downloadedKind = if (isProcessedDownload) {
                if (mutableState.value.downloadContentType == DownloadContentType.AUDIO) MediaKind.AUDIO else MediaKind.VIDEO
            } else {
                items.map(ResolvedMedia::kind).distinct().singleOrNull()
            }
            observeDownloads(workIds, downloadedKind = downloadedKind)
        }
    }

    fun downloadOne(item: ResolvedMedia, index: Int, width: Int, height: Int) {
        if (mutableState.value.isSaving || mutableState.value.savingItemIndex != null) return
        itemDownloadJob?.cancel()
        itemDownloadJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                savingItemIndex = index,
                savedItemIndex = null,
                lastDownloadedKind = null
            )
            val sourceUrl = WebLink.normalize(mutableState.value.url).orEmpty()
            val mediaWithDimensions = if (item.isVideo) item else item.copy(width = width, height = height)
            val workId = if (item.backend == MediaBackend.YT_DLP) {
                enqueueYtDlpDownload(mediaWithDimensions, sourceUrl)
            } else {
                ytDlpDownloads.enqueueDirect(mediaWithDimensions, index, sourceUrl, mutableState.value.media?.size ?: 1)
            }
            val downloadedKind = if (
                item.backend == MediaBackend.YT_DLP && mutableState.value.downloadContentType == DownloadContentType.AUDIO
            ) MediaKind.AUDIO else item.kind
            observeDownloads(listOf(workId), itemIndex = index, downloadedKind = downloadedKind)
        }
    }

    private suspend fun recordLink(url: String) = withContext(ioDispatcher) {
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
        val failure = error.toMediaResolutionFailure()
        mutableState.value = mutableState.value.copy(
            isResolving = false,
            error = DownloaderProblem.Resolution(failure),
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

    private suspend fun observeDownloads(
        workIds: List<UUID>,
        itemIndex: Int? = null,
        downloadedKind: MediaKind? = null
    ) {
        activeWorkIds = workIds
        val final = ytDlpDownloads.observe(workIds)
            .onEach { jobs ->
                val unfinished = jobs.filter { it.isActive }
                if (unfinished.isNotEmpty()) {
                    val progress = jobs.map {
                        if (it.state == DownloadQueueState.SUCCEEDED) 100f else it.progress
                    }.average().toFloat()
                    val knownSizeJobs = unfinished.filter { it.totalBytes > 0L }
                    val sizeJobs = knownSizeJobs.ifEmpty { unfinished }
                    mutableState.value = mutableState.value.copy(
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
        if (activeWorkIds == workIds) activeWorkIds = emptyList()
        val failure = final.firstOrNull { it.state == DownloadQueueState.FAILED }
        when {
            failure != null -> {
                val downloadFailure = DownloadFailure.fromCode(
                    failure.error
                )
                mutableState.value = mutableState.value.copy(
                    isSaving = false,
                    savingItemIndex = null,
                    downloadProgress = 0f,
                    downloadEtaSeconds = 0L,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    error = DownloaderProblem.Download(downloadFailure),
                    showBrowserAction = WebLink.normalize(mutableState.value.url) != null
                )
                if (itemIndex != null) mutableEvents.send(DownloaderEvent.ItemSaveFailed(downloadFailure))
            }
            final.any { it.state == DownloadQueueState.CANCELLED } -> mutableState.value = mutableState.value.copy(
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
                    lastDownloadedKind = downloadedKind,
                    downloadProgress = 100f,
                    downloadedBytes = 0L,
                    totalBytes = 0L
                )
                mutableEvents.send(DownloaderEvent.ItemSaved(requireNotNull(downloadedKind)))
                delay(2000)
                mutableState.value = mutableState.value.copy(savedItemIndex = null, downloadProgress = 0f)
            }
            else -> {
                mutableState.value = mutableState.value.copy(
                    isSaving = false,
                    saved = true,
                    lastDownloadedKind = downloadedKind,
                    downloadProgress = 100f,
                    downloadEtaSeconds = 0L,
                    downloadedBytes = 0L,
                    totalBytes = 0L
                )
                mutableEvents.send(DownloaderEvent.DownloadComplete(downloadedKind))
                delay(3000)
                mutableState.value = mutableState.value.copy(saved = false, downloadProgress = 0f)
            }
        }
    }

    override fun onCleared() {
        ytDlpEngine.cancelAll()
    }
}
