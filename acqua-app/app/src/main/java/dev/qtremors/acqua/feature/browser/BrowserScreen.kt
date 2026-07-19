package dev.qtremors.acqua.feature.browser

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.R
import dev.qtremors.acqua.data.session.SavedWebsite
import dev.qtremors.acqua.domain.WebLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onAddWebsite: (String, String) -> Unit,
    onOpenWebsite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    var showAddDialog by remember { mutableStateOf(false) }
    var showManageDialog by remember { mutableStateOf(false) }
    var editingWebsite by remember { mutableStateOf<SavedWebsite?>(null) }

    if (showAddDialog) WebsiteDialog(
        title = R.string.save_website,
        confirmLabel = R.string.save_and_open,
        onDismiss = { showAddDialog = false },
        onSave = { name, url -> showAddDialog = false; onAddWebsite(name, url) }
    )
    editingWebsite?.let { website ->
        WebsiteDialog(
            title = R.string.edit_website,
            confirmLabel = R.string.save_changes,
            initialName = website.name,
            initialUrl = website.origin,
            onDismiss = { editingWebsite = null },
            onSave = { name, url ->
                viewModel.updateWebsite(website.origin, name, url)
                editingWebsite = null
            }
        )
    }
    if (showManageDialog) ManageWebsiteDataDialog(
        websites = state.websites,
        onDismiss = { showManageDialog = false },
        onClearSelected = { showManageDialog = false; viewModel.clearSelected(it) },
        onClearAll = { showManageDialog = false; viewModel.clearAll() }
    )

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Text(
            stringResource(R.string.browser),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(vertical = 16.dp)
        )
        Text(
            stringResource(R.string.browser_extraction),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = colors.primary),
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Card(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            RoundedCornerShape(16.dp),
            CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh)
        ) {
            Column(Modifier.padding(16.dp)) {
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    RoundedCornerShape(12.dp),
                    CardDefaults.cardColors(containerColor = colors.errorContainer.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, colors.errorContainer.copy(alpha = 0.5f))
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Warning, stringResource(R.string.warning), tint = colors.error, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                stringResource(R.string.browse_responsibly),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.error
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.browser_safety_guidance), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.use_browser_sessions),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            stringResource(R.string.browser_sessions_explanation),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(state.useSessions, viewModel::setUseSessions)
                }
                Text(stringResource(R.string.saved_websites), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                Text(
                    stringResource(if (state.websites.isEmpty()) R.string.save_website_guidance else R.string.open_website_guidance),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    state.websites.forEach { website ->
                        WebsiteTile(
                            website,
                            onOpen = { onOpenWebsite(website.origin) },
                            onEdit = { editingWebsite = website }
                        )
                    }
                    AddWebsiteTile { showAddDialog = true }
                }
                OutlinedButton(
                    onClick = { showManageDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) { Text(stringResource(R.string.manage_website_data)) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WebsiteDialog(
    title: Int,
    confirmLabel: Int,
    initialName: String = "",
    initialUrl: String = "",
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var error by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    name, { name = it.take(40); error = null },
                    label = { Text(stringResource(R.string.name)) },
                    placeholder = { Text(stringResource(R.string.website_name_hint)) },
                    singleLine = true,
                    isError = error == R.string.enter_name,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    url, { url = it; error = null },
                    label = { Text(stringResource(R.string.website_url)) },
                    placeholder = { Text(stringResource(R.string.website_url_hint)) },
                    singleLine = true,
                    isError = error == R.string.enter_valid_website,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val normalized = WebLink.normalize(url)
                when {
                    name.isBlank() -> error = R.string.enter_name
                    normalized == null -> error = R.string.enter_valid_website
                    else -> onSave(name.trim(), normalized)
                }
            }) { Text(stringResource(confirmLabel)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun ManageWebsiteDataDialog(
    websites: List<SavedWebsite>,
    onDismiss: () -> Unit,
    onClearSelected: (Set<String>) -> Unit,
    onClearAll: () -> Unit
) {
    var selected by remember(websites) { mutableStateOf(emptySet<String>()) }
    val allOrigins = websites.mapTo(mutableSetOf(), SavedWebsite::origin)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_website_data)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (websites.isEmpty()) Text(stringResource(R.string.no_saved_websites)) else {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(stringResource(R.string.select_websites), style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = { selected = if (selected == allOrigins) emptySet() else allOrigins }) {
                            Text(stringResource(if (selected == allOrigins) R.string.deselect_all else R.string.select_all))
                        }
                    }
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        websites.forEach { website ->
                            val checked = website.origin in selected
                            Surface(
                                onClick = { selected = if (checked) selected - website.origin else selected + website.origin },
                                shape = RoundedCornerShape(12.dp),
                                color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked, null)
                                    Spacer(Modifier.width(8.dp))
                                    Column { Text(website.name); Text(website.host, style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.website_data_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onClearAll, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.clear_all_browser_data), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onClearSelected(selected) }, enabled = selected.isNotEmpty()) {
                Text(stringResource(R.string.clear_selected))
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WebsiteTile(
    website: SavedWebsite,
    onOpen: () -> Unit,
    onEdit: () -> Unit
) {
    val iconKey = website.iconFile?.let { "${it.absolutePath}:${it.lastModified()}" }
    val icon by produceState<ImageBitmap?>(null, iconKey) {
        value = withContext(Dispatchers.IO) {
            website.iconFile?.takeIf(File::isFile)?.let { BitmapFactory.decodeFile(it.absolutePath) }?.asImageBitmap()
        }
    }
    Surface(
        modifier = Modifier.width(96.dp).height(112.dp).combinedClickable(
            onClick = onOpen,
            onLongClick = onEdit
        ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(10.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Box(
                Modifier.size(54.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                icon?.let {
                    Image(it, stringResource(R.string.website_icon_description, website.name, website.host), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } ?: Icon(Icons.Filled.Public, null, Modifier.size(28.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(website.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun AddWebsiteTile(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.width(96.dp).height(112.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Filled.Add, stringResource(R.string.save_website), Modifier.size(40.dp))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.save_website), style = MaterialTheme.typography.labelMedium)
        }
    }
}
