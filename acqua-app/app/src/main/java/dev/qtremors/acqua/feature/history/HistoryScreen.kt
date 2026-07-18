package dev.qtremors.acqua.feature.history

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
    val colors = MaterialTheme.colorScheme
    val filters = listOf(
        HistoryFilter.MEDIA to R.string.all_media,
        HistoryFilter.PHOTOS to R.string.photos,
        HistoryFilter.VIDEOS to R.string.videos,
        HistoryFilter.LINKS to R.string.links
    )
    LaunchedEffect(active) { if (active) viewModel.refresh() }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.downloads_and_links),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            if (state.entries.isNotEmpty()) {
                TextButton(onClick = {
                    context.performHaptic(HapticSignal.WARNING)
                    viewModel.clear()
                    Toast.makeText(context, R.string.history_cleared, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.clear_all), color = colors.error) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 16.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { (filter, label) ->
                val selected = state.filter == filter
                Surface(
                    onClick = {
                        context.performHaptic(HapticSignal.CLICK)
                        viewModel.selectFilter(filter)
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) colors.primary else colors.surfaceContainerHigh,
                    contentColor = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                    modifier = Modifier.height(40.dp)
                ) {
                    Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(label), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
        if (state.filteredEntries.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_history), color = colors.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.filteredEntries, key = HistoryEntry::id) { entry ->
                    HistoryRow(
                        entry,
                        mediaDownloader,
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
                val bitmap = if (entry.isDownloaded && !entry.isVideo && entry.fileUri.isNotEmpty()) {
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
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    ?: Icon(
                        if (!entry.isDownloaded) Icons.Filled.Link else if (entry.isVideo) Icons.Filled.Movie else Icons.Filled.Image,
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
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                val locale = LocalConfiguration.current.locales[0]
                val date = remember(entry.timestamp, locale) { SimpleDateFormat("MMM dd, HH:mm", locale).format(Date(entry.timestamp)) }
                Text(date, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                val detail = when {
                    !entry.isDownloaded -> entry.url
                    entry.sizeBytes > 0 -> String.format(locale, "%.2f MB", entry.sizeBytes / (1024.0 * 1024.0))
                    else -> stringResource(R.string.unknown_size)
                }
                Text(detail, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = colors.onSurfaceVariant, maxLines = 1)
            }
            if (entry.isDownloaded) {
                IconButton(onOpen) { Icon(Icons.Filled.PlayArrow, stringResource(R.string.open_file), tint = colors.primary) }
                IconButton(onShare) { Icon(Icons.Filled.Share, stringResource(R.string.share_file), tint = colors.primary) }
            } else {
                IconButton(onRefetch) { Icon(Icons.Filled.Refresh, stringResource(R.string.refetch_link), tint = colors.primary) }
            }
            IconButton(onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.delete_record), tint = colors.error) }
        }
    }
}
