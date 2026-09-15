package dev.qtremors.acqua.feature.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.qtremors.acqua.data.settings.SettingsStore
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.FilenameFormatter
import dev.qtremors.acqua.downloader.YtDlpMaintenanceGateway
import dev.qtremors.acqua.downloader.YtDlpFailure
import dev.qtremors.acqua.downloader.SafeDiagnostics
import dev.qtremors.acqua.downloader.YtDlpUpdateStatus
import dev.qtremors.acqua.downloader.toYtDlpFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val baseFolder: String = "",
    val categorizeMedia: Boolean = false,
    val filenamePattern: String = FilenameFormatter.DEFAULT_PATTERN,
    val audioFilenamePattern: String = FilenameFormatter.DEFAULT_AUDIO_PATTERN,
    val maximumVideoHeight: Int = 0,
    val audioFormat: AudioOutputFormat = AudioOutputFormat.ORIGINAL,
    val embedMetadata: Boolean = true,
    val embedThumbnail: Boolean = true,
    val autoUpdateYtDlp: Boolean = true,
    val screenProtectionEnabled: Boolean = false,
    val ytDlpVersion: String? = null,
    val lastYtDlpUpdate: Long = 0L,
    val ytDlpUpdateStatus: YtDlpUpdateStatus? = null,
    val isUpdatingYtDlp: Boolean = false,
    val ytDlpUpdateError: YtDlpFailure? = null
)

class SettingsViewModel(
    private val repository: SettingsStore,
    private val maintenance: YtDlpMaintenanceGateway,
    private val reportFailure: (YtDlpFailure, Throwable) -> Unit = { failure, error ->
        Log.e(TAG, "yt-dlp update failed (${failure.name})", SafeDiagnostics.redact(error))
    }
) : ViewModel() {
    private val mutableState = MutableStateFlow(repository.readSettingsUiState())
    val state = mutableState.asStateFlow()
    private var hasLoadedYtDlpStatus = false

    init {
        viewModelScope.launch {
            repository.screenProtectionEnabledFlow().collect { enabled ->
                mutableState.value = mutableState.value.copy(screenProtectionEnabled = enabled)
            }
        }
    }

    fun loadYtDlpStatus() {
        if (hasLoadedYtDlpStatus) return
        hasLoadedYtDlpStatus = true
        updateYtDlp(force = false)
    }

    fun setBaseFolder(value: String) {
        repository.setBaseFolder(value)
        mutableState.value = mutableState.value.copy(baseFolder = value)
    }

    fun setCategorizeMedia(enabled: Boolean) {
        repository.setCategorizeMedia(enabled)
        mutableState.value = mutableState.value.copy(categorizeMedia = enabled)
    }

    fun setFilenamePattern(value: String) {
        repository.setFilenamePattern(value)
        mutableState.value = mutableState.value.copy(filenamePattern = value)
    }

    fun toggleFilenameVariable(variable: String) {
        setFilenamePattern(FilenameFormatter.toggleVariable(mutableState.value.filenamePattern, variable))
    }

    fun resetFilenamePattern() = setFilenamePattern(FilenameFormatter.DEFAULT_PATTERN)

    fun setAudioFilenamePattern(value: String) {
        repository.setAudioFilenamePattern(value)
        mutableState.value = mutableState.value.copy(audioFilenamePattern = value)
    }

    fun toggleAudioFilenameVariable(variable: String) {
        setAudioFilenamePattern(FilenameFormatter.toggleAudioVariable(mutableState.value.audioFilenamePattern, variable))
    }

    fun resetAudioFilenamePattern() = setAudioFilenamePattern(FilenameFormatter.DEFAULT_AUDIO_PATTERN)

    fun setMaximumVideoHeight(value: Int) {
        repository.setMaximumVideoHeight(value)
        mutableState.value = mutableState.value.copy(maximumVideoHeight = value)
    }

    fun setAudioFormat(value: AudioOutputFormat) {
        repository.setAudioFormat(value)
        mutableState.value = mutableState.value.copy(audioFormat = value)
    }

    fun setEmbedMetadata(enabled: Boolean) {
        repository.setEmbedMetadata(enabled)
        mutableState.value = mutableState.value.copy(embedMetadata = enabled)
    }

    fun setEmbedThumbnail(enabled: Boolean) {
        repository.setEmbedThumbnail(enabled)
        mutableState.value = mutableState.value.copy(embedThumbnail = enabled)
    }

    fun setAutoUpdateYtDlp(enabled: Boolean) {
        repository.setAutoUpdateYtDlp(enabled)
        mutableState.value = mutableState.value.copy(autoUpdateYtDlp = enabled)
    }

    fun setScreenProtectionEnabled(enabled: Boolean) {
        repository.setScreenProtectionEnabled(enabled)
        mutableState.value = mutableState.value.copy(screenProtectionEnabled = enabled)
    }

    fun reload() {
        mutableState.value = repository.readSettingsUiState(mutableState.value)
    }

    fun updateYtDlp(force: Boolean = true) {
        if (mutableState.value.isUpdatingYtDlp) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                isUpdatingYtDlp = true,
                ytDlpUpdateError = null,
                ytDlpUpdateStatus = null
            )
            try {
                val result = maintenance.update(force)
                mutableState.value = mutableState.value.copy(
                    isUpdatingYtDlp = false,
                    ytDlpVersion = result.version,
                    lastYtDlpUpdate = result.lastCheckedAt,
                    ytDlpUpdateStatus = result.updateStatus
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (error is VirtualMachineError || error is ThreadDeath) throw error
                val failure = error.toYtDlpFailure()
                reportFailure(failure, error)
                mutableState.value = mutableState.value.copy(
                    isUpdatingYtDlp = false,
                    ytDlpUpdateError = failure
                )
            }
        }
    }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}

private fun SettingsStore.readSettingsUiState(
    previous: SettingsUiState = SettingsUiState()
): SettingsUiState {
    val downloads = downloadSettings()
    val media = mediaProcessingSettings()
    return previous.copy(
        baseFolder = downloads.baseFolder,
        categorizeMedia = downloads.categorizeMedia,
        filenamePattern = downloads.filenamePattern,
        audioFilenamePattern = downloads.audioFilenamePattern,
        maximumVideoHeight = media.maximumVideoHeight,
        audioFormat = media.audioFormat,
        embedMetadata = media.embedMetadata,
        embedThumbnail = media.embedThumbnail,
        autoUpdateYtDlp = media.autoUpdateYtDlp,
        screenProtectionEnabled = screenProtectionEnabled(),
        lastYtDlpUpdate = media.lastYtDlpUpdate
    )
}
