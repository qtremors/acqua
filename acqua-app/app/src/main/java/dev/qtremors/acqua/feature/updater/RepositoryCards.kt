package dev.qtremors.acqua.feature.updater

import android.content.Intent
import android.content.ClipboardManager
import androidx.core.net.toUri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.qtremors.acqua.R
import dev.qtremors.acqua.data.updater.InstalledApp
import dev.qtremors.acqua.data.updater.TrackedRepo
import dev.qtremors.acqua.ui.components.EmptyState
import dev.qtremors.acqua.ui.components.EmptyStateVariant
import dev.qtremors.acqua.ui.components.SettingsCardContainer
import dev.qtremors.acqua.ui.components.SettingsSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun GitHubAccessCard(
    username: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.github_access)) {
        SettingsCardContainer {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Key, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        username?.let { stringResource(R.string.connected_as, it) } ?: stringResource(R.string.public_access),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (username == null) stringResource(R.string.public_access_description) else stringResource(R.string.token_access_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = if (username == null) onConnect else onDisconnect) {
                    Text(stringResource(if (username == null) R.string.add_token else R.string.disconnect))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.FileUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.import_label))
                }
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.export_label))
                }
            }
        }
    }
}

@Composable
internal fun RepoSection(
    title: String,
    repos: List<TrackedRepo>,
    state: AppUpdatesUiState,
    viewModel: AppUpdatesViewModel,
    onNotes: (TrackedRepo) -> Unit,
    onEdit: (TrackedRepo) -> Unit,
    onRemove: (TrackedRepo) -> Unit
) {
    SettingsSection(title = title) {
        repos.forEachIndexed { index, repo ->
            RepoCard(
                repo = repo,
                downloadState = state.downloadStates[repo.fullName] ?: UpdateDownloadState.Idle,
                index = index,
                count = repos.size,
                onNotes = { onNotes(repo) },
                onEdit = { onEdit(repo) },
                onRemove = { onRemove(repo) },
                onDownload = { viewModel.download(repo) },
                onInstall = { file ->
                    if (viewModel.canInstallPackages()) viewModel.install(repo, file)
                    else viewModel.openInstallPermissionSettings()
                }
            )
        }
    }
}

@Composable
internal fun RepoCard(
    repo: TrackedRepo,
    downloadState: UpdateDownloadState,
    index: Int,
    count: Int,
    onNotes: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onDownload: () -> Unit,
    onInstall: (java.io.File) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val iconModel = remember(repo.mappedPackageName, repo.avatarUrl) {
        repo.mappedPackageName?.let { runCatching { context.packageManager.getApplicationIcon(it) }.getOrNull() }
            ?: repo.avatarUrl
    }
    val shape = when {
        count == 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
        index == count - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(4.dp)
    }
    Surface(modifier = Modifier.fillMaxWidth(), shape = shape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = iconModel, contentDescription = null, modifier = Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(repo.mappedAppName ?: repo.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(repo.fullName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.stop_tracking)) }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { menu = false; onRemove() })
                    }
                }
            }
            repo.description?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = if (repo.isUpdateAvailable) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(10.dp)) {
                    Text(
                        if (repo.installedVersionName == null) repo.latestTagName else "${repo.installedVersionName}  →  ${repo.latestTagName}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onNotes, enabled = !repo.latestReleaseBody.isNullOrBlank()) {
                    Icon(Icons.Filled.Description, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.notes))
                }
                when (downloadState) {
                    is UpdateDownloadState.Downloaded -> FilledTonalButton(onClick = { onInstall(downloadState.apkFile) }) { Text(stringResource(R.string.install)) }
                    is UpdateDownloadState.Downloading -> Text("${downloadState.progressPercent}%", style = MaterialTheme.typography.labelLarge)
                    else -> if (repo.installedVersionName != null && !repo.isUpdateAvailable) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(5.dp)); Text(stringResource(R.string.current_version), style = MaterialTheme.typography.labelLarge)
                        }
                    } else FilledTonalButton(onClick = onDownload, enabled = repo.downloadUrl != null) {
                        Text(stringResource(if (repo.installedVersionName == null) R.string.download_and_install else R.string.update))
                    }
                }
            }
            if (downloadState is UpdateDownloadState.Downloading) {
                LinearProgressIndicator(
                    progress = { downloadState.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            if (downloadState is UpdateDownloadState.Error) {
                Text(
                    stringResource(downloadState.reason.messageResource),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
internal fun AddRepositoryInput(
    query: String,
    includePreReleases: Boolean,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    onIncludePreReleasesChange: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.add_repository),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, stringResource(R.string.cancel), Modifier.size(18.dp))
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.repository_address_example)) },
                leadingIcon = { Icon(Icons.Filled.Link, stringResource(R.string.repository_address)) },
                trailingIcon = {
                    Row {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Close, stringResource(R.string.clear_link), Modifier.size(18.dp))
                            }
                        }
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
                                    ?.getItemAt(0)?.text?.toString()?.let(onQueryChange)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.ContentPaste, stringResource(R.string.paste), Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (query.isNotBlank() && !searching) {
                        keyboard?.hide()
                        onSearch()
                    }
                })
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.include_prereleases),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = includePreReleases, onCheckedChange = onIncludePreReleasesChange)
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = {
                        keyboard?.hide()
                        onSearch()
                    },
                    enabled = query.isNotBlank() && !searching
                ) {
                    if (searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Search, stringResource(R.string.find_repository), Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.add_repository))
                }
            }
        }
    }
}
