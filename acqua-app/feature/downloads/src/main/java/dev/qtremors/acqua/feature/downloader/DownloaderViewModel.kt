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
import dev.qtremors.acqua.downloader.CaptionMode
import dev.qtremors.acqua.downloader.CaptionFormat
import dev.qtremors.acqua.downloader.VideoContainer
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
    private val history: HistoryWriter,
    private val resolution: MediaResolutionGateway,
    private val settings: SettingsStore,
    private val ytDlpEngine: YtDlpCancellation,
    private val ytDlpDownloads: DownloadQueueGateway,
    private val savedStateHandle: SavedStateHandle,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val mutableState = MutableStateFlow(restoredDownloaderState(settings, savedStateHandle))
    private var previousMediaSettings = settings.mediaProcessingSettings()
    private var previousDownloadSettings = settings.downloadSettings()
    private var previousDownloadDefaults = settings.downloadDefaults()
    val state = mutableState.asStateFlow()
    private val mutableEvents = Channel<DownloaderEvent>(Channel.BUFFERED)
    val events = mutableEvents.receiveAsFlow()
    private var resolutionJob: Job? = null
    private var downloadJob: Job? = null
    private var itemDownloadJob: Job? = null
    private var lastAutomaticRequest: AutomaticRequest? = null
    private var activeWorkIds: List<UUID> = emptyList()
    private val selectionMemory = DownloadSelectionMemory(mutableState, savedStateHandle, settings)
    private val contentSelection = DownloadContentSelection(mutableState, savedStateHandle, resolution, viewModelScope)
    private val submissions = DownloadSubmissionController(mutableState, savedStateHandle, ytDlpDownloads, settings)
    private val taskObserver = DownloadTaskObserver(ytDlpDownloads, mutableState, mutableEvents, ioDispatcher) { ids ->
        if (activeWorkIds == ids) activeWorkIds = emptyList()
    }
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

    fun setAdvancedOptions(options: YtDlpDownloadOptions) {
        val normalized = dev.qtremors.acqua.downloader.DownloadCaptions.normalized(options.copy(contentType = mutableState.value.downloadContentType,
            audioFormat = mutableState.value.audioFormat))
        savedStateHandle["downloader.advanced"] = dev.qtremors.acqua.downloader.DownloadOptionsCodec.encode(normalized)
        mutableState.value = mutableState.value.copy(advancedOptions = normalized)
        selectionMemory.remember()
    }

    fun applyDownloadConfiguration(options: YtDlpDownloadOptions) {
        val snapshot = mutableState.value
        val profileChanged = snapshot.advancedOptions.cookieProfileId != options.cookieProfileId || snapshot.advancedOptions.cookieProfileFingerprint != options.cookieProfileFingerprint
        if (profileChanged) {
            resolutionJob?.cancel(); contentSelection.cancel(); ytDlpEngine.cancelAll(); lastAutomaticRequest = null
            savedStateHandle["downloader.accepted"] = hashMapOf<String, String>()
        }
        val height = options.maximumVideoHeight.coerceAtLeast(0)
        if (height != snapshot.maximumVideoHeight) {
            settings.setMaximumVideoHeight(height)
            previousMediaSettings = previousMediaSettings.copy(maximumVideoHeight = height)
        }
        if (options.audioFormat != snapshot.audioFormat) {
            settings.setAudioFormat(options.audioFormat)
            previousMediaSettings = previousMediaSettings.copy(audioFormat = options.audioFormat)
        }
        val embedding = snapshot.advancedOptions.captionMode == CaptionMode.EMBED || snapshot.advancedOptions.captionMode == CaptionMode.BOTH
        val advanced = snapshot.advancedOptions.copy(contentType = snapshot.downloadContentType,
            cookieProfileId = options.cookieProfileId, cookieProfileFingerprint = options.cookieProfileFingerprint,
            clipRanges = if (profileChanged) emptyList() else snapshot.advancedOptions.clipRanges,
            clipStartSeconds = snapshot.advancedOptions.clipStartSeconds.takeUnless { profileChanged },
            clipEndSeconds = snapshot.advancedOptions.clipEndSeconds.takeUnless { profileChanged },
            splitChapters = !profileChanged && snapshot.advancedOptions.splitChapters,
            captionTiming = if (profileChanged) dev.qtremors.acqua.downloader.CaptionTiming() else snapshot.advancedOptions.captionTiming,
            recordHistory = options.recordHistory,
            network = options.network.validated(),
            removeSegments = options.removeSegments.toSet(),
            formatPolicy = options.formatPolicy.validated(),
            maximumVideoHeight = height, audioFormat = options.audioFormat,
            videoFormatId = options.videoFormatId.takeUnless { profileChanged }, videoFormatIncludesAudio = options.videoFormatIncludesAudio,
            additionalAudioFormatIds = options.additionalAudioFormatIds.toSet().takeUnless { profileChanged }.orEmpty(), silentVideo = options.silentVideo, acceptedStreams = emptyList(),
            audioFormatId = options.audioFormatId.takeUnless { profileChanged }, videoContainer = options.videoContainer, audioBitrateKbps = options.audioBitrateKbps,
            captionFormat = if (options.videoContainer == VideoContainer.WEBM && embedding) CaptionFormat.VTT else snapshot.advancedOptions.captionFormat)
        val templated = options.commandTemplateId != null
        val configured = (if (templated) options.copy(contentType = snapshot.downloadContentType) else advanced).copy(
            commandTemplateId = options.commandTemplateId, commandTemplateSnapshot = options.commandTemplateSnapshot,
            formatSelectorOverride = options.formatSelectorOverride, templateAudioMultistreams = options.templateAudioMultistreams,
            network = advanced.network, formatPolicy = advanced.formatPolicy, additionalAudioFormatIds = advanced.additionalAudioFormatIds, acceptedStreams = emptyList()).let {
                if (!profileChanged) it else it.copy(clipRanges = emptyList(), clipStartSeconds = null, clipEndSeconds = null, splitChapters = false,
                    captionTiming = dev.qtremors.acqua.downloader.CaptionTiming(), videoFormatId = null, audioFormatId = null)
            }
        mutableState.value = snapshot.copy(maximumVideoHeight = height, audioFormat = options.audioFormat,
            media = snapshot.media.takeUnless { profileChanged }, isResolving = !profileChanged && snapshot.isResolving,
            inspectedCollectionItems = snapshot.inspectedCollectionItems.takeUnless { profileChanged }.orEmpty(),
            selectedCollectionItems = snapshot.selectedCollectionItems.takeUnless { profileChanged },
            acceptedMediaTasks = snapshot.acceptedMediaTasks.takeUnless { profileChanged }.orEmpty(),
            hasRejectedItems = !profileChanged && snapshot.hasRejectedItems, saved = !profileChanged && snapshot.saved,
            savedItemIndex = snapshot.savedItemIndex.takeUnless { profileChanged },
            error = snapshot.error.takeUnless { profileChanged }, resolutionFailure = snapshot.resolutionFailure.takeUnless { profileChanged },
            showBrowserAction = !profileChanged && snapshot.showBrowserAction,
            embedMetadata = if (templated) options.embedMetadata else snapshot.embedMetadata,
            embedThumbnail = if (templated) options.embedThumbnail else snapshot.embedThumbnail,
            cropArtwork = if (templated) options.cropArtwork else snapshot.cropArtwork,
            preserveOriginalMetadata = if (templated) options.preserveOriginalMetadata else snapshot.preserveOriginalMetadata,
            advancedOptions = dev.qtremors.acqua.downloader.DownloadCaptions.normalized(configured))
        selectionMemory.remember()
    }

    fun setDefaultDownloadOptions(options: YtDlpDownloadOptions) {
        settings.setDownloadDefaults(options)
        previousDownloadDefaults = settings.downloadDefaults()
        val embedding = options.captionMode == CaptionMode.EMBED || options.captionMode == CaptionMode.BOTH
        setAdvancedOptions(if (options.videoContainer == VideoContainer.WEBM && embedding) {
            options.copy(captionFormat = CaptionFormat.VTT)
        } else options)
    }

    fun updateDownloadDefaults(update: (YtDlpDownloadOptions) -> YtDlpDownloadOptions) {
        settings.setDownloadDefaults(update(settings.downloadDefaults()))
        refreshSettingsDefaults()
    }

    private fun refreshSettingsDefaults() {
        val media = settings.mediaProcessingSettings()
        val download = settings.downloadSettings()
        val defaults = settings.downloadDefaults()
        var state = mutableState.value
        if (media.maximumVideoHeight != previousMediaSettings.maximumVideoHeight) {
            state = state.copy(maximumVideoHeight = media.maximumVideoHeight)
            savedStateHandle[DownloaderSavedState.MAXIMUM_HEIGHT] = media.maximumVideoHeight
        }
        if (media.audioFormat != previousMediaSettings.audioFormat) {
            state = state.copy(audioFormat = media.audioFormat)
            savedStateHandle[DownloaderSavedState.AUDIO_FORMAT] = media.audioFormat.name
        }
        if (media.embedMetadata != previousMediaSettings.embedMetadata) {
            state = state.copy(embedMetadata = media.embedMetadata)
            savedStateHandle[DownloaderSavedState.EMBED_METADATA] = media.embedMetadata
        }
        if (media.embedThumbnail != previousMediaSettings.embedThumbnail) {
            state = state.copy(embedThumbnail = media.embedThumbnail)
            savedStateHandle[DownloaderSavedState.EMBED_THUMBNAIL] = media.embedThumbnail
        }
        if (media.cropArtwork != previousMediaSettings.cropArtwork) {
            state = state.copy(cropArtwork = media.cropArtwork)
            savedStateHandle[DownloaderSavedState.CROP_ARTWORK] = media.cropArtwork
        }
        if (media.preserveOriginalMetadata != previousMediaSettings.preserveOriginalMetadata) {
            state = state.copy(preserveOriginalMetadata = media.preserveOriginalMetadata)
            savedStateHandle[DownloaderSavedState.ORIGINAL_METADATA] = media.preserveOriginalMetadata
        }
        if (download.filenamePattern != previousDownloadSettings.filenamePattern) state = state.copy(filenamePattern = download.filenamePattern)
        if (download.audioFilenamePattern != previousDownloadSettings.audioFilenamePattern) state = state.copy(audioFilenamePattern = download.audioFilenamePattern)
        if (defaults != previousDownloadDefaults) {
            val captions = defaults.captionMode
            val embedding = captions == CaptionMode.EMBED || captions == CaptionMode.BOTH
            val options = state.advancedOptions.copy(captionMode = captions,
                captionFormat = if (state.advancedOptions.videoContainer == VideoContainer.WEBM && embedding) CaptionFormat.VTT else defaults.captionFormat,
                captionLanguages = defaults.captionLanguages, automaticCaptions = defaults.automaticCaptions,
                skipArchived = defaults.skipArchived,
                recordHistory = if (defaults.recordHistory != previousDownloadDefaults.recordHistory) defaults.recordHistory else state.advancedOptions.recordHistory,
                verboseDiagnostics = defaults.verboseDiagnostics,
                network = if (defaults.network != previousDownloadDefaults.network) defaults.network else state.advancedOptions.network,
                removeSegments = if (defaults.removeSegments != previousDownloadDefaults.removeSegments) defaults.removeSegments else state.advancedOptions.removeSegments,
                formatPolicy = if (defaults.formatPolicy != previousDownloadDefaults.formatPolicy) defaults.formatPolicy else state.advancedOptions.formatPolicy)
            state = state.copy(advancedOptions = dev.qtremors.acqua.downloader.DownloadCaptions.normalized(options))
            savedStateHandle["downloader.advanced"] = dev.qtremors.acqua.downloader.DownloadOptionsCodec.encode(options)
        }
        previousMediaSettings = media
        previousDownloadSettings = download
        previousDownloadDefaults = defaults
        val changed = state != mutableState.value
        mutableState.value = state
        if (changed) selectionMemory.remember()
    }

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

    fun setDownloadContentType(value: DownloadContentType) {
        refreshSettingsDefaults()
        savedStateHandle[DownloaderSavedState.CONTENT_TYPE] = value.name
        settings.setLastDownloadContentType(value)
        selectionMemory.remember()
        selectionMemory.restore(if (value == DownloadContentType.AUDIO) MediaKind.AUDIO else MediaKind.VIDEO)
        mutableState.value = mutableState.value.copy(downloadContentType = value)
        selectionMemory.remember()
    }

    fun setDownloadEngine(value: DownloadEngine) {
        val snapshot = mutableState.value
        if (value == snapshot.downloadEngine ||
            snapshot.isSaving || snapshot.savingItemIndex != null || snapshot.isBatchRunning
        ) return
        contentSelection.cancel()
        ytDlpEngine.cancelAll()
        resolutionJob?.cancel()
        lastAutomaticRequest = null
        settings.setLastDownloadEngine(value)
        savedStateHandle[DownloaderSavedState.ENGINE] = value.name
        savedStateHandle.remove<ArrayList<Int>>(DownloaderSavedState.COLLECTION_SELECTION)
        mutableState.value = snapshot.copy(
            downloadEngine = value,
            advancedOptions = if (value == DownloadEngine.ACQUA) snapshot.advancedOptions.copy(commandTemplateId = null,
                commandTemplateSnapshot = null, formatSelectorOverride = null, templateAudioMultistreams = false) else snapshot.advancedOptions,
            selectedCollectionItems = null,
            isResolving = false,
            error = null,
            media = null,
            inspectingCollectionIndex = null,
            inspectedCollectionItems = emptySet(),
            saved = false,
            showBrowserAction = false,
            resolutionFailure = null,
            downloadProgress = 0f,
            downloadEtaSeconds = 0L,
            downloadedBytes = 0L,
            totalBytes = 0L
        )
        selectionMemory.remember()
    }

    fun setMaximumVideoHeight(value: Int) {
        val sanitized = value.coerceAtLeast(0)
        settings.setMaximumVideoHeight(sanitized)
        previousMediaSettings = previousMediaSettings.copy(maximumVideoHeight = sanitized)
        savedStateHandle[DownloaderSavedState.MAXIMUM_HEIGHT] = sanitized
        mutableState.value = mutableState.value.copy(maximumVideoHeight = sanitized,
            advancedOptions = mutableState.value.advancedOptions.copy(formatSelectorOverride = null, templateAudioMultistreams = false))
        selectionMemory.remember()
    }

    fun setAudioFormat(value: AudioOutputFormat) {
        settings.setAudioFormat(value)
        previousMediaSettings = previousMediaSettings.copy(audioFormat = value)
        savedStateHandle[DownloaderSavedState.AUDIO_FORMAT] = value.name
        mutableState.value = mutableState.value.copy(audioFormat = value)
        selectionMemory.remember()
    }

    fun setEmbedMetadata(enabled: Boolean) {
        settings.setEmbedMetadata(enabled)
        previousMediaSettings = previousMediaSettings.copy(embedMetadata = enabled)
        savedStateHandle[DownloaderSavedState.EMBED_METADATA] = enabled
        mutableState.value = mutableState.value.copy(embedMetadata = enabled)
        selectionMemory.remember()
    }

    fun setEmbedThumbnail(enabled: Boolean) {
        settings.setEmbedThumbnail(enabled)
        previousMediaSettings = previousMediaSettings.copy(embedThumbnail = enabled)
        savedStateHandle[DownloaderSavedState.EMBED_THUMBNAIL] = enabled
        mutableState.value = mutableState.value.copy(embedThumbnail = enabled)
        selectionMemory.remember()
    }

    fun setCropArtwork(enabled: Boolean) {
        settings.setCropArtwork(enabled)
        previousMediaSettings = previousMediaSettings.copy(cropArtwork = enabled)
        savedStateHandle[DownloaderSavedState.CROP_ARTWORK] = enabled
        mutableState.value = mutableState.value.copy(cropArtwork = enabled)
        selectionMemory.remember()
    }

    fun setPreserveOriginalMetadata(enabled: Boolean) {
        settings.setPreserveOriginalMetadata(enabled)
        previousMediaSettings = previousMediaSettings.copy(preserveOriginalMetadata = enabled)
        savedStateHandle[DownloaderSavedState.ORIGINAL_METADATA] = enabled
        mutableState.value = mutableState.value.copy(preserveOriginalMetadata = enabled)
        selectionMemory.remember()
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
        recordHistory: Boolean = mutableState.value.advancedOptions.recordHistory): Unit = withContext(ioDispatcher) {
        if (!recordHistory) return@withContext
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
                isDownloaded = false,
                title = media?.title, author = media?.artist ?: media?.username, sourceIdentity = media?.sourceIdentity
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
