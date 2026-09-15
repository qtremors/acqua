@file:OptIn(ExperimentalLayoutApi::class)

package dev.qtremors.acqua.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.BuildConfig
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.data.backup.PreferencesBackupManager
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.FilenameFormatter
import dev.qtremors.acqua.downloader.YtDlpFailure
import dev.qtremors.acqua.downloader.YtDlpUpdateStatus
import dev.qtremors.acqua.appinfo.AboutBuildInfo
import dev.qtremors.acqua.appinfo.AboutExternalLink
import dev.qtremors.acqua.appinfo.deviceDescription
import dev.qtremors.acqua.platform.HapticSignal
import dev.qtremors.acqua.platform.WebViewUpdateManager
import dev.qtremors.acqua.platform.performHaptic
import dev.qtremors.acqua.ui.components.SettingsCardContainer
import dev.qtremors.acqua.ui.components.SettingsSection
import dev.qtremors.acqua.ui.components.SettingsSwitchRow
import dev.qtremors.acqua.ui.settings.AccentColorSelector
import dev.qtremors.acqua.ui.settings.ThemeModeSelector
import dev.qtremors.acqua.ui.theme.ThemePreset
import dev.qtremors.acqua.ui.theme.ThemeState
import dev.qtremors.acqua.ui.theme.bounceClickable
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date


