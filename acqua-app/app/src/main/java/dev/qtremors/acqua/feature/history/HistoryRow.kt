package dev.qtremors.acqua.feature.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import dev.qtremors.acqua.R
import dev.qtremors.acqua.data.history.HistoryEntry
import dev.qtremors.acqua.data.history.HistoryFileStatus
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.platform.FileActions
import dev.qtremors.acqua.ui.image.ThumbnailKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

internal fun copyHistoryLink(context: Context, url: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
        ?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.hist_source), url))
    Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
}

@Composable
internal fun HistoryRow(
    entry: HistoryEntry, status: HistoryFileStatus?, selecting: Boolean, isSelected: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, onToggle: () -> Unit,
    onShare: () -> Unit, onRemove: () -> Unit, onDetails: () -> Unit,
    onSource: () -> Unit, onRefetch: () -> Unit, onRetry: () -> Unit
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val colors = MaterialTheme.colorScheme
    val available = entry.isDownloaded && status == HistoryFileStatus.AVAILABLE
    val title = entry.fileName.ifBlank { WebLink.host(entry.url) ?: stringResource(R.string.media_link_title) }
    val type = stringResource(when {
        !entry.isDownloaded -> R.string.links
        entry.isAudio -> R.string.audio
        entry.isVideo -> R.string.video
        else -> R.string.photo
    })
    var menu by remember { mutableStateOf(false) }
    val thumbnail = remember(entry, status) {
        when {
            !available -> null
            entry.isVideo || entry.isAudio -> ThumbnailKey("", entry.fileName.substringAfterLast('.', ""), entry.sizeBytes, entry.timestamp, entry.fileUri)
            else -> entry.fileUri.toUri()
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth().semantics { if (selecting) selected = isSelected }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) colors.secondaryContainer else colors.surfaceContainer)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                val description = stringResource(R.string.hist_select_item, title)
                Checkbox(isSelected, onCheckedChange = { onToggle() }, modifier = Modifier.semantics { contentDescription = description })
            }
            Box(Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                Icon(when {
                    !entry.isDownloaded -> Icons.Default.Link
                    entry.isAudio -> Icons.Default.MusicNote
                    entry.isVideo -> Icons.Default.Movie
                    else -> Icons.Default.Image
                }, null, tint = colors.primary)
                if (thumbnail != null) AsyncImage(thumbnail, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val source = WebLink.host(entry.url)?.removePrefix("www.").orEmpty()
                val size = if (entry.sizeBytes > 0) Formatter.formatShortFileSize(context, entry.sizeBytes) else stringResource(R.string.unknown_size)
                Text(listOf(source, type, size.takeIf { entry.isDownloaded }.orEmpty()).filter(String::isNotBlank).joinToString(" | "), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(entry.timestamp)), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                if (entry.isDownloaded && !available) Text(stringResource(if (status == HistoryFileStatus.MISSING) R.string.hist_file_missing else R.string.hist_file_unavailable), color = colors.error, style = MaterialTheme.typography.bodySmall)
            }
            if (!selecting) Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.hist_item_options, title)) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    if (available) DropdownMenuItem(text = { Text(stringResource(R.string.share_file)) }, onClick = { menu = false; onShare() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.hist_details)) }, onClick = { menu = false; onDetails() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.hist_open_source)) }, onClick = { menu = false; onSource() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.hist_copy_link)) }, onClick = { menu = false; copyHistoryLink(context, entry.url) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.refetch_link)) }, onClick = { menu = false; onRefetch() })
                    if (entry.isDownloaded && !available) DropdownMenuItem(text = { Text(stringResource(R.string.hist_retry)) }, onClick = { menu = false; onRetry() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(stringResource(R.string.hist_select)) }, onClick = { menu = false; onLongClick() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.hist_remove), color = colors.error) }, onClick = { menu = false; onRemove() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryDetailsSheet(
    entry: HistoryEntry, status: HistoryFileStatus?, onDismiss: () -> Unit,
    fileActions: FileActions, onRefetch: () -> Unit, onRetry: () -> Unit
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val location by produceState(entry.fileUri, entry.fileUri) {
        value = withContext(Dispatchers.IO) { fileActions.storageLocation(entry.fileUri) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.hist_details), style = MaterialTheme.typography.titleLarge)
            if (entry.isDownloaded) {
                HistoryDetail(R.string.hist_filename, entry.fileName)
                HistoryDetail(R.string.hist_location, location)
                HistoryDetail(R.string.hist_size, if (entry.sizeBytes > 0) Formatter.formatFileSize(context, entry.sizeBytes) else stringResource(R.string.unknown_size))
                HistoryDetail(R.string.hist_status, stringResource(when (status) {
                    HistoryFileStatus.AVAILABLE -> R.string.hist_available
                    HistoryFileStatus.MISSING -> R.string.hist_file_missing
                    else -> R.string.hist_file_unavailable
                }))
            }
            HistoryDetail(R.string.hist_source, entry.url)
            HistoryDetail(R.string.hist_date, DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT, locale).format(Date(entry.timestamp)))
            HorizontalDivider()
            if (entry.isDownloaded && status == HistoryFileStatus.AVAILABLE) {
                TextButton(onClick = { fileActions.open(entry.fileUri, entry.mimeType) }) { Text(stringResource(R.string.open_file)) }
                TextButton(onClick = { fileActions.share(entry.fileUri, entry.mimeType) }) { Text(stringResource(R.string.share_file)) }
            } else if (entry.isDownloaded) TextButton(onClick = onRetry) { Text(stringResource(R.string.hist_retry)) }
            TextButton(onClick = { fileActions.openSource(entry.url) }) { Text(stringResource(R.string.hist_open_source)) }
            TextButton(onClick = { copyHistoryLink(context, entry.url) }) { Text(stringResource(R.string.hist_copy_link)) }
            TextButton(onClick = onRefetch) { Text(stringResource(R.string.refetch_link)) }
        }
    }
}

@Composable
private fun HistoryDetail(label: Int, value: String) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(value, style = MaterialTheme.typography.bodyMedium) }
    }
}
