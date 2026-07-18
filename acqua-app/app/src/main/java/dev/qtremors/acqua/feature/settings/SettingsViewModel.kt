package dev.qtremors.acqua.feature.settings

import androidx.lifecycle.ViewModel
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.downloader.FilenameFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(
    val baseFolder: String = "",
    val categorizeMedia: Boolean = false,
    val filenamePattern: String = FilenameFormatter.DEFAULT_PATTERN
)

class SettingsViewModel(private val repository: AppSettingsRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(
        repository.downloadSettings().let {
            SettingsUiState(it.baseFolder, it.categorizeMedia, it.filenamePattern)
        }
    )
    val state = mutableState.asStateFlow()

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
}