@Composable
internal fun SettingsPreferenceSections(
    settingsState: SettingsUiState,
    viewModel: SettingsViewModel,
    themeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    contentAlpha: Float,
    contentOffset: Dp
) {
    val colors = MaterialTheme.colorScheme
    // 3. Section: Appearance
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = contentAlpha
            translationY = contentOffset.toPx()
        }
    ) {
        SettingsSection(title = stringResource(R.string.appearance)) {
            SettingsCardContainer(index = 0, count = 3) {
                ThemeModeSelector(
                    currentMode = themeState.themeMode,
                    onModeSelected = { newMode ->
                        onThemeChange(themeState.copy(themeMode = newMode))
                    }
                )
                AccentColorSelector(
                    currentAccent = themeState.accentColor,
                    onAccentSelected = { newAccent ->
                        onThemeChange(themeState.copy(accentColor = newAccent, themePreset = ThemePreset.NONE))
                    }
                )
            }
            SettingsSwitchRow(
                index = 1,
                count = 3,
                title = stringResource(R.string.harmonize_colors),
                description = stringResource(R.string.harmonize_colors_description),
                checked = themeState.harmonizeColors,
                leadingIcon = Icons.Filled.Palette,
                onCheckedChange = { harmonize ->
                    onThemeChange(themeState.copy(harmonizeColors = harmonize))
                }
            )
            SettingsSwitchRow(
                index = 2,
                count = 3,
                title = stringResource(R.string.vibrations),
                description = stringResource(R.string.vibrations_description),
                checked = themeState.vibrationsEnabled,
                leadingIcon = Icons.Filled.Vibration,
                onCheckedChange = { vibrations ->
                    onThemeChange(themeState.copy(vibrationsEnabled = vibrations))
                }
            )
        }
    }

    // 4. Section: Downloads and Storage
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = contentAlpha
            translationY = contentOffset.toPx()
        }
    ) {
        SettingsSection(title = stringResource(R.string.downloads_and_filenames)) {
            SettingsCardContainer(index = 0, count = 4) {
                OutlinedTextField(
                    value = settingsState.baseFolder,
                    onValueChange = viewModel::setBaseFolder,
                    label = { Text(stringResource(R.string.downloads_subfolder)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = {
                        Icon(Icons.Filled.Folder, null, tint = colors.primary)
                    }
                )
            }
            SettingsSwitchRow(
                index = 1,
                count = 4,
                title = stringResource(R.string.categorize_media),
                description = stringResource(R.string.category_explanation),
                checked = settingsState.categorizeMedia,
                leadingIcon = Icons.Filled.FolderSpecial,
                onCheckedChange = viewModel::setCategorizeMedia
            )
            SettingsCardContainer(index = 2, count = 4) {
                OutlinedTextField(
                    value = settingsState.filenamePattern,
                    onValueChange = viewModel::setFilenamePattern,
                    label = { Text(stringResource(R.string.filename_pattern)) },
                    placeholder = { Text(FilenameFormatter.DEFAULT_PATTERN) },
                    supportingText = { Text(stringResource(R.string.filename_variables_guidance)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                Text(
                    stringResource(R.string.filename_preview, FilenameFormatter.preview(settingsState.filenamePattern)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilenameFormatter.variables.forEach { variable ->
                        val selected = settingsState.filenamePattern.contains(variable)
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.toggleFilenameVariable(variable) },
                            label = { Text(variable) },
                            leadingIcon = if (selected) {{
                                Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                            }} else null
                        )
                    }
                }
                OutlinedButton(
                    onClick = viewModel::resetFilenamePattern,
                    enabled = settingsState.filenamePattern != FilenameFormatter.DEFAULT_PATTERN,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.reset_filename_pattern))
                }
            }
            SettingsCardContainer(index = 3, count = 4) {
                OutlinedTextField(
                    value = settingsState.audioFilenamePattern,
                    onValueChange = viewModel::setAudioFilenamePattern,
                    label = { Text(stringResource(R.string.audio_filename_pattern)) },
                    placeholder = { Text(FilenameFormatter.DEFAULT_AUDIO_PATTERN) },
                    supportingText = { Text(stringResource(R.string.filename_variables_guidance)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                Text(
                    stringResource(R.string.audio_filename_preview, FilenameFormatter.previewAudio(settingsState.audioFilenamePattern)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilenameFormatter.audioVariables.forEach { variable ->
                        val selected = settingsState.audioFilenamePattern.contains(variable)
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.toggleAudioFilenameVariable(variable) },
                            label = { Text(variable) },
                            leadingIcon = if (selected) {{
                                Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                            }} else null
                        )
                    }
                }
                OutlinedButton(
                    onClick = viewModel::resetAudioFilenamePattern,
                    enabled = settingsState.audioFilenamePattern != FilenameFormatter.DEFAULT_AUDIO_PATTERN,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.reset_audio_filename_pattern))
                }
            }
        }
    }

    // 5. Section: Media Processing
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = contentAlpha
            translationY = contentOffset.toPx()
        }
    ) {
        SettingsSection(title = stringResource(R.string.media_processing)) {
            SettingsCardContainer(index = 0, count = 4) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.HighQuality, null, tint = colors.primary)
                    Text(stringResource(R.string.default_video_quality), fontWeight = FontWeight.Bold)
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0, 2160, 1440, 1080, 720, 480).forEach { height ->
                        FilterChip(
                            selected = settingsState.maximumVideoHeight == height,
                            onClick = { viewModel.setMaximumVideoHeight(height) },
                            label = { Text(if (height == 0) stringResource(R.string.best) else "${height}p") }
                        )
                    }
                }
            }
            SettingsCardContainer(index = 1, count = 4) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Audiotrack, null, tint = colors.primary)
                    Text(stringResource(R.string.default_audio_format), fontWeight = FontWeight.Bold)
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AudioOutputFormat.entries.forEach { format ->
                        FilterChip(
                            selected = settingsState.audioFormat == format,
                            onClick = { viewModel.setAudioFormat(format) },
                            label = {
                                Text(
                                    when (format) {
                                        AudioOutputFormat.ORIGINAL -> stringResource(R.string.original_audio)
                                        AudioOutputFormat.M4A -> "M4A"
                                        AudioOutputFormat.MP3 -> "MP3"
                                    }
                                )
                            }
                        )
                    }
                }
            }
            SettingsSwitchRow(
                index = 2,
                count = 4,
                title = stringResource(R.string.embed_metadata),
                description = stringResource(R.string.embed_metadata_explanation),
                checked = settingsState.embedMetadata,
                leadingIcon = Icons.Filled.Movie,
                onCheckedChange = viewModel::setEmbedMetadata
            )
            SettingsSwitchRow(
                index = 3,
                count = 4,
                title = stringResource(R.string.embed_thumbnail),
                description = stringResource(R.string.embed_thumbnail_explanation),
                checked = settingsState.embedThumbnail,
                leadingIcon = Icons.Filled.Image,
                onCheckedChange = viewModel::setEmbedThumbnail
            )
        }
    }
}
