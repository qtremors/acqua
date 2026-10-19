package dev.qtremors.acqua.feature.downloader

import dev.qtremors.acqua.ui.components.EmptyState
import dev.qtremors.acqua.ui.components.EmptyStateVariant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.domain.CollectionSelection
import dev.qtremors.acqua.domain.PlaylistLink
import dev.qtremors.acqua.domain.ResolvedMedia

@Composable
internal fun PlaylistPickerDialog(
    state: DownloaderUiState, onResolve: () -> Unit, onDismiss: () -> Unit, onApply: (Boolean, Set<Int>?) -> Unit
) {
    var playlist by remember { mutableStateOf(state.downloadPlaylist) }
    var draft by remember { mutableStateOf(state.selectedCollectionItems) }
    val scopeChanged = playlist != state.downloadPlaylist
    val entries = state.media.orEmpty().takeIf { !scopeChanged && playlist }.orEmpty()
    val available = entries.indices.filter { entries[it].unavailableReason == null }.toSet()
    val selected = draft?.intersect(available) ?: available
    DownloadContentDialog(stringResource(R.string.playlist), "download-playlist-dialog",
        !state.isResolving && (scopeChanged || entries.isEmpty() || selected.isNotEmpty()), onDismiss,
        { onApply(playlist, if (scopeChanged) null else draft) }, tabs = {
            if (PlaylistLink.videoUrl(state.url) != null) DownloadDialogTabs(
                listOf(stringResource(R.string.this_video), stringResource(R.string.playlist)), if (playlist) 1 else 0, { playlist = it == 1 })
        }) { modifier ->
        LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                if (scopeChanged) Text(stringResource(R.string.playlist_scope_fetch_hint))
                else if (state.media == null) SourceFetchPrompt(state.isResolving, onResolve)
                else if (!playlist) Text(stringResource(R.string.this_video))
                else if (entries.isEmpty()) EmptyState(variant = EmptyStateVariant.Playlist, compact = true)
                else {
                    Text(entries.firstOrNull()?.collectionTitle ?: stringResource(R.string.playlist), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.collection_selected, selected.size, entries.size), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (entries.isNotEmpty()) {
                item { PlaylistRangeControls(entries.size, { draft = it?.intersect(available) }) }
                itemsIndexed(entries) { index, media ->
                    PlaylistEntryRow(media, index, index in selected, index in available) {
                        draft = if (index in selected) selected - index else selected + index
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistRangeControls(count: Int, onSelect: (Set<Int>?) -> Unit) {
    var range by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            TextButton(onClick = { onSelect(null) }) { Text(stringResource(R.string.playlist_all)) }
            TextButton(onClick = { onSelect(emptySet()) }) { Text(stringResource(R.string.playlist_none)) }
        }
        DownloadTextField(range, { range = it; invalid = false }, stringResource(R.string.collection_range), invalid = invalid,
            modifier = Modifier.fillMaxWidth().testTag("playlist-range"), supportingText = if (invalid)
                stringResource(R.string.collection_range_error, count) else stringResource(R.string.collection_range_hint))
        TextButton(onClick = {
            val parsed = CollectionSelection.parse(range, count)
            invalid = parsed == null
            if (parsed != null) onSelect(parsed)
        }) { Text(stringResource(R.string.apply_range)) }
    }
}

@Composable
internal fun PlaylistEntryRow(media: ResolvedMedia, index: Int, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().testTag("playlist-entry-$index")
            .toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onSelect() })
            .padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(selected, onCheckedChange = null, enabled = enabled, modifier = Modifier.size(24.dp))
            PlaylistThumbnail(media, index)
            Column(Modifier.weight(1f)) {
                Text("${index + 1}. ${media.title ?: media.url}", maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall)
                if (media.unavailableReason != null) Text(stringResource(if (media.unavailableReason?.startsWith("Inspect") == true)
                    R.string.collection_nested else R.string.collection_unavailable), style = MaterialTheme.typography.bodySmall)
                else {
                    media.artist?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
                    if (media.durationSeconds > 0) Text(formatClipTime(media.durationSeconds.toDouble()), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
internal fun PlaylistThumbnail(media: ResolvedMedia, index: Int) {
    Box(Modifier.size(width = 80.dp, height = 45.dp).clip(MaterialTheme.shapes.small)
        .background(MaterialTheme.colorScheme.surfaceContainerHighest).testTag("playlist-thumbnail-$index"),
        contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.VideoLibrary, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        AsyncImage(media.thumbnailUrl, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
internal fun SourceFetchPrompt(resolving: Boolean, onFetch: () -> Unit, hint: String = stringResource(R.string.extra_fetch_hint)) {
    if (!resolving) EmptyState(variant = EmptyStateVariant.MediaDetails, description = hint, compact = true,
        action = { TextButton(onClick = onFetch) { Text(stringResource(R.string.fetch)) } })
    else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(hint, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onFetch, enabled = !resolving) {
            Text(stringResource(if (resolving) R.string.resolving_media else R.string.fetch))
        }
    }
}
