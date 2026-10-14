package dev.qtremors.acqua.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.data.settings.DownloadDestination
import dev.qtremors.acqua.data.settings.DownloadDestinationStore
import dev.qtremors.acqua.data.settings.FolderOrganization
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.platform.storage.DownloadFolderAccess
import dev.qtremors.acqua.ui.components.SettingsSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DownloadFoldersSection(store: DownloadDestinationStore? = null) {
    val context = LocalContext.current
    val destinations = store ?: remember(context) { DownloadDestinationStore(context) }
    val scope = rememberCoroutineScope()
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
    SettingsSection(stringResource(R.string.download_folders)) {
        Text(stringResource(R.string.download_folders_hint))
        MediaKind.entries.forEach { mediaKind ->
            TextButton(onClick = {
                val saved = destinations.get()[mediaKind]
                tree = saved.treeUri; organization = saved.organization.name; hidden = saved.hidden
                issue = false; kind = mediaKind.name
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(folderKindLabel(mediaKind))) }
        }
    }
    kind?.let { selected ->
        AlertDialog(onDismissRequest = { if (!busy) kind = null },
            title = { Text(stringResource(folderKindLabel(MediaKind.valueOf(selected)))) }, text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(if (tree == null) stringResource(R.string.download_folder_default) else label ?: stringResource(R.string.download_folder_unavailable))
                    TextButton(enabled = !busy, onClick = { picker.launch(tree?.let(Uri::parse)) }) { Text(stringResource(R.string.download_folder_choose)) }
                    if (tree != null) TextButton(enabled = !busy, onClick = { tree = null; hidden = false; issue = false }) {
                        Text(stringResource(R.string.download_folder_use_default))
                    }
                    Column(Modifier.selectableGroup()) {
                        FolderOrganization.entries.forEach { option ->
                            Text(stringResource(folderOrganizationLabel(option)), Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(organization == option.name, enabled = !busy, role = Role.RadioButton,
                                    onClick = { organization = option.name }))
                        }
                    }
                    FilterChip(selected = hidden, enabled = tree != null && !busy, onClick = { hidden = !hidden },
                        label = { Text(stringResource(R.string.download_folder_hidden)) })
                    Text(stringResource(R.string.download_folder_hidden_hint))
                    if (issue) Text(stringResource(R.string.download_folder_unavailable))
                }
            }, confirmButton = { TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true; issue = false
                    val draft = DownloadDestination(tree, FolderOrganization.valueOf(organization), hidden)
                    runCatching { withContext(Dispatchers.IO) {
                        draft.treeUri?.let { access.root(Uri.parse(it)) }
                        destinations.put(MediaKind.valueOf(selected), draft)
                    } }.onSuccess { kind = null }.onFailure {
                        if (it is kotlinx.coroutines.CancellationException) throw it
                        issue = true
                    }
                    busy = false
                }
            }) { Text(stringResource(R.string.done)) } }, dismissButton = {
                TextButton(enabled = !busy, onClick = { kind = null }) { Text(stringResource(R.string.cancel)) }
            })
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
