package dev.qtremors.acqua.feature.downloader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.qtremors.acqua.data.history.HistoryEntry
import dev.qtremors.acqua.data.history.HistoryRepository
import dev.qtremors.acqua.domain.MediaResolutionService
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.platform.storage.MediaStorage
import dev.qtremors.acqua.resolver.instagram.AgeGateException
import dev.qtremors.acqua.resolver.instagram.ExpiredSessionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val savedItemIndex: Int? = null
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
    private val storage: MediaStorage
) : ViewModel() {
    private val mutableState = MutableStateFlow(DownloaderUiState())
    val state = mutableState.asStateFlow()
    private val mutableEvents = MutableSharedFlow<DownloaderEvent>(extraBufferCapacity = 4)
    val events = mutableEvents.asSharedFlow()
    private var resolutionJob: Job? = null
    private var lastAutomaticRequest: Triple<String, Boolean, Int>? = null

    fun setInitialUrl(url: String) {
        if (mutableState.value.url.isEmpty() && url.isNotBlank()) updateUrl(url)
    }

    fun updateUrl(value: String) {
        resolutionJob?.cancel()
        mutableState.value = DownloaderUiState(
            url = value,
            validity = when {
                value.isBlank() -> LinkValidity.EMPTY
                WebLink.normalize(value) != null -> LinkValidity.VALID
                else -> LinkValidity.INVALID
            }
        )
    }

    fun dismissError() {
        mutableState.value = mutableState.value.copy(error = null, showBrowserAction = false)
    }

    fun resolve(
        browserSessionsEnabled: Boolean,
        requestRevision: Int,
        browserResolver: suspend (String) -> List<ResolvedMedia>
    ) {
        val snapshot = mutableState.value
        if (snapshot.validity != LinkValidity.VALID) return
        val normalizedUrl = WebLink.normalize(snapshot.url) ?: return
        val requestKey = Triple(normalizedUrl, browserSessionsEnabled, requestRevision)
        if (requestKey == lastAutomaticRequest) return
        lastAutomaticRequest = requestKey
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
                val media = resolution.resolve(url, browserSessionsEnabled, browserResolver)
                mutableState.value = mutableState.value.copy(isResolving = false, media = media)
                mutableEvents.emit(DownloaderEvent.ResolutionComplete)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showResolutionError(error)
            }
        }
    }

    fun downloadAll(
        browserSessionsEnabled: Boolean,
        browserResolver: suspend (String) -> List<ResolvedMedia>
    ) {
        if (mutableState.value.isSaving) return
        viewModelScope.launch {
            val url = WebLink.normalize(mutableState.value.url) ?: return@launch
            mutableState.value = mutableState.value.copy(error = null, showBrowserAction = false)
            val items = mutableState.value.media ?: try {
                mutableState.value = mutableState.value.copy(isResolving = true)
                recordLink(url)
                resolution.resolve(url, browserSessionsEnabled, browserResolver).also {
                    mutableState.value = mutableState.value.copy(isResolving = false, media = it)
                }
            } catch (error: Exception) {
                showResolutionError(error)
                return@launch
            }

            mutableState.value = mutableState.value.copy(isSaving = true, savingIndex = 0)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    items.forEachIndexed { index, item ->
                        mutableState.value = mutableState.value.copy(savingIndex = index + 1)
                        storage.save(item, index, url)
                    }
                }
            }
            if (result.isFailure) {
                mutableState.value = mutableState.value.copy(
                    isSaving = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to write media"
                )
                return@launch
            }
            mutableState.value = mutableState.value.copy(isSaving = false, saved = true)
            mutableEvents.emit(DownloaderEvent.DownloadComplete)
            delay(2500)
            mutableState.value = mutableState.value.copy(saved = false, media = null)
        }
    }

    fun downloadOne(item: ResolvedMedia, index: Int, width: Int, height: Int) {
        if (mutableState.value.savingItemIndex != null) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(savingItemIndex = index, savedItemIndex = null)
            val sourceUrl = WebLink.normalize(mutableState.value.url).orEmpty()
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    storage.save(item.copy(width = width, height = height), index, sourceUrl)
                }
            }
            result.onSuccess {
                mutableState.value = mutableState.value.copy(savingItemIndex = null, savedItemIndex = index)
                mutableEvents.emit(DownloaderEvent.ItemSaved)
                delay(2000)
                mutableState.value = mutableState.value.copy(savedItemIndex = null)
            }
                .onFailure {
                    mutableState.value = mutableState.value.copy(savingItemIndex = null)
                    mutableEvents.emit(
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
}
