package dev.qtremors.acqua.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import dev.qtremors.acqua.ui.components.SettingsActionRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.data.settings.DownloadDestination
import dev.qtremors.acqua.data.settings.DownloadDestinationStore
import dev.qtremors.acqua.data.settings.FolderOrganization
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.platform.storage.DownloadFolderAccess
import dev.qtremors.acqua.ui.components.SettingsSection
import dev.qtremors.acqua.ui.components.SettingsCardContainer
import dev.qtremors.acqua.ui.components.SettingsSwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadFoldersSection(store: DownloadDestinationStore? = null, index: Int = 0, count: Int = 1) {
    val context = LocalContext.current
    val destinations = store ?: remember(context) { DownloadDestinationStore(context) }
    val scope = rememberCoroutineScope()
    var open by rememberSaveable { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    var tree by rememberSaveable { mutableStateOf<String?>(null) }
    var organization by rememberSaveable { mutableStateOf(FolderOrganization.NONE.name) }
    var hidden by rememberSaveable { mutableStateOf(false) }
    var issue by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf<String?>(null) }
    val access = remember(context) { DownloadFolderAccess(context) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                access.root(uri)
            } }.onSuccess { tree = uri.toString(); issue = false }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                issue = true
            }
            busy = false
        }
    }
    LaunchedEffect(tree, kind) {
        label = null
        tree?.let { selected ->
            label = withContext(Dispatchers.IO) { runCatching { access.label(Uri.parse(selected)) }.getOrNull() }
        }
    }
    SettingsActionRow(stringResource(R.string.download_folders), { kind = null; open = true }, index = index, count = count,
        description = stringResource(R.string.settings_download_folders_summary), leadingIcon = Icons.Filled.Folder,
        trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
    if (open) {
        val saved = remember(revision) { destinations.get() }
        val selected = kind?.let(MediaKind::valueOf)
        ModalBottomSheet(onDismissRequest = { if (!busy) open = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            BackHandler(enabled = selected != null) { if (!busy) kind = null }
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (selected != null) IconButton(enabled = !busy, onClick = { kind = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                    Text(stringResource(selected?.let(::folderKindLabel) ?: R.string.download_folders), Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    IconButton(enabled = !busy, onClick = { open = false }) {
                        Icon(Icons.Filled.Close, stringResource(R.string.close))
                    }
                }
                if (selected == null) {
                    Text(stringResource(R.string.download_folders_hint), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val kinds = listOf(MediaKind.IMAGE, MediaKind.VIDEO, MediaKind.AUDIO)
                        kinds.forEachIndexed { row, mediaKind ->
                            SettingsActionRow(stringResource(folderKindLabel(mediaKind)), index = row, count = kinds.size,
                                description = stringResource(if (saved[mediaKind].treeUri == null) R.string.download_folder_default else R.string.download_folder_custom),
                                leadingIcon = when (mediaKind) {
                                    MediaKind.IMAGE -> Icons.Filled.Image
                                    MediaKind.VIDEO -> Icons.Filled.Movie
                                    MediaKind.AUDIO -> Icons.Filled.Audiotrack
                                }, trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight, onClick = {
                                    val destination = saved[mediaKind]
                                    tree = destination.treeUri; organization = destination.organization.name; hidden = destination.hidden
                                    issue = false; kind = mediaKind.name
                                })
                        }
                    }
                } else {
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SettingsSection(stringResource(R.string.download_folder_location)) {
                            SettingsCardContainer {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    Text(if (tree == null) stringResource(R.string.download_folder_default)
                                        else label ?: stringResource(R.string.download_folder_custom), style = MaterialTheme.typography.bodyLarge)
                                }
                                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    FilledTonalButton(enabled = !busy, onClick = { picker.launch(tree?.let(Uri::parse)) },
                                        modifier = Modifier.weight(1f)) { Text(stringResource(R.string.download_folder_choose)) }
                                    if (tree != null) TextButton(enabled = !busy, onClick = { tree = null; hidden = false; issue = false },
                                        modifier = Modifier.weight(1f)) { Text(stringResource(R.string.download_folder_use_default)) }
                                }
                            }
                        }
                        SettingsSection(stringResource(R.string.download_folder_subfolders)) {
                            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                FolderOrganization.entries.forEachIndexed { row, option ->
                                    SettingsCardContainer(Modifier.heightIn(min = 52.dp)
                                        .selectable(organization == option.name, enabled = !busy, role = Role.RadioButton,
                                            onClick = { organization = option.name }), index = row, count = FolderOrganization.entries.size) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            RadioButton(organization == option.name, onClick = null, enabled = !busy)
                                            Text(stringResource(folderOrganizationLabel(option)), style = MaterialTheme.typography.bodyLarge)
                                        }
                                    }
                                }
                            }
                        }
                        if (tree != null) SettingsSwitchRow(stringResource(R.string.download_folder_hidden), hidden, { hidden = it },
                            enabled = !busy, description = stringResource(R.string.download_folder_hidden_summary))
                        if (issue) Text(stringResource(R.string.download_folder_unavailable), color = MaterialTheme.colorScheme.error)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(enabled = !busy, onClick = { kind = null }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.cancel))
                        }
                        FilledTonalButton(enabled = !busy, modifier = Modifier.weight(1f), onClick = {
                            scope.launch {
                                busy = true; issue = false
                                val draft = DownloadDestination(tree, FolderOrganization.valueOf(organization), hidden)
                                runCatching { withContext(Dispatchers.IO) {
                                    draft.treeUri?.let { access.root(Uri.parse(it)) }
                                    destinations.put(selected, draft)
                                } }.onSuccess { revision++; kind = null }.onFailure {
                                    if (it is kotlinx.coroutines.CancellationException) throw it
                                    issue = true
                                }
                                busy = false
                            }
                        }) { Text(stringResource(R.string.done)) }
                    }
                }
            }
        }
    }
}

private fun folderKindLabel(kind: MediaKind): Int = when (kind) {
    MediaKind.IMAGE -> R.string.download_folder_photos
    MediaKind.AUDIO -> R.string.download_folder_audio
    MediaKind.VIDEO -> R.string.download_folder_video
}

private fun folderOrganizationLabel(organization: FolderOrganization): Int = when (organization) {
    FolderOrganization.NONE -> R.string.download_folder_flat
    FolderOrganization.SOURCE -> R.string.download_folder_source
    FolderOrganization.COLLECTION -> R.string.download_folder_collection
    FolderOrganization.SOURCE_COLLECTION -> R.string.download_folder_source_collection
}
