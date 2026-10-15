package dev.qtremors.acqua.feature.downloader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.domain.PlaylistLink
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.downloader.CaptionMode
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions

@Composable
internal fun DownloadExtraControls(
    state: DownloaderUiState, onResolve: () -> Unit, onInspect: (Int) -> Unit,
    onPlaylist: (Boolean, Set<Int>?) -> Unit, onContent: (YtDlpDownloadOptions, Set<Int>?) -> Unit
) {
    var opened by rememberSaveable(state.url, state.downloadContentType) { mutableStateOf<String?>(null) }
    val collection = state.media?.any { it.collectionUrl != null } == true
    val playlist = PlaylistLink.isPlaylist(state.url) || collection
    val options = state.advancedOptions
    val processed = state.downloadEngine == DownloadEngine.YT_DLP
    val audio = state.selectionKind() == MediaKind.AUDIO
    val captions = processed && options.captionMode != CaptionMode.NONE
    val enabled = !state.isResolving && !state.isSaving && state.savingItemIndex == null && !state.isBatchRunning &&
        WebLink.extractAll(state.url).size == 1
    val dismiss = { opened = null }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (processed) {
                ExtraChip("playlist", stringResource(R.string.playlist), Icons.AutoMirrored.Outlined.PlaylistPlay,
                    playlist && state.downloadPlaylist, enabled && playlist) { opened = "playlist" }
                ExtraChip("captions", stringResource(R.string.caption_output), Icons.Outlined.Subtitles,
                    captions, enabled) { opened = "captions" }
                ExtraChip("clip", stringResource(if (options.splitChapters) R.string.chapters else R.string.clip), Icons.Outlined.ContentCut,
                    dev.qtremors.acqua.downloader.MediaTimeline.hasSections(options), enabled) { opened = "clip" }
            }
            if (state.selectionKind() != MediaKind.IMAGE) {
                val available = state.media == null || audio || state.media.any { !it.thumbnailUrl.isNullOrBlank() }
                ExtraChip("thumbnail", stringResource(if (audio) R.string.artwork else R.string.thumbnail), Icons.Outlined.Image,
                    options.downloadThumbnail, enabled && available) {
                    onContent(options.copy(downloadThumbnail = !options.downloadThumbnail), state.selectedCollectionItems)
                }
            }
        }
        if (processed && playlist) Text(if (state.downloadPlaylist && collection)
            stringResource(R.string.collection_selected, state.selectedIndices().size, state.media.orEmpty().size)
            else stringResource(if (state.downloadPlaylist) R.string.playlist_detected else R.string.this_video), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val detail = buildList {
            if (captions) add(stringResource(R.string.caption_selection_summary, options.captionLanguages.replace(",", ", ")))
            val start = options.clipStartSeconds
            val end = options.clipEndSeconds
            if (processed && start != null && end != null) add(stringResource(R.string.clip_range_summary, formatClipTime(start), formatClipTime(end)))
            if (processed && options.clipRanges.isNotEmpty()) add(stringResource(R.string.clip_ranges_count, options.clipRanges.size))
            if (processed && options.splitChapters) add(stringResource(R.string.chapters_available, state.selectedSource()?.chapters.orEmpty().size))
            if (options.downloadThumbnail && state.selectionKind() != MediaKind.IMAGE)
                add(stringResource(if (audio) R.string.download_artwork_summary else R.string.download_thumbnail_summary))
        }.joinToString(" · ")
        if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    when (opened) {
        "playlist" -> PlaylistPickerDialog(state, onResolve, dismiss) { scope, indices -> onPlaylist(scope, indices); dismiss() }
        "captions" -> CaptionPickerDialog(state, onResolve, onInspect, dismiss) { onContent(it, state.selectedCollectionItems); dismiss() }
        "clip" -> ClipPickerDialog(state, onResolve, onInspect, dismiss) { options, indices -> onContent(options, indices); dismiss() }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExtraChip(tag: String, label: String, icon: ImageVector, selected: Boolean, enabled: Boolean, onOpen: () -> Unit) {
    ToggleButton(checked = selected, onCheckedChange = { onOpen() }, enabled = enabled,
        modifier = Modifier.testTag("download-extra-$tag"), shapes = ToggleButtonDefaults.shapesFor(48.dp),
        icon = { Icon(if (selected) Icons.Filled.Check else icon, null, Modifier.size(18.dp)) }) {
        Text(label)
    }
}
