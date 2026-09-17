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
import dev.qtremors.acqua.core.data.R
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
internal fun ConfigureRepoBottomSheet(
    state: FeedsUiState,
    onDismiss: () -> Unit,
    onSelectApk: (String) -> Unit,
    onSelectApp: (InstalledApp?) -> Unit,
    onAllowPreReleases: (Boolean) -> Unit,
    onSave: () -> Unit
) {
    val preview = state.preview ?: return
    var apkMenu by remember { mutableStateOf(false) }
    var appPicker by remember { mutableStateOf(false) }
    var showReadme by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.92f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(R.string.configure_github_feed), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = preview.repository.owner.avatar_url, contentDescription = null, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(preview.repository.full_name, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Star, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.star_count, preview.repository.stargazers_count), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                preview.repository.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (preview.readme.isNotBlank()) {
                    OutlinedButton(onClick = { showReadme = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Description, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.view_readme))
                    }
                }
                HorizontalDivider()
                Text(stringResource(R.string.apk_asset), fontWeight = FontWeight.SemiBold)
                Box {
                    val selectedLabel = if (preview.selectedApkName == TrackedRepo.AUTO_APK) {
                        val match = dev.qtremors.acqua.data.updater.AppUpdater.selectBestApkAsset(preview.apkAssets)?.name
                        stringResource(R.string.automatic_apk, match ?: stringResource(R.string.no_compatible_asset))
                    } else preview.selectedApkName
                    OutlinedButton(onClick = { apkMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(selectedLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    DropdownMenu(expanded = apkMenu, onDismissRequest = { apkMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.automatic_recommended)) }, onClick = { onSelectApk(TrackedRepo.AUTO_APK); apkMenu = false })
                        preview.apkAssets.forEach { asset ->
                            DropdownMenuItem(text = { Text(asset.name) }, onClick = { onSelectApk(asset.name); apkMenu = false })
                        }
                    }
                }
                Text(stringResource(R.string.linked_app), fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { appPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Apps, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(preview.linkedApp?.appName ?: stringResource(R.string.not_installed))
                }
                OptionSwitch(stringResource(R.string.include_prereleases), stringResource(R.string.include_prereleases_description), preview.allowPreReleases, onAllowPreReleases)
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_github_feed)) }
            Spacer(Modifier.size(24.dp))
        }
    }
    if (appPicker) {
        InstalledAppPickerSheet(state.installedApps, onDismiss = { appPicker = false }, onSelect = { onSelectApp(it); appPicker = false })
    }
    if (showReadme) {
        AlertDialog(
            onDismissRequest = { showReadme = false },
            title = { Text(stringResource(R.string.repository_readme)) },
            text = {
                SelectionContainer {
                    Text(
                        preview.readme,
                        modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showReadme = false }) { Text(stringResource(R.string.done)) } }
        )
    }
}

@Composable
internal fun OptionSwitch(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InstalledAppPickerSheet(apps: List<InstalledApp>, onDismiss: () -> Unit, onSelect: (InstalledApp?) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) { apps.filter { it.appName.contains(query, true) || it.packageName.contains(query, true) } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.8f).padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.link_installed_app), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), label = { Text(stringResource(R.string.search_apps)) }, leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true)
            TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.not_installed_track_only)) }
            LazyColumn(Modifier.weight(1f)) {
                items(filtered, key = InstalledApp::packageName) { app ->
                    TextButton(onClick = { onSelect(app) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(app.appName, color = MaterialTheme.colorScheme.onSurface)
                            Text("${app.packageName} · ${app.versionName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReleaseNotesBottomSheet(repo: TrackedRepo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 20.dp)) {
            Text(repo.latestReleaseName ?: repo.latestTagName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(repo.fullName, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f)) {
                SelectionContainer {
                    Text(repo.latestReleaseBody.orEmpty(), modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            repo.latestReleaseUrl?.let { url ->
                OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.view_release_on_github))
                }
            }
        }
    }
}

@Composable
internal fun TokenDialog(saving: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.github_token_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.github_token_description))
                OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text(stringResource(R.string.github_token)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(token) }, enabled = token.isNotBlank() && !saving) { Text(stringResource(R.string.connect)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
