package dev.qtremors.acqua.feature.downloader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import dev.qtremors.acqua.ui.components.EmptyState
import dev.qtremors.acqua.ui.components.EmptyStateVariant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import dev.qtremors.acqua.ui.components.ExpressiveSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.data.network.HttpMediaClient
import dev.qtremors.acqua.data.network.MediaPreviewCache
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.MediaResolutionFailure
import dev.qtremors.acqua.downloader.DownloadFailure
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.downloader.DownloadQueueState
import dev.qtremors.acqua.downloader.YtDlpFormatSelector.qualityDimension
import dev.qtremors.acqua.platform.HapticSignal
import dev.qtremors.acqua.platform.performHaptic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadConfigBottomSheet(
    state: DownloaderUiState,
    canResolve: Boolean,
    onDismiss: () -> Unit,
    onResolve: () -> Unit,
    onEngineChange: (DownloadEngine) -> Unit,
    onContentTypeChange: (DownloadContentType) -> Unit,
    onVideoHeightChange: (Int) -> Unit,
    onAudioFormatChange: (AudioOutputFormat) -> Unit,
    onEmbedMetadataChange: (Boolean) -> Unit,
    onEmbedThumbnailChange: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val ytDlpMedia = state.media?.singleOrNull()?.takeIf { it.backend == MediaBackend.YT_DLP }
    val availableHeights = ytDlpMedia?.formats.orEmpty().asSequence()
        .filter { it.hasVideo && it.qualityDimension > 0 }
        .map { it.qualityDimension }
        .distinct()
        .sortedDescending()
        .toList()
        .ifEmpty { listOf(2160, 1440, 1080, 720, 480, 360) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        ),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = colors.surfaceContainerLow
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.download_options),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, stringResource(R.string.close))
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.download_engine),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(8.dp))
            ExpressiveSegmentedButtonRow(
                items = listOf(DownloadEngine.ACQUA, DownloadEngine.YT_DLP),
                selectedIndex = if (state.downloadEngine == DownloadEngine.ACQUA) 0 else 1,
                onSelect = { index ->
                    onEngineChange(if (index == 0) DownloadEngine.ACQUA else DownloadEngine.YT_DLP)
                },
                label = { engine ->
                    when (engine) {
                        DownloadEngine.ACQUA -> stringResource(R.string.acqua_engine)
                        DownloadEngine.YT_DLP -> stringResource(R.string.ytdlp_engine)
                    }
                },
                leadingIcon = { engine, _ ->
                    when (engine) {
                        DownloadEngine.ACQUA -> Icon(Icons.Filled.Download, null, Modifier.size(16.dp))
                        DownloadEngine.YT_DLP -> Icon(Icons.Filled.Tune, null, Modifier.size(16.dp))
                    }
                }
            )

            Spacer(Modifier.height(10.dp))

            if (state.downloadEngine == DownloadEngine.ACQUA) {
                Text(
                    stringResource(R.string.acqua_engine_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )
            } else {
                Text(
                    stringResource(R.string.ytdlp_engine_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )

                Text(
                    stringResource(R.string.video) + " / " + stringResource(R.string.audio),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(8.dp))
                ExpressiveSegmentedButtonRow(
                    items = listOf(DownloadContentType.VIDEO, DownloadContentType.AUDIO),
                    selectedIndex = if (state.downloadContentType == DownloadContentType.VIDEO) 0 else 1,
                    onSelect = { index ->
                        onContentTypeChange(if (index == 0) DownloadContentType.VIDEO else DownloadContentType.AUDIO)
                    },
                    label = { type ->
                        when (type) {
                            DownloadContentType.VIDEO -> stringResource(R.string.video)
                            DownloadContentType.AUDIO -> stringResource(R.string.audio)
                        }
                    },
                    leadingIcon = { type, _ ->
                        when (type) {
                            DownloadContentType.VIDEO -> Icon(Icons.Filled.Movie, null, Modifier.size(18.dp))
                            DownloadContentType.AUDIO -> Icon(Icons.Filled.MusicNote, null, Modifier.size(18.dp))
                        }
                    }
                )

            if (state.downloadContentType == DownloadContentType.VIDEO) {
                Text(
                    stringResource(R.string.quality),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.maximumVideoHeight == 0,
                        onClick = { onVideoHeightChange(0) },
                        label = { Text(stringResource(R.string.best)) }
                    )
                    availableHeights.forEach { height ->
                        FilterChip(
                            selected = state.maximumVideoHeight == height,
                            onClick = { onVideoHeightChange(height) },
                            label = { Text("${height}p") }
                        )
                    }
                }
            } else {
                Text(
                        stringResource(R.string.audio_format),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                    )
                    ExpressiveSegmentedButtonRow(
                        items = AudioOutputFormat.entries,
                        selectedIndex = AudioOutputFormat.entries.indexOf(state.audioFormat).coerceAtLeast(0),
                        onSelect = { index ->
                            onAudioFormatChange(AudioOutputFormat.entries[index])
                        },
                        label = { format ->
                            when (format) {
                                AudioOutputFormat.ORIGINAL -> stringResource(R.string.original_audio)
                                AudioOutputFormat.M4A -> "M4A"
                                AudioOutputFormat.MP3 -> "MP3"
                            }
                        }
                    )
                }

                HorizontalDivider(Modifier.padding(vertical = 12.dp))

                ListItem(
                    supportingContent = { Text(stringResource(R.string.embed_metadata_explanation), style = MaterialTheme.typography.bodySmall) },
                    trailingContent = { Switch(checked = state.embedMetadata, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics { }) },
                    modifier = Modifier.toggleable(state.embedMetadata, role = Role.Switch, onValueChange = onEmbedMetadataChange)
                        .semantics(mergeDescendants = true) { },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                ) {
                    Text(stringResource(R.string.embed_metadata), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                }
                ListItem(
                    supportingContent = { Text(stringResource(R.string.embed_thumbnail_explanation), style = MaterialTheme.typography.bodySmall) },
                    trailingContent = { Switch(checked = state.embedThumbnail, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics { }) },
                    modifier = Modifier.toggleable(state.embedThumbnail, role = Role.Switch, onValueChange = onEmbedThumbnailChange)
                        .semantics(mergeDescendants = true) { },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                ) {
                    Text(stringResource(R.string.embed_thumbnail), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    onDismiss()
                    onResolve()
                },
                enabled = canResolve && !state.isResolving,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (state.isResolving) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = colors.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                } else {
                    Icon(Icons.Filled.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    stringResource(if (state.isResolving) R.string.resolving_media else R.string.fetch),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
internal fun downloadProgressDetails(
    downloadedBytes: Long,
    totalBytes: Long,
    etaSeconds: Long
): String {
    val context = LocalContext.current
    val size = when {
        downloadedBytes > 0L && totalBytes > 0L -> stringResource(
            R.string.download_size_progress,
            Formatter.formatShortFileSize(context, downloadedBytes),
            Formatter.formatShortFileSize(context, totalBytes)
        )
        downloadedBytes > 0L -> stringResource(
            R.string.download_size_downloaded,
            Formatter.formatShortFileSize(context, downloadedBytes)
        )
        else -> null
    }
    val eta = if (etaSeconds > 0L) pluralStringResource(
        R.plurals.download_eta,
        etaSeconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        etaSeconds
    ) else null
    return listOfNotNull(size, eta).joinToString(" · ")
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CompactDownloadStatus(
    state: DownloaderUiState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val activeItem = state.downloadQueue.activeItems.firstOrNull()
    val status = activeItem?.let {
        stringResource(
            when (it.state) {
                DownloadQueueState.QUEUED -> R.string.download_queued
                DownloadQueueState.RETRYING -> R.string.download_retrying
                else -> R.string.download_running
            }
        )
    } ?: stringResource(R.string.download_running)
    val details = downloadProgressDetails(
        state.downloadedBytes,
        state.totalBytes,
        state.downloadEtaSeconds
    )
    val progress = (state.downloadProgress / 100f).coerceIn(0f, 1f)

    ElevatedCard(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth().semantics {
            liveRegion = LiveRegionMode.Polite
            stateDescription = status
        },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = colors.secondaryContainer)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(38.dp).semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f, 0)
                    },
                    color = colors.primary,
                    trackColor = colors.onSecondaryContainer.copy(alpha = 0.16f)
                )
                Text(
                    "${state.downloadProgress.coerceIn(0f, 100f).toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 8.sp),
                    color = colors.onSecondaryContainer
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        pluralStringResource(
                            R.plurals.active_downloads,
                            state.activeDownloadCount,
                            state.activeDownloadCount
                        ),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSecondaryContainer
                    )
                    if (status.isNotEmpty()) {
                        Text(
                            "· $status",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
                if (details.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        details,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSecondaryContainer.copy(alpha = 0.85f),
                        maxLines = 2
                    )
                }
            }
            IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Close,
                    stringResource(R.string.cancel_download),
                    tint = colors.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
