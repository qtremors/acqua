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
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.platform.storage.MediaStorage
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions
import dev.qtremors.acqua.downloader.YtDlpDownloadCoordinator
import dev.qtremors.acqua.downloader.YtDlpDownloadWorker
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

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
    val downloadEngine: DownloadEngine = DownloadEngine.ACQUA
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
    private var activeYtDlpWorkId: UUID? = null

    fun setInitialUrl(url: String) {
        if (mutableState.value.url.isEmpty() && url.isNotBlank()) updateUrl(url)
    }

    fun updateUrl(value: String) {
        ytDlpEngine.cancelAll()
        resolutionJob?.cancel()
        downloadJob?.cancel()
        itemDownloadJob?.cancel()
        activeYtDlpWorkId = null
        lastAutomaticRequest = null
        mutableState.value = defaultState(
            url = value,
            contentType = if (WebLink.isYouTubeMusicUrl(value)) DownloadContentType.AUDIO else DownloadContentType.VIDEO
        )
    }

    fun seedResolvedMedia(url: String, media: List<ResolvedMedia>) {
        updateUrl(url)
        if (media.isNotEmpty()) {
            mutableState.value = mutableState.value.copy(media = media, isResolving = false)
        }
    }

    fun setDownloadContentType(value: DownloadContentType) {
        mutableState.value = mutableState.value.copy(downloadContentType = value)
    }

    fun setDownloadEngine(value: DownloadEngine) {
        val snapshot = mutableState.value
        if (value == DownloadEngine.AUTO || value == snapshot.downloadEngine ||
            snapshot.isSaving || snapshot.savingItemIndex != null
        ) return
        ytDlpEngine.cancelAll()
        resolutionJob?.cancel()
        lastAutomaticRequest = null
        mutableState.value = snapshot.copy(
            downloadEngine = value,
            isResolving = false,
            error = null,
            media = null,
            saved = false,
            showBrowserAction = false,
            downloadProgress = 0f,
            downloadEtaSeconds = 0L
        )
    }

    fun setMaximumVideoHeight(value: Int) {
        mutableState.value = mutableState.value.copy(maximumVideoHeight = value.coerceAtLeast(0))
    }

    fun setAudioFormat(value: AudioOutputFormat) {
        mutableState.value = mutableState.value.copy(audioFormat = value)
    }

    fun setEmbedMetadata(enabled: Boolean) {
        mutableState.value = mutableState.value.copy(embedMetadata = enabled)
    }

    fun setEmbedThumbnail(enabled: Boolean) {
        mutableState.value = mutableState.value.copy(embedThumbnail = enabled)
    }

    fun cancelActiveDownload() {
        activeYtDlpWorkId?.let(ytDlpDownloads::cancel)
        activeYtDlpWorkId = null
        mutableState.value = mutableState.value.copy(
            isSaving = false,
            downloadProgress = 0f,
            downloadEtaSeconds = 0L
        )
    }

    fun dismissError() {
        mutableState.value = mutableState.value.copy(error = null, showBrowserAction = false)
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
                saved = false,
                showBrowserAction = false
            )
            try {
                val media = resolution.resolve(
                    url,
                    browserSessionsEnabled,
                    false,
                    snapshot.downloadEngine,
                    browserResolver
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
            mutableState.value = mutableState.value.copy(error = null, showBrowserAction = false)
            val items = mutableState.value.media ?: try {
                mutableState.value = mutableState.value.copy(isResolving = true)
                recordLink(url)
                resolution.resolve(
                    url,
                    browserSessionsEnabled,
                    false,
                    mutableState.value.downloadEngine,
                    browserResolver
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
            if (items.size == 1 && items.single().backend == MediaBackend.YT_DLP) {
                observeYtDlpDownload(enqueueYtDlpDownload(items.single(), url))
                return@launch
            }
            val result = try {
                withContext(Dispatchers.IO) {
                    items.forEachIndexed { index, item ->
                        mutableState.value = mutableState.value.copy(savingIndex = index + 1)
                        storage.save(item, index, url)
                    }
                }
                Result.success(Unit)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
            if (result.isFailure) {
                mutableState.value = mutableState.value.copy(
                    isSaving = false,
                    downloadProgress = 0f,
                    error = result.exceptionOrNull()?.message ?: "Failed to write media"
                )
                return@launch
            }
            mutableState.value = mutableState.value.copy(
                isSaving = false,
                saved = true,
                downloadProgress = 100f,
                downloadEtaSeconds = 0L
            )
            mutableEvents.send(DownloaderEvent.DownloadComplete)
        }
    }

    fun downloadOne(item: ResolvedMedia, index: Int, width: Int, height: Int) {
        if (mutableState.value.isSaving || mutableState.value.savingItemIndex != null) return
        itemDownloadJob?.cancel()
        itemDownloadJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(savingItemIndex = index, savedItemIndex = null)
            val sourceUrl = WebLink.normalize(mutableState.value.url).orEmpty()
            val result = try {
                withContext(Dispatchers.IO) {
                    val mediaWithDimensions = if (item.isVideo) item else item.copy(width = width, height = height)
                    storage.save(mediaWithDimensions, index, sourceUrl)
                }
                Result.success(Unit)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
            result.onSuccess {
                mutableState.value = mutableState.value.copy(savingItemIndex = null, savedItemIndex = index)
                mutableEvents.send(DownloaderEvent.ItemSaved)
                delay(2000)
                mutableState.value = mutableState.value.copy(savedItemIndex = null)
            }
                .onFailure {
                    mutableState.value = mutableState.value.copy(savingItemIndex = null)
                    mutableEvents.send(
                        DownloaderEvent.ItemSaveFailed(it.message ?: "Failed to save")
                    )
                }
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
        mutableState.value = mutableState.value.copy(
            isResolving = false,
            error = error.message ?: "An unexpected error occurred",
            showBrowserAction = error is AgeGateException || error is ExpiredSessionException ||
                error !is IllegalArgumentException
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
            activeYtDlpWorkId = it
        }
    }

    private suspend fun observeYtDlpDownload(workId: UUID) {
        val final = ytDlpDownloads.observe(workId)
            .onEach { info ->
                if (!info.state.isFinished) {
                    mutableState.value = mutableState.value.copy(
                        isSaving = true,
                        downloadProgress = info.progress.getFloat(YtDlpDownloadWorker.KEY_PROGRESS, 0f),
                        downloadEtaSeconds = info.progress.getLong(YtDlpDownloadWorker.KEY_ETA_SECONDS, 0L)
                    )
                }
            }
            .first { it.state.isFinished }
        if (activeYtDlpWorkId == workId) activeYtDlpWorkId = null
        when (final.state) {
            WorkInfo.State.SUCCEEDED -> {
                mutableState.value = mutableState.value.copy(
                    isSaving = false,
                    saved = true,
                    downloadProgress = 100f,
                    downloadEtaSeconds = 0L
                )
                mutableEvents.send(DownloaderEvent.DownloadComplete)
            }
            WorkInfo.State.FAILED -> mutableState.value = mutableState.value.copy(
                isSaving = false,
                downloadProgress = 0f,
                downloadEtaSeconds = 0L,
                error = final.outputData.getString(YtDlpDownloadWorker.KEY_ERROR)
                    ?: "The background download failed."
            )
            WorkInfo.State.CANCELLED -> mutableState.value = mutableState.value.copy(
                isSaving = false,
                downloadProgress = 0f,
                downloadEtaSeconds = 0L
            )
            else -> Unit
        }
    }

    private fun defaultState(
        url: String = "",
        contentType: DownloadContentType = DownloadContentType.VIDEO
    ): DownloaderUiState {
        val preferences = settings.mediaProcessingSettings()
        return DownloaderUiState(
            url = url,
            validity = when {
                url.isBlank() -> LinkValidity.EMPTY
                WebLink.normalize(url) != null -> LinkValidity.VALID
                else -> LinkValidity.INVALID
            },
            downloadContentType = contentType,
            downloadEngine = if (WebLink.isYouTubeUrl(url)) DownloadEngine.YT_DLP else DownloadEngine.ACQUA,
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
