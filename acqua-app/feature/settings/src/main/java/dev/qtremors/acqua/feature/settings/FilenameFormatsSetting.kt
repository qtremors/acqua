package dev.qtremors.acqua.feature.settings

import dev.qtremors.acqua.ui.components.ExpressiveTextField
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.downloader.FilenameFormatter
import dev.qtremors.acqua.ui.components.SettingsActionRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilenameFormatsSetting(
    videoPattern: String,
    audioPattern: String,
    onVideoChange: (String) -> Unit,
    onAudioChange: (String) -> Unit,
    index: Int = 0,
    count: Int = 1
) {
    var open by rememberSaveable { mutableStateOf(false) }
    SettingsActionRow(stringResource(R.string.filename_formats), { open = true }, index = index, count = count,
        description = stringResource(R.string.filename_formats_summary), leadingIcon = Icons.Filled.DriveFileRenameOutline,
        trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
    if (open) {
        var expandedGroup by rememberSaveable { mutableStateOf<String?>(null) }
        ModalBottomSheet(onDismissRequest = { open = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp).testTag("filename-formats-sheet"),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.filename_formats), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = { open = false }) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
                }
                FilenameFormatGroup(videoPattern, false, expandedGroup == "video", {
                    expandedGroup = if (expandedGroup == "video") null else "video"
                }, onVideoChange)
                FilenameFormatGroup(audioPattern, true, expandedGroup == "audio", {
                    expandedGroup = if (expandedGroup == "audio") null else "audio"
                }, onAudioChange)
            }
        }
    }
}

@Composable
private fun FilenameFormatGroup(pattern: String, audio: Boolean, expanded: Boolean, onToggle: () -> Unit, onSave: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val state = stringResource(if (expanded) R.string.filename_group_expanded else R.string.filename_group_collapsed)
    Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle)
                .semantics(mergeDescendants = true) { stateDescription = state }
                .testTag(if (audio) "filename-group-audio" else "filename-group-video")
                .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = colors.primaryContainer) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(if (audio) Icons.Filled.Audiotrack else Icons.Filled.Movie, null, tint = colors.onPrimaryContainer)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(if (audio) R.string.audio else R.string.video), style = MaterialTheme.typography.titleMedium)
                    Text(if (audio) FilenameFormatter.previewAudio(pattern) else FilenameFormatter.preview(pattern),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = colors.onSurfaceVariant)
            }
            AnimatedVisibility(expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                FilenameFormatEditor(pattern, audio, onSave)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FilenameFormatEditor(pattern: String, audio: Boolean, onSave: (String) -> Unit) {
    val title = stringResource(if (audio) R.string.audio_filename_pattern else R.string.filename_pattern)
    val default = if (audio) FilenameFormatter.DEFAULT_AUDIO_PATTERN else FilenameFormatter.DEFAULT_PATTERN
    val variables = if (audio) FilenameFormatter.audioVariables else FilenameFormatter.variables
    val editor = rememberTextFieldState(pattern)
    val currentPattern = editor.text.toString()
    val currentOnSave by rememberUpdatedState(onSave)
    var lastReported by remember { mutableStateOf(pattern) }
    LaunchedEffect(editor) {
        snapshotFlow { editor.text.toString() }.collect { value ->
            if (value != lastReported) {
                lastReported = value
                currentOnSave(value)
            }
        }
    }
    LaunchedEffect(pattern) {
        if (pattern != lastReported) {
            lastReported = pattern
            editor.edit { replace(0, length, pattern) }
        }
    }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ExpressiveTextField(state = editor, modifier = Modifier.fillMaxWidth().semantics { contentDescription = title },
            label = { Text(title) }, placeholder = { Text(default) },
            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 1, maxHeightInLines = 3))
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(stringResource(if (audio) R.string.audio_filename_preview else R.string.filename_preview,
                if (audio) FilenameFormatter.previewAudio(currentPattern) else FilenameFormatter.preview(currentPattern)),
                Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.settings_filename_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val labelStyle = MaterialTheme.typography.labelLarge
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val tokenWidths = remember(variables, labelStyle, density.density, density.fontScale) {
            variables.associateWith { variable ->
                with(density) { textMeasurer.measure(variable, style = labelStyle).size.width.toDp() } + 24.dp
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val availableWidth = maxWidth
            val halfWidth = (availableWidth - 6.dp) / 2
            FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                variables.forEach { variable ->
                    val selected = currentPattern.contains(variable)
                    val minimumWidth = if (tokenWidths.getValue(variable) > halfWidth) availableWidth else halfWidth
                    ToggleButton(checked = selected, onCheckedChange = {
                        val updated = if (audio) FilenameFormatter.toggleAudioVariable(currentPattern, variable)
                            else FilenameFormatter.toggleVariable(currentPattern, variable)
                        editor.edit { replace(0, length, updated) }
                    }, modifier = Modifier.weight(1f).widthIn(min = minimumWidth).heightIn(min = 44.dp)
                        .testTag("filename-token-${variable.removeSurrounding("{", "}")}"),
                        shapes = ToggleButtonShapes(CircleShape, CircleShape, CircleShape),
                        colors = ToggleButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(variable, Modifier.fillMaxWidth(), style = labelStyle, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        OutlinedButton(onClick = { editor.edit { replace(0, length, default) } }, enabled = currentPattern != default,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (audio) R.string.reset_audio_filename_pattern else R.string.reset_filename_pattern))
        }
    }
}
