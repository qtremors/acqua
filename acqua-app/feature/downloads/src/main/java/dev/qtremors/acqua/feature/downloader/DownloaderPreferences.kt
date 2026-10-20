package dev.qtremors.acqua.feature.downloader

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.acqua.data.settings.SettingsStore
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.CaptionMode
import dev.qtremors.acqua.downloader.CaptionFormat
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.downloader.VideoContainer
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class DownloaderPreferences(
    private val mutableState: MutableStateFlow<DownloaderUiState>,
    private val settings: SettingsStore,
    private val savedStateHandle: SavedStateHandle,
    private val selectionMemory: DownloadSelectionMemory
) {
    private var previousMediaSettings = settings.mediaProcessingSettings()
    private var previousDownloadSettings = settings.downloadSettings()
    private var previousDownloadDefaults = settings.downloadDefaults()
    private val mutableDownloadDefaults = MutableStateFlow(previousDownloadDefaults)
    val downloadDefaults = mutableDownloadDefaults.asStateFlow()

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
        val defaults = settings.downloadDefaults()
        settings.setDownloadDefaults(options.copy(videoContainer = defaults.videoContainer, audioBitrateKbps = defaults.audioBitrateKbps))
        previousDownloadDefaults = settings.downloadDefaults()
        mutableDownloadDefaults.value = previousDownloadDefaults
        val embedding = options.captionMode == CaptionMode.EMBED || options.captionMode == CaptionMode.BOTH
        setAdvancedOptions(if (options.videoContainer == VideoContainer.WEBM && embedding) {
            options.copy(captionFormat = CaptionFormat.VTT)
        } else options)
    }

    fun updateDownloadDefaults(update: (YtDlpDownloadOptions) -> YtDlpDownloadOptions) {
        settings.setDownloadDefaults(update(settings.downloadDefaults()))
        refreshSettingsDefaults()
    }

    fun refreshSettingsDefaults() {
        val media = settings.mediaProcessingSettings()
        val download = settings.downloadSettings()
        val defaults = settings.downloadDefaults()
        mutableDownloadDefaults.value = defaults
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
            val container = if (defaults.videoContainer != previousDownloadDefaults.videoContainer) defaults.videoContainer else state.advancedOptions.videoContainer
            val options = state.advancedOptions.copy(captionMode = captions,
                videoContainer = container,
                audioBitrateKbps = if (defaults.audioBitrateKbps != previousDownloadDefaults.audioBitrateKbps) defaults.audioBitrateKbps else state.advancedOptions.audioBitrateKbps,
                captionFormat = if (container == VideoContainer.WEBM && embedding) CaptionFormat.VTT else defaults.captionFormat,
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
}
