package dev.qtremors.acqua.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.FilenameFormatter
import dev.qtremors.acqua.downloader.YtDlpMaintenance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val baseFolder: String = "",
    val categorizeMedia: Boolean = false,
    val filenamePattern: String = FilenameFormatter.DEFAULT_PATTERN,
    val maximumVideoHeight: Int = 0,
    val audioFormat: AudioOutputFormat = AudioOutputFormat.ORIGINAL,
    val embedMetadata: Boolean = true,
    val embedThumbnail: Boolean = true,
    val autoUpdateYtDlp: Boolean = true,
    val ytDlpVersion: String? = null,
    val isUpdatingYtDlp: Boolean = false,
    val ytDlpUpdateError: String? = null
)

class SettingsViewModel(
    private val repository: AppSettingsRepository,
    private val maintenance: YtDlpMaintenance
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        repository.downloadSettings().let { downloads ->
            repository.mediaProcessingSettings().let { media ->
                SettingsUiState(
                    baseFolder = downloads.baseFolder,
                    categorizeMedia = downloads.categorizeMedia,
                    filenamePattern = downloads.filenamePattern,
                    maximumVideoHeight = media.maximumVideoHeight,
                    audioFormat = media.audioFormat,
                    embedMetadata = media.embedMetadata,
                    embedThumbnail = media.embedThumbnail,
                    autoUpdateYtDlp = media.autoUpdateYtDlp
                )
            }
        }
    )
    val state = mutableState.asStateFlow()

    init {
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

    fun updateYtDlp(force: Boolean = true) {
        if (mutableState.value.isUpdatingYtDlp) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isUpdatingYtDlp = true, ytDlpUpdateError = null)
            runCatching { maintenance.update(force) }
                .onSuccess { version ->
                    mutableState.value = mutableState.value.copy(
                        isUpdatingYtDlp = false,
                        ytDlpVersion = version
                    )
                }
                .onFailure { error ->
                    mutableState.value = mutableState.value.copy(
                        isUpdatingYtDlp = false,
                        ytDlpUpdateError = error.message ?: "yt-dlp update failed"
                    )
                }
        }
    }
}
