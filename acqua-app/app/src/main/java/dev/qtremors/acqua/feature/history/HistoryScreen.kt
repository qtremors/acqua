package dev.qtremors.acqua.feature.history

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import dev.qtremors.acqua.R
import dev.qtremors.acqua.data.history.HistoryEntry
import dev.qtremors.acqua.data.network.MediaDownloader
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.platform.FileActions
import dev.qtremors.acqua.platform.HapticSignal
import dev.qtremors.acqua.platform.performHaptic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    mediaDownloader: MediaDownloader,
    fileActions: FileActions,
    active: Boolean,
    onRefetch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val visibleEntries = state.filteredEntries
    val colors = MaterialTheme.colorScheme
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(active) { if (active) viewModel.refresh() }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_history)) },
            text = { Text(stringResource(R.string.clear_history_confirmation)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    context.performHaptic(HapticSignal.WARNING)
                    viewModel.clear()
                    Toast.makeText(context, R.string.history_cleared, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.clear_all), color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        HistoryHeader(
            hasEntries = state.entries.isNotEmpty(),
            onOpenDownloads = fileActions::openDownloads,
            onClear = { confirmClear = true }
        )
        HistorySummaryCard(state.summary)
        HistorySearchAndFilters(
            query = state.query,
            selectedFilter = state.filter,
            selectedSort = state.sort,
            summary = state.summary,
            resultCount = visibleEntries.size,
            onQueryChange = viewModel::setQuery,
            onFilterChange = { filter ->
                context.performHaptic(HapticSignal.CLICK)
                viewModel.selectFilter(filter)
            },
            onSortChange = { sort ->
                context.performHaptic(HapticSignal.CLICK)
                viewModel.selectSort(sort)
            }
        )
        if (visibleEntries.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                HistoryEmptyState(state.emptyReason)
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(visibleEntries, key = HistoryEntry::id) { entry ->
                    HistoryRow(
                        entry,
                        mediaDownloader,
                        missing = entry.id in state.missingEntryIds,
                        onOpen = { context.performHaptic(HapticSignal.CLICK); fileActions.open(entry.fileUri, entry.mimeType) },
                        onShare = { context.performHaptic(HapticSignal.CLICK); fileActions.share(entry.fileUri, entry.mimeType) },
                        onDelete = { context.performHaptic(HapticSignal.CLICK); viewModel.delete(entry.id) },
                        onRefetch = { context.performHaptic(HapticSignal.CLICK); onRefetch(entry.url) }
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    mediaDownloader: MediaDownloader,
    missing: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRefetch: () -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var image by remember(entry.id, entry.thumbnailUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(entry.id, entry.thumbnailUrl, entry.fileUri) {
        image = withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = if (entry.isDownloaded && !entry.isVideo && !entry.isAudio && entry.fileUri.isNotEmpty()) {
                    context.contentResolver.openInputStream(entry.fileUri.toUri())?.use(BitmapFactory::decodeStream)
                } else {
                    entry.thumbnailUrl?.let { url ->
                        mediaDownloader.fetchBytes(ResolvedMedia(url, MediaKind.IMAGE))
                            .let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    }
                }
                bitmap?.asImageBitmap()
            }.getOrNull()
        }
    }
    Card(
        Modifier.fillMaxWidth(), RoundedCornerShape(24.dp),
        CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        ?: Icon(
                            when {
                                !entry.isDownloaded -> Icons.Filled.Link
                                entry.isVideo -> Icons.Filled.Movie
                                entry.isAudio -> Icons.Filled.MusicNote
                                else -> Icons.Filled.Image
                            },
                            null,
                            tint = colors.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (entry.isDownloaded) entry.fileName else stringResource(R.string.media_link_title),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2
                    )
                    Spacer(Modifier.height(4.dp))
                    val locale = LocalConfiguration.current.locales[0]
                    val date = remember(entry.timestamp, locale) {
                        SimpleDateFormat("MMM dd, HH:mm", locale).format(Date(entry.timestamp))
                    }
                    Text(date, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    val detail = when {
                        missing -> stringResource(R.string.file_missing)
                        !entry.isDownloaded -> entry.url
                        entry.sizeBytes > 0 -> String.format(locale, "%.2f MB", entry.sizeBytes / (1024.0 * 1024.0))
                        else -> stringResource(R.string.unknown_size)
                    }
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = if (missing) colors.error else colors.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (entry.isDownloaded && !missing) {
                    IconButton(onOpen) { Icon(Icons.Filled.PlayArrow, stringResource(R.string.open_file), tint = colors.primary) }
                    IconButton(onShare) { Icon(Icons.Filled.Share, stringResource(R.string.share_file), tint = colors.primary) }
                } else {
                    IconButton(onRefetch) { Icon(Icons.Filled.Refresh, stringResource(R.string.refetch_link), tint = colors.primary) }
                }
                IconButton(onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.delete_record), tint = colors.error) }
            }
        }
    }
}
