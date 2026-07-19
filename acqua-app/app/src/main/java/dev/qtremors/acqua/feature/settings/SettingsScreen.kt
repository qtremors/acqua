package dev.qtremors.acqua.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.R
import dev.qtremors.acqua.downloader.FilenameFormatter
import dev.qtremors.acqua.downloader.AudioOutputFormat

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
    ) {
        Text(
            stringResource(R.string.settings),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(vertical = 16.dp)
        )
        Text(
            stringResource(R.string.downloads_and_filenames),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = colors.primary),
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh)
        ) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = state.baseFolder,
                    onValueChange = viewModel::setBaseFolder,
                    label = { Text(stringResource(R.string.downloads_subfolder)) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                )
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.categorize_media),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            stringResource(R.string.category_explanation),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    Switch(state.categorizeMedia, viewModel::setCategorizeMedia)
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                OutlinedTextField(
                    value = state.filenamePattern,
                    onValueChange = viewModel::setFilenamePattern,
                    label = { Text(stringResource(R.string.filename_pattern)) },
                    placeholder = { Text(FilenameFormatter.DEFAULT_PATTERN) },
                    supportingText = { Text(stringResource(R.string.filename_variables_guidance)) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                )
                FlowRow(
                    Modifier.fillMaxWidth(),
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
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.reset_filename_pattern))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.media_processing),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = colors.primary),
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.default_video_quality), fontWeight = FontWeight.Bold)
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
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
                HorizontalDivider(Modifier.padding(vertical = 14.dp))
                Text(stringResource(R.string.default_audio_format), fontWeight = FontWeight.Bold)
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
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
                SettingsSwitchRow(
                    title = stringResource(R.string.embed_metadata),
                    description = stringResource(R.string.embed_metadata_explanation),
                    checked = state.embedMetadata,
                    onCheckedChange = viewModel::setEmbedMetadata
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.embed_thumbnail),
                    description = stringResource(R.string.embed_thumbnail_explanation),
                    checked = state.embedThumbnail,
                    onCheckedChange = viewModel::setEmbedThumbnail
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SettingsSwitchRow(
                    title = stringResource(R.string.auto_update_ytdlp),
                    description = stringResource(R.string.auto_update_ytdlp_explanation),
                    checked = state.autoUpdateYtDlp,
                    onCheckedChange = viewModel::setAutoUpdateYtDlp
                )
                Text(
                    stringResource(R.string.ytdlp_version, state.ytDlpVersion ?: stringResource(R.string.unknown)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { viewModel.updateYtDlp() },
                    enabled = !state.isUpdatingYtDlp,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    if (state.isUpdatingYtDlp) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(if (state.isUpdatingYtDlp) R.string.updating_ytdlp else R.string.update_ytdlp))
                }
                state.ytDlpUpdateError?.let {
                    Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked, onCheckedChange)
    }
}
