package dev.qtremors.acqua.feature.downloader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
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
import dev.qtremors.acqua.downloader.YtDlpCancellation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class DownloaderViewModel(
    history: HistoryWriter,
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
    private val selectionMemory = DownloadSelectionMemory(mutableState, savedStateHandle, settings)
    private val preferences = DownloaderPreferences(mutableState, settings, savedStateHandle, selectionMemory)
    val downloadDefaults = preferences.downloadDefaults
    private val contentSelection = DownloadContentSelection(mutableState, savedStateHandle, resolution, viewModelScope)
    private val submissions = DownloadSubmissionController(mutableState, savedStateHandle, ytDlpDownloads, settings)
    private val taskObserver = DownloadTaskObserver(ytDlpDownloads, mutableState, mutableEvents, ioDispatcher) { ids ->
        if (activeWorkIds == ids) activeWorkIds = emptyList()
    }
    private val linkHistory = DownloadLinkHistory(history, ioDispatcher)
    private val pendingDownloads = PendingDownloadController(
        mutableState, ytDlpDownloads, resolution, viewModelScope, submissions::enqueue, submissions::enqueueDirect, ::recordLink
    )

    init {
        viewModelScope.launch { settings.preferenceChanges().collect { refreshSettingsDefaults() } }
        viewModelScope.launch {
            ytDlpDownloads.observePending().collectLatest { links ->
                mutableState.value = mutableState.value.copy(pendingLinks = links)
            }
        }
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
        refreshSettingsDefaults()
        selectionMemory.remember()
        val contentType = if (WebLink.isYouTubeMusicUrl(value)) DownloadContentType.AUDIO
            else settings.lastDownloadContentType() ?: DownloadContentType.VIDEO
        savedStateHandle[DownloaderSavedState.URL] = value
        savedStateHandle["downloader.accepted"] = hashMapOf<String, String>()
        savedStateHandle["downloader.rejected"] = false
        savedStateHandle.remove<ArrayList<Int>>(DownloaderSavedState.COLLECTION_SELECTION)
        savedStateHandle.remove<Boolean>(DownloaderSavedState.PLAYLIST)
        contentSelection.cancel()
        ytDlpEngine.cancelAll()
        resolutionJob?.cancel()
        downloadJob?.cancel()
        itemDownloadJob?.cancel()
        pendingDownloads.cancel()
        activeWorkIds = emptyList()
        lastAutomaticRequest = null
        mutableState.value = downloaderDefaultState(
            settings = settings,
            url = value,
            contentType = contentType
        ).copy(pendingLinks = mutableState.value.pendingLinks,
            downloadQueue = mutableState.value.downloadQueue, activeDownloadCount = mutableState.value.activeDownloadCount)
        selectionMemory.adopt(if (mutableState.value.downloadContentType == DownloadContentType.AUDIO) MediaKind.AUDIO else MediaKind.VIDEO)
        if (WebLink.extractAll(value).size > 1) savePendingLinks()
    }

    fun setAdvancedOptions(options: YtDlpDownloadOptions) = preferences.setAdvancedOptions(options)

    fun applyDownloadConfiguration(options: YtDlpDownloadOptions) {
        val snapshot = mutableState.value
        val profileChanged = snapshot.advancedOptions.cookieProfileId != options.cookieProfileId || snapshot.advancedOptions.cookieProfileFingerprint != options.cookieProfileFingerprint
        if (profileChanged) {
            resolutionJob?.cancel(); contentSelection.cancel(); ytDlpEngine.cancelAll(); lastAutomaticRequest = null
            savedStateHandle["downloader.accepted"] = hashMapOf<String, String>()
        }
        preferences.applyDownloadConfiguration(options)
    }

    fun setDefaultDownloadOptions(options: YtDlpDownloadOptions) = preferences.setDefaultDownloadOptions(options)

    fun updateDownloadDefaults(update: (YtDlpDownloadOptions) -> YtDlpDownloadOptions) = preferences.updateDownloadDefaults(update)

    private fun refreshSettingsDefaults() = preferences.refreshSettingsDefaults()

    fun selectCollectionItem(index: Int) = submissions.selectCollectionItem(index)
    fun selectCollectionRange(value: String): Boolean = submissions.selectCollectionRange(value)
    fun selectAllCollectionItems(selected: Boolean) = submissions.selectAllCollectionItems(selected)
    fun selectPreviewItem(index: Int, beginSelection: Boolean) = submissions.selectPreviewItem(index, beginSelection)
    fun selectPreviewItems(indices: Set<Int>?) = submissions.selectPreviewItems(indices)

    fun applyPlaylistSelection(playlist: Boolean, indices: Set<Int>?) {
        if (playlist != mutableState.value.downloadPlaylist) {
            resolutionJob?.cancel()
            ytDlpEngine.cancelAll()
        }
        contentSelection.selectPlaylist(playlist, indices)
        lastAutomaticRequest = null
        selectionMemory.remember()
    }

    fun inspectCollectionEntry(index: Int, browserSessions: Boolean,
        browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>) =
        contentSelection.inspect(index, browserSessions, browserResolver)

    fun applyContentConfiguration(options: YtDlpDownloadOptions, indices: Set<Int>?) {
        contentSelection.selectPlaylist(mutableState.value.downloadPlaylist, indices)
        val current = mutableState.value.advancedOptions
        setDefaultDownloadOptions(current.copy(captionMode = options.captionMode, captionFormat = options.captionFormat,
            captionLanguages = options.captionLanguages, automaticCaptions = options.automaticCaptions,
            clipStartSeconds = options.clipStartSeconds, clipEndSeconds = options.clipEndSeconds, splitChapters = options.splitChapters,
            clipRanges = options.clipRanges.toList(), captionTiming = options.captionTiming,
            downloadThumbnail = options.downloadThumbnail))
    }

    fun savePendingLinks() = pendingDownloads.save()

    fun downloadPendingLinks(
        browserSessionsEnabled: Boolean,
        browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>
    ) = pendingDownloads.download(browserSessionsEnabled, browserResolver)

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
            selectionMemory.restore(if (media.all { it.kind == MediaKind.IMAGE }) MediaKind.IMAGE
                else if (isAudioOnly || mutableState.value.downloadContentType == DownloadContentType.AUDIO) MediaKind.AUDIO else MediaKind.VIDEO)
        }
    }

    fun setDownloadContentType(value: DownloadContentType) = preferences.setDownloadContentType(value)

    fun setDownloadEngine(value: DownloadEngine) {
        val snapshot = mutableState.value
        if (value == snapshot.downloadEngine ||
            snapshot.isSaving || snapshot.savingItemIndex != null || snapshot.isBatchRunning
        ) return
        contentSelection.cancel()
        ytDlpEngine.cancelAll()
        resolutionJob?.cancel()
        lastAutomaticRequest = null
        preferences.setDownloadEngine(value)
    }

    fun setMaximumVideoHeight(value: Int) = preferences.setMaximumVideoHeight(value)

    fun setAudioFormat(value: AudioOutputFormat) = preferences.setAudioFormat(value)

    fun setEmbedMetadata(enabled: Boolean) = preferences.setEmbedMetadata(enabled)

    fun setEmbedThumbnail(enabled: Boolean) = preferences.setEmbedThumbnail(enabled)

    fun setCropArtwork(enabled: Boolean) = preferences.setCropArtwork(enabled)

    fun setPreserveOriginalMetadata(enabled: Boolean) = preferences.setPreserveOriginalMetadata(enabled)

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
        pendingDownloads.cancel()
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

    fun resetResolution() {
        lastAutomaticRequest = null
    }

    fun renameMedia(index: Int, name: String) {
        val snapshot = mutableState.value
        if (snapshot.isSaving || snapshot.savingItemIndex != null) {
            val workId = if (snapshot.savingItemIndex == index) activeWorkIds.singleOrNull()
                else if (snapshot.isSaving) snapshot.media?.getOrNull(index)?.let { media ->
                    val key = "${media.collectionUrl ?: WebLink.mediaAssetIdentity(media.url)}#${media.collectionIndex}"
                    snapshot.acceptedMediaTasks[key]?.let(UUID::fromString)
                } else null
            if (workId != null && !ytDlpDownloads.rename(workId, name)) return
        }
        mutableState.value = snapshot.copy(media = snapshot.media?.mapIndexed { position, media ->
            if (position == index) media.copy(downloadName = name.trim().takeIf(String::isNotBlank)) else media
        })
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
        if (WebLink.extractAll(snapshot.url).size > 1) return
        if (snapshot.validity != LinkValidity.VALID) return
        val normalizedUrl = snapshot.resolutionUrl() ?: return
        val requestKey = AutomaticRequest(
            normalizedUrl,
            browserSessionsEnabled,
            requestRevision,
            snapshot.downloadEngine
        )
        if (snapshot.media != null && snapshot.error == null && lastAutomaticRequest == null) {
            lastAutomaticRequest = requestKey
            return
        }
        if (requestKey == lastAutomaticRequest) return
        lastAutomaticRequest = requestKey
        ytDlpEngine.cancelAll()
        resolutionJob?.cancel()
        resolutionJob = viewModelScope.launch {
            delay(350)
            val url = mutableState.value.resolutionUrl() ?: return@launch
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
                val media = resolution.resolveConfigured(url, browserSessionsEnabled, snapshot.downloadEngine,
                    snapshot.advancedOptions.cookieProfileId, browserResolver)
                val isAudioOnly = media.isNotEmpty() && media.all { it.isAudio }
                recordLink(url, media.firstOrNull())
                selectionMemory.restore(if (media.isNotEmpty() && media.all { it.kind == MediaKind.IMAGE }) MediaKind.IMAGE
                    else if (isAudioOnly || snapshot.downloadContentType == DownloadContentType.AUDIO) MediaKind.AUDIO else MediaKind.VIDEO)
                mutableState.value = mutableState.value.copy(
                    isResolving = false,
                    media = media,
                    advancedOptions = mutableState.value.advancedOptions.copy(cookieProfileId = snapshot.advancedOptions.cookieProfileId,
                        cookieProfileFingerprint = snapshot.advancedOptions.cookieProfileFingerprint),
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
        if (WebLink.extractAll(mutableState.value.url).size > 1) {
            downloadPendingLinks(browserSessionsEnabled, browserResolver)
            return
        }
        if (mutableState.value.isSaving || mutableState.value.savingItemIndex != null) return
        ytDlpEngine.cancelAll()
        resolutionJob?.cancel()
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            val previousSnapshot = mutableState.value
            val reuseAccepted = previousSnapshot.hasRejectedItems || previousSnapshot.error is DownloaderProblem.Download ||
                previousSnapshot.acceptedMediaTasks.values.any { id -> previousSnapshot.downloadQueue.items.any {
                    it.id == id && (it.isActive || it.canRetry)
                } }
            val url = mutableState.value.resolutionUrl() ?: return@launch
            mutableState.value = mutableState.value.copy(
                error = null,
                showBrowserAction = false,
                resolutionFailure = null
            )
            val items = mutableState.value.media ?: try {
                mutableState.value = mutableState.value.copy(isResolving = true)
                recordLink(url)
                resolution.resolveConfigured(url, browserSessionsEnabled, mutableState.value.downloadEngine,
                    mutableState.value.advancedOptions.cookieProfileId, browserResolver).also {
                    recordLink(url, it.firstOrNull())
                    selectionMemory.restore(if (it.isNotEmpty() && it.all { item -> item.kind == MediaKind.IMAGE }) MediaKind.IMAGE
                        else if (it.isNotEmpty() && it.all { item -> item.isAudio }) MediaKind.AUDIO else mutableState.value.selectionKind())
                    mutableState.value = mutableState.value.copy(
                        isResolving = false,
                        media = it, advancedOptions = mutableState.value.advancedOptions.copy(cookieProfileId = previousSnapshot.advancedOptions.cookieProfileId,
                            cookieProfileFingerprint = previousSnapshot.advancedOptions.cookieProfileFingerprint)
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
            selectionMemory.remember()
            val snapshot = mutableState.value
            val workIds = mutableListOf<UUID>()
            val accepted = if (reuseAccepted) snapshot.acceptedMediaTasks.toMutableMap() else mutableMapOf<String, String>()
            var enqueueFailed = false
            items.forEachIndexed { index, item ->
                if (item.unavailableReason != null ||
                    (snapshot.selectedCollectionItems != null && index !in snapshot.selectedCollectionItems)) return@forEachIndexed
                try {
                    val key = "${item.collectionUrl ?: WebLink.mediaAssetIdentity(item.url)}#${item.collectionIndex}"
                    val previous = accepted[key]?.let(UUID::fromString)
                    val id = if (previous != null) {
                        if (snapshot.downloadQueue.items.any { it.id == previous.toString() && it.canRetry }) {
                            ytDlpDownloads.retry(previous) ?: previous
                        } else previous
                    } else if (item.backend == MediaBackend.YT_DLP) submissions.enqueue(item, url, snapshot)
                        else submissions.enqueueDirect(item, index, url, items.size, snapshot)
                    workIds += id
                    accepted[key] = id.toString()
                    savedStateHandle["downloader.accepted"] = HashMap(accepted)
                    mutableState.value = mutableState.value.copy(acceptedMediaTasks = accepted.toMap())
                    activeWorkIds = workIds.toList()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { enqueueFailed = true }
            }
            savedStateHandle["downloader.rejected"] = enqueueFailed
            mutableState.value = mutableState.value.copy(hasRejectedItems = enqueueFailed)
            if (workIds.isEmpty()) {
                mutableState.value = mutableState.value.copy(isSaving = false,
                    error = if (enqueueFailed) DownloaderProblem.Download(DownloadFailure.UNKNOWN) else null)
                return@launch
            }
            val selected = snapshot.selectedIndices()
            val downloadedKind = items.filterIndexed { index, _ -> index in selected }
                .map(submissions::downloadedKind).distinct().singleOrNull()
            observeDownloads(workIds, downloadedKind = downloadedKind, enqueueFailed = enqueueFailed)
        }
    }

    fun downloadOne(item: ResolvedMedia, index: Int, width: Int, height: Int) {
        if (mutableState.value.isSaving || mutableState.value.savingItemIndex != null) return
        itemDownloadJob?.cancel()
        itemDownloadJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                savingItemIndex = index,
                savedItemIndex = null,
                lastDownloadedKind = null,
                error = null,
                resolutionFailure = null,
                showBrowserAction = false
            )
            val sourceUrl = WebLink.normalize(mutableState.value.url).orEmpty()
            selectionMemory.remember()
            val mediaWithDimensions = if (item.isVideo) item else item.copy(width = width, height = height)
            val workId = try { if (item.backend == MediaBackend.YT_DLP) {
                submissions.enqueue(mediaWithDimensions, sourceUrl)
            } else {
                submissions.enqueueDirect(mediaWithDimensions, index, sourceUrl, mutableState.value.media?.size ?: 1)
            } } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableState.value = mutableState.value.copy(savingItemIndex = null,
                    error = DownloaderProblem.Download(DownloadFailure.UNKNOWN))
                return@launch
            }
            observeDownloads(listOf(workId), itemIndex = index, downloadedKind = submissions.downloadedKind(item))
        }
    }

    private suspend fun recordLink(url: String, media: ResolvedMedia? = null,
        recordHistory: Boolean = mutableState.value.advancedOptions.recordHistory) = linkHistory.record(url, media, recordHistory)

    private fun showResolutionError(error: Exception) {
        val failure = error.toMediaResolutionFailure()
        mutableState.value = mutableState.value.copy(
            isResolving = false,
            error = DownloaderProblem.Resolution(failure),
            resolutionFailure = failure,
            showBrowserAction = failure != MediaResolutionFailure.INVALID_LINK
        )
    }

    suspend fun retryDiagnostic(id: String): Boolean {
        val requestId = runCatching { UUID.fromString(id) }.getOrNull() ?: return false
        val retried = withContext(ioDispatcher) { runCatching { ytDlpDownloads.retry(requestId) }.getOrNull() } ?: return false
        mutableState.value = mutableState.value.copy(error = null)
        viewModelScope.launch { observeDownloads(listOf(retried)) }
        return true
    }

    private suspend fun observeDownloads(
        workIds: List<UUID>,
        itemIndex: Int? = null,
        downloadedKind: MediaKind? = null,
        enqueueFailed: Boolean = false
    ) {
        activeWorkIds = workIds
        taskObserver.observe(workIds, itemIndex, downloadedKind, enqueueFailed)
    }

    override fun onCleared() {
        contentSelection.cancel()
        ytDlpEngine.cancelAll()
    }
}
