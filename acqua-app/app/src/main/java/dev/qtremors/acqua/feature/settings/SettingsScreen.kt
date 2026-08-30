package dev.qtremors.acqua.feature.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.R
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.FilenameFormatter
import dev.qtremors.acqua.downloader.YtDlpFailure
import dev.qtremors.acqua.downloader.YtDlpUpdateStatus
import dev.qtremors.acqua.platform.WebViewUpdateManager
import dev.qtremors.acqua.ui.components.SettingsActionRow
import dev.qtremors.acqua.ui.components.SettingsCardContainer
import dev.qtremors.acqua.ui.components.SettingsSection
import dev.qtremors.acqua.ui.components.SettingsSwitchRow
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val webViewProvider = remember { WebViewUpdateManager.currentProvider(context) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text(
                stringResource(R.string.settings),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        // Section 1: Downloads and Storage
        item {
            SettingsSection(title = stringResource(R.string.downloads_and_filenames)) {
                SettingsCardContainer(index = 0, count = 3) {
                    OutlinedTextField(
                        value = state.baseFolder,
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
                    count = 3,
                    title = stringResource(R.string.categorize_media),
                    description = stringResource(R.string.category_explanation),
                    checked = state.categorizeMedia,
                    leadingIcon = Icons.Filled.FolderSpecial,
                    onCheckedChange = viewModel::setCategorizeMedia
                )
                SettingsCardContainer(index = 2, count = 3) {
                    OutlinedTextField(
                        value = state.filenamePattern,
                        onValueChange = viewModel::setFilenamePattern,
                        label = { Text(stringResource(R.string.filename_pattern)) },
                        placeholder = { Text(FilenameFormatter.DEFAULT_PATTERN) },
                        supportingText = { Text(stringResource(R.string.filename_variables_guidance)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Text(
                        stringResource(R.string.filename_preview, FilenameFormatter.preview(state.filenamePattern)),
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
                            val selected = state.filenamePattern.contains(variable)
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
                        enabled = state.filenamePattern != FilenameFormatter.DEFAULT_PATTERN,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.reset_filename_pattern))
                    }
                }
            }
        }

        // Section 2: Media Processing
        item {
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
                                selected = state.maximumVideoHeight == height,
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AudioOutputFormat.entries.forEach { format ->
                            FilterChip(
                                selected = state.audioFormat == format,
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
                    checked = state.embedMetadata,
                    leadingIcon = Icons.Filled.Movie,
                    onCheckedChange = viewModel::setEmbedMetadata
                )
                SettingsSwitchRow(
                    index = 3,
                    count = 4,
                    title = stringResource(R.string.embed_thumbnail),
                    description = stringResource(R.string.embed_thumbnail_explanation),
                    checked = state.embedThumbnail,
                    leadingIcon = Icons.Filled.Image,
                    onCheckedChange = viewModel::setEmbedThumbnail
                )
            }
        }

        // Section 3: Engine & Updates
        item {
            SettingsSection(title = stringResource(R.string.engine_settings_title)) {
                SettingsSwitchRow(
                    index = 0,
                    count = 2,
                    title = stringResource(R.string.auto_update_ytdlp),
                    description = stringResource(R.string.auto_update_ytdlp_explanation),
                    checked = state.autoUpdateYtDlp,
                    leadingIcon = Icons.Filled.Sync,
                    onCheckedChange = viewModel::setAutoUpdateYtDlp
                )
                SettingsCardContainer(index = 1, count = 2) {
                    Text(
                        stringResource(R.string.ytdlp_version, state.ytDlpVersion ?: stringResource(R.string.unknown)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (state.lastYtDlpUpdate > 0L) {
                        val checkedAt = remember(state.lastYtDlpUpdate) {
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(Date(state.lastYtDlpUpdate))
                        }
                        Text(
                            stringResource(R.string.ytdlp_last_checked, checkedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.updateYtDlp() },
                        enabled = !state.isUpdatingYtDlp,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (state.isUpdatingYtDlp) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(if (state.isUpdatingYtDlp) R.string.updating_ytdlp else R.string.update_ytdlp))
                    }
                    state.ytDlpUpdateError?.let { failure ->
                        val message = when (failure) {
                            YtDlpFailure.RUNTIME -> R.string.ytdlp_error_runtime
                            YtDlpFailure.NETWORK -> R.string.ytdlp_error_network
                            YtDlpFailure.STORAGE -> R.string.ytdlp_error_storage
                            YtDlpFailure.UPDATE_SERVICE -> R.string.ytdlp_error_update_service
                            YtDlpFailure.UNKNOWN -> R.string.ytdlp_error_unknown
                        }
                        Text(
                            stringResource(message),
                            color = colors.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    state.ytDlpUpdateStatus?.let { status ->
                        Text(
                            stringResource(
                                when (status) {
                                    YtDlpUpdateStatus.UPDATED -> R.string.ytdlp_updated
                                    YtDlpUpdateStatus.ALREADY_CURRENT -> R.string.ytdlp_already_current
                                }
                            ),
                            color = colors.primary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }

        // Section 4: Browser Engine
        item {
            SettingsSection(title = stringResource(R.string.browser_engine)) {
                SettingsCardContainer(index = 0, count = 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Language, null, tint = colors.primary)
                        Text(
                            webViewProvider?.label ?: stringResource(R.string.webview_provider_unknown),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        stringResource(
                            R.string.webview_version,
                            webViewProvider?.versionName ?: stringResource(R.string.unknown)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        stringResource(R.string.webview_update_explanation),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    OutlinedButton(
                        onClick = {
                            if (!WebViewUpdateManager.openUpdatePage(context, webViewProvider?.packageName)) {
                                Toast.makeText(context, R.string.webview_update_unavailable, Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.check_webview_updates))
                    }
                }
            }
        }

        // Section 5: About
        item {
            SettingsSection(title = stringResource(R.string.about_section_title)) {
                SettingsActionRow(
                    index = 0,
                    count = 1,
                    title = stringResource(R.string.about_title),
                    description = stringResource(R.string.about_settings_description),
                    leadingIcon = Icons.Filled.Info,
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = onOpenAbout
                )
            }
        }
    }
}
