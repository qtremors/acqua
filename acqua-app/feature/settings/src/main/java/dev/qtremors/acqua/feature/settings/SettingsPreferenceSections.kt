package dev.qtremors.acqua.feature.settings

import dev.qtremors.acqua.ui.components.ExpressiveTextField
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.VideoContainer
import dev.qtremors.acqua.ui.components.SettingsCardContainer
import dev.qtremors.acqua.ui.components.SettingsChoice
import dev.qtremors.acqua.ui.components.SettingsChoicePair
import dev.qtremors.acqua.ui.components.SettingsSection
import dev.qtremors.acqua.ui.components.SettingsSwitchRow
import dev.qtremors.acqua.ui.settings.AccentColorSelector
import dev.qtremors.acqua.ui.settings.ThemeModeSelector
import dev.qtremors.acqua.ui.theme.ThemePreset
import dev.qtremors.acqua.ui.theme.ThemeState

internal fun LazyListScope.settingsPreferenceItems(
    state: SettingsUiState, viewModel: SettingsViewModel, themeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit
) {
    item(key = "appearance") {
        SettingsSection(stringResource(R.string.appearance)) {
            SettingsCardContainer(index = 0, count = 5) {
                ThemeModeSelector(themeState.themeMode) { onThemeChange(themeState.copy(themeMode = it)) }
                AccentColorSelector(themeState.accentColor) { onThemeChange(themeState.copy(accentColor = it, themePreset = ThemePreset.NONE)) }
            }
            SettingsSwitchRow(stringResource(R.string.harmonize_colors), themeState.harmonizeColors,
                { onThemeChange(themeState.copy(harmonizeColors = it)) }, index = 1, count = 5,
                description = stringResource(R.string.harmonize_colors_description), leadingIcon = Icons.Filled.Palette)
            SettingsSwitchRow(stringResource(R.string.vibrations), themeState.vibrationsEnabled,
                { onThemeChange(themeState.copy(vibrationsEnabled = it)) }, index = 2, count = 5,
                description = stringResource(R.string.vibrations_description), leadingIcon = Icons.Filled.Vibration)
            HighContrastSetting(themeState.highContrast, count = 5) { onThemeChange(themeState.copy(highContrast = it)) }
            LanguageSetting()
        }
    }
    item(key = "filenames") {
        SettingsSection(stringResource(R.string.downloads_and_filenames)) {
            SettingsCardContainer(index = 0, count = 5) {
                ExpressiveTextField(state.baseFolder, viewModel::setBaseFolder,
                    label = { Text(stringResource(R.string.downloads_subfolder)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.Folder, null) })
            }
            SettingsSwitchRow(stringResource(R.string.categorize_media), state.categorizeMedia, viewModel::setCategorizeMedia,
                index = 1, count = 5, description = stringResource(R.string.settings_categories_summary), leadingIcon = Icons.Filled.FolderSpecial)
            DownloadFoldersSection(index = 2, count = 5)
            FilenameFormatsSetting(state.filenamePattern, state.audioFilenamePattern, viewModel::setFilenamePattern,
                viewModel::setAudioFilenamePattern, index = 3, count = 5)
            ConcurrentDownloadsSetting(state.maximumConcurrentDownloads, index = 4, count = 5, onChange = viewModel::setMaximumConcurrentDownloads)
        }
    }
    item(key = "video-defaults") {
        SettingsSection(stringResource(R.string.settings_video_defaults)) {
            SettingsChoicePair(
                SettingsChoice(stringResource(R.string.quality),
                    state.videoQualities.map { if (it == 0) stringResource(R.string.best) else "${it}p" },
                    state.videoQualities.indexOf(state.maximumVideoHeight), { viewModel.setMaximumVideoHeight(state.videoQualities[it]) }),
                SettingsChoice(stringResource(R.string.download_container), VideoContainer.entries.map {
                    if (it == VideoContainer.AUTO) stringResource(R.string.format_automatic) else it.name
                }, VideoContainer.entries.indexOf(state.videoContainer), { viewModel.setVideoContainer(VideoContainer.entries[it]) })
            )
        }
    }
    item(key = "audio-defaults") {
        SettingsSection(stringResource(R.string.settings_audio_defaults)) {
            val bitrates = (listOf(0, 320, 256, 192, 128, 96, 64) + state.audioBitrateKbps).distinct()
            SettingsChoicePair(
                SettingsChoice(stringResource(R.string.download_format), AudioOutputFormat.entries.map {
                    if (it == AudioOutputFormat.ORIGINAL) stringResource(R.string.original_audio) else it.name
                }, AudioOutputFormat.entries.indexOf(state.audioFormat), { viewModel.setAudioFormat(AudioOutputFormat.entries[it]) }),
                SettingsChoice(stringResource(R.string.quality), bitrates.map { if (it == 0) stringResource(R.string.best) else "$it kbps" },
                    if (state.audioFormat == AudioOutputFormat.ORIGINAL) 0 else bitrates.indexOf(state.audioBitrateKbps),
                    { viewModel.setAudioBitrate(bitrates[it]) }, enabled = state.audioFormat != AudioOutputFormat.ORIGINAL,
                    hint = stringResource(R.string.settings_audio_quality_hint))
            )
        }
    }
    item(key = "metadata-defaults") {
        SettingsSection(stringResource(R.string.settings_metadata)) {
            SettingsSwitchRow(stringResource(R.string.embed_metadata), state.embedMetadata, viewModel::setEmbedMetadata,
                index = 0, count = 4, description = stringResource(R.string.settings_metadata_summary))
            SettingsSwitchRow(stringResource(R.string.embed_thumbnail), state.embedThumbnail, viewModel::setEmbedThumbnail,
                index = 1, count = 4, description = stringResource(R.string.settings_artwork_summary))
            SettingsSwitchRow(stringResource(R.string.crop_artwork), state.cropArtwork, viewModel::setCropArtwork,
                index = 2, count = 4, enabled = state.embedThumbnail)
            SettingsSwitchRow(stringResource(R.string.original_metadata), state.preserveOriginalMetadata, viewModel::setPreserveOriginalMetadata,
                index = 3, count = 4, description = stringResource(R.string.settings_dates_summary))
        }
    }
}
