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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdatesScreen(
    viewModel: AppUpdatesViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val state by viewModel.state.collectAsState()
    val repos by viewModel.trackedRepos.collectAsState()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var showAddFlow by remember { mutableStateOf(false) }
    var addingNewRepo by remember { mutableStateOf(true) }
    var repoQuery by rememberSaveable { mutableStateOf("") }
    var includePreReleases by rememberSaveable { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var notesRepo by remember { mutableStateOf<TrackedRepo?>(null) }
    var removeRepo by remember { mutableStateOf<TrackedRepo?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val backup = viewModel.exportBackup()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(backup) }
                        ?: error("Could not open the selected file")
                }
            }.fold(
                { Toast.makeText(context, R.string.tracked_repos_exported, Toast.LENGTH_SHORT).show() },
                { Toast.makeText(context, R.string.export_failed, Toast.LENGTH_LONG).show() }
            )
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Could not read the selected file")
                }
            }.mapCatching { viewModel.importBackup(it).getOrThrow() }
                .fold(
                    { count -> Toast.makeText(context, resources.getQuantityString(R.plurals.tracked_repos_imported, count, count), Toast.LENGTH_SHORT).show() },
                    { Toast.makeText(context, R.string.import_failed, Toast.LENGTH_LONG).show() }
                )
        }
    }

    val localizedNotice = state.message?.localized(resources)
    LaunchedEffect(state.message, localizedNotice) {
        localizedNotice?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearMessage()
        }
    }

    val pending = repos.filter { it.installedVersionName != null && it.isUpdateAvailable }
    val current = repos.filter { it.installedVersionName != null && !it.isUpdateAvailable }
    val notInstalled = repos.filter { it.installedVersionName == null }

    BackHandler(enabled = showAddFlow) {
        showAddFlow = false
        viewModel.clearPreview()
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_updates_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (repos.isEmpty()) stringResource(R.string.no_tracked_repositories)
                        else pluralStringResource(R.plurals.tracked_repository_count, repos.size, repos.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    addingNewRepo = true
                    showAddFlow = true
                }) {
                    Icon(Icons.Filled.Add, stringResource(R.string.add_repository))
                }
            }
            if (state.isRefreshing && state.refreshTotal > 0) {
                LinearProgressIndicator(
                    progress = { state.refreshCompleted.toFloat() / state.refreshTotal },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }
        }

        item {
            GitHubAccessCard(
                username = state.authenticatedUser,
                onConnect = { showTokenDialog = true },
                onDisconnect = viewModel::signOut,
                onExport = { exportLauncher.launch("acqua-tracked-repositories.json") },
                onImport = { importLauncher.launch(arrayOf("application/json", "text/plain")) }
            )
        }

        if (!state.isLoading && repos.isEmpty()) {
            item {
                EmptyState(
                    variant = EmptyStateVariant.ReadyToDownload,
                    icon = Icons.Filled.CloudDownload,
                    title = stringResource(R.string.no_tracked_repositories),
                    description = stringResource(R.string.no_tracked_repositories_description),
                    action = { Button(onClick = { addingNewRepo = true; showAddFlow = true }) { Text(stringResource(R.string.add_first_repository)) } }
                )
            }
        }
        if (pending.isNotEmpty()) {
            item { RepoSection(stringResource(R.string.updates_available), pending, state, viewModel, { notesRepo = it }, { addingNewRepo = false; showAddFlow = true; viewModel.editRepo(it) }, { removeRepo = it }) }
        }
        if (current.isNotEmpty()) {
            item { RepoSection(stringResource(R.string.up_to_date), current, state, viewModel, { notesRepo = it }, { addingNewRepo = false; showAddFlow = true; viewModel.editRepo(it) }, { removeRepo = it }) }
        }
        if (notInstalled.isNotEmpty()) {
            item { RepoSection(stringResource(R.string.not_installed), notInstalled, state, viewModel, { notesRepo = it }, { addingNewRepo = false; showAddFlow = true; viewModel.editRepo(it) }, { removeRepo = it }) }
        }
        }

        if (showAddFlow && addingNewRepo && state.preview == null) {
            AddRepositoryInput(
                query = repoQuery,
                includePreReleases = includePreReleases,
                searching = state.isSearching,
                onQueryChange = { repoQuery = it },
                onIncludePreReleasesChange = { includePreReleases = it },
                onSearch = { viewModel.searchRepository(repoQuery, includePreReleases) },
                onDismiss = {
                    showAddFlow = false
                    viewModel.clearPreview()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .padding(bottom = contentPadding.calculateBottomPadding() + 8.dp)
            )
        }
        if (showAddFlow && !addingNewRepo && state.isSearching && state.preview == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }

    if (showAddFlow && state.preview != null) {
        ConfigureRepoBottomSheet(
            state = state,
            onDismiss = { showAddFlow = false; viewModel.clearPreview() },
            onSelectApk = viewModel::selectApk,
            onSelectApp = viewModel::selectInstalledApp,
            onAllowPreReleases = viewModel::setAllowPreReleases,
            onSave = {
                viewModel.savePreview()
                repoQuery = ""
                showAddFlow = false
            }
        )
    }
    if (showTokenDialog) {
        TokenDialog(
            saving = state.isSavingToken,
            onDismiss = { showTokenDialog = false },
            onSave = { viewModel.saveToken(it); showTokenDialog = false }
        )
    }
    notesRepo?.let { repo ->
        ReleaseNotesBottomSheet(repo = repo, onDismiss = { notesRepo = null })
    }
    removeRepo?.let { repo ->
        AlertDialog(
            onDismissRequest = { removeRepo = null },
            title = { Text(stringResource(R.string.stop_tracking_title)) },
            text = { Text(stringResource(R.string.stop_tracking_message, repo.fullName)) },
            confirmButton = {
                TextButton(onClick = { viewModel.removeRepo(repo); removeRepo = null }) { Text(stringResource(R.string.stop_tracking)) }
            },
            dismissButton = { TextButton(onClick = { removeRepo = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
