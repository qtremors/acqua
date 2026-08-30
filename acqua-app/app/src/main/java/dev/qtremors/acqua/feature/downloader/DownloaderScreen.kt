package dev.qtremors.acqua.feature.downloader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.qtremors.acqua.R
import dev.qtremors.acqua.data.network.MediaDownloader
import dev.qtremors.acqua.data.network.MediaPreviewCache
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.MediaResolutionFailure
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.downloader.DownloadQueueState
import dev.qtremors.acqua.downloader.YtDlpFormatSelector.qualityDimension
import dev.qtremors.acqua.platform.HapticSignal
import dev.qtremors.acqua.platform.performHaptic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderScreen(
    viewModel: DownloaderViewModel,
    mediaDownloader: MediaDownloader,
    useBrowserSessions: Boolean,
    sessionsInitialized: Boolean,
    browserRequestRevision: Int,
    resolveInBrowser: suspend (String, Boolean) -> List<ResolvedMedia>,
    requestDownloadAccess: (needsNotification: Boolean, action: () -> Unit) -> Unit,
    onOpenBrowser: (String) -> Unit,
    modifier: Modifier = Modifier,
    onMediaSaved: () -> Unit = {},
    showLinkEditor: Boolean = true
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val currentOnMediaSaved by rememberUpdatedState(onMediaSaved)
    val colors = MaterialTheme.colorScheme
    val previewCache = remember(mediaDownloader) { MediaPreviewCache(context, mediaDownloader) }
    var showConfigSheet by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                DownloaderEvent.ResolutionComplete -> context.performHaptic(HapticSignal.CLICK)
                DownloaderEvent.DownloadComplete -> {
                    context.performHaptic(HapticSignal.COMPLETE)
                    currentOnMediaSaved()
                }
                DownloaderEvent.ItemSaved -> {
                    context.performHaptic(HapticSignal.COMPLETE)
                    Toast.makeText(context, R.string.saved_to_downloads, Toast.LENGTH_SHORT).show()
                    currentOnMediaSaved()
                }
                is DownloaderEvent.ItemSaveFailed -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showConfigSheet) {
        DownloadConfigBottomSheet(
            state = state,
            canResolve = sessionsInitialized,
            onDismiss = { showConfigSheet = false },
            onResolve = { viewModel.resolve(useBrowserSessions, browserRequestRevision, resolveInBrowser) },
            onEngineChange = viewModel::setDownloadEngine,
            onContentTypeChange = viewModel::setDownloadContentType,
            onVideoHeightChange = viewModel::setMaximumVideoHeight,
            onAudioFormatChange = viewModel::setAudioFormat,
            onEmbedMetadataChange = viewModel::setEmbedMetadata,
            onEmbedThumbnailChange = viewModel::setEmbedThumbnail
        )
    }

    Column(
        modifier = modifier.fillMaxSize().imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            when {
                state.validity == LinkValidity.EMPTY -> LinkMessageCard(
                    icon = { Icon(Icons.Filled.Download, stringResource(R.string.ready), Modifier.size(24.dp), tint = colors.onPrimaryContainer) },
                    title = stringResource(R.string.ready_to_download),
                    message = stringResource(R.string.supported_link_guidance),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                state.validity == LinkValidity.INVALID -> LinkMessageCard(
                    error = true,
                    icon = { Icon(Icons.Filled.Warning, stringResource(R.string.invalid), Modifier.size(24.dp), tint = colors.onErrorContainer) },
                    title = stringResource(R.string.invalid_link),
                    message = stringResource(R.string.valid_link_guidance),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                else -> MediaContentSection(
                    state = state,
                    previewCache = previewCache,
                    onDownloadOne = { item, index, width, height ->
                        requestDownloadAccess(true) { viewModel.downloadOne(item, index, width, height) }
                    },
                    onDismissError = viewModel::dismissError,
                    onCopyError = { error ->
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("AcquaError", error))
                    },
                    onOpenBrowser = { onOpenBrowser(state.url) },
                    useBrowserSessions = useBrowserSessions
                )
            }
        }

        if (state.activeDownloadCount > 0) {
            CompactDownloadStatus(
                state = state,
                onCancel = viewModel::cancelActiveDownload,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        BottomControlsSection(
            state = state,
            showLinkEditor = showLinkEditor,
            canResolve = sessionsInitialized,
            onUrlChange = viewModel::updateUrl,
            onOpenConfig = { showConfigSheet = true },
            onResolve = { viewModel.resolve(useBrowserSessions, browserRequestRevision, resolveInBrowser) },
            onDownloadAll = {
                requestDownloadAccess(true) {
                    viewModel.downloadAll(useBrowserSessions, resolveInBrowser)
                }
            }
        )
    }
}

@Composable
private fun LinkMessageCard(
    error: Boolean = false,
    icon: @Composable () -> Unit,
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (error) colors.errorContainer.copy(alpha = 0.4f) else colors.surfaceContainerLow
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                Modifier.size(44.dp),
                shape = CircleShape,
                color = if (error) colors.errorContainer else colors.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) { icon() }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (error) colors.error else colors.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MediaContentSection(
    state: DownloaderUiState,
    previewCache: MediaPreviewCache,
    onDownloadOne: (ResolvedMedia, Int, Int, Int) -> Unit,
    onDismissError: () -> Unit,
    onCopyError: (String) -> Unit,
    onOpenBrowser: () -> Unit,
    useBrowserSessions: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        state.media?.let { media ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = colors.surfaceContainerLow)
            ) {
                Column(Modifier.padding(12.dp)) {
                    val isMulti = media.size > 1
                    val loopMultiplier = 1000
                    val pageCount = if (isMulti) media.size * loopMultiplier else 1
                    val initialPage = if (isMulti) (loopMultiplier / 2) * media.size else 0
                    val pagerState = rememberPagerState(
                        initialPage = initialPage,
                        pageCount = { pageCount }
                    )
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth(),
                        pageSpacing = 12.dp
                    ) { page ->
                        val index = page % media.size
                        val item = media[index]
                        MediaPreviewCard(
                            item = item,
                            index = index,
                            useBrowserSessions = useBrowserSessions,
                            previewCache = previewCache,
                            contentType = state.downloadContentType,
                            audioFormat = state.audioFormat,
                            isSaving = state.savingItemIndex == index,
                            isSaved = state.savedItemIndex == index,
                            downloadsEnabled = !state.isSaving && state.savingItemIndex == null,
                            onDownload = { width, height -> onDownloadOne(item, index, width, height) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (isMulti) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.preview_page_count, (pagerState.currentPage % media.size) + 1, media.size),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.onSurfaceVariant
                            )
                        }
                    }

                    val singleMedia = media.firstOrNull()
                    singleMedia?.title?.let { title ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        singleMedia.username?.let { author ->
                            Text(
                                author,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        if (state.media == null && state.error == null) {
            LinkMessageCard(
                icon = { Icon(Icons.Filled.Download, stringResource(R.string.ready), Modifier.size(24.dp), tint = colors.onPrimaryContainer) },
                title = stringResource(R.string.ready_to_download),
                message = stringResource(R.string.supported_link_guidance),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        state.error?.let { error ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = colors.errorContainer)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(
                            stringResource(
                                when (state.resolutionFailure) {
                                    MediaResolutionFailure.INVALID_LINK -> R.string.invalid_link
                                    MediaResolutionFailure.SESSION_REQUIRED,
                                    MediaResolutionFailure.SESSION_EXPIRED -> R.string.browser_session_needed
                                    MediaResolutionFailure.NETWORK -> R.string.network_error
                                    MediaResolutionFailure.ENGINE_REQUIRED,
                                    MediaResolutionFailure.RUNTIME_UNAVAILABLE -> R.string.download_engine_error
                                    else -> R.string.could_not_resolve_media
                                }
                            ),
                            fontWeight = FontWeight.Bold,
                            color = colors.onErrorContainer
                        )
                        Row {
                            IconButton({ onCopyError(error) }) {
                                Icon(Icons.Filled.ContentCopy, stringResource(R.string.copy_error), tint = colors.onErrorContainer)
                            }
                            TextButton(onDismissError) {
                                Text(stringResource(R.string.dismiss), color = colors.onErrorContainer)
                            }
                        }
                    }
                    SelectionContainer {
                        Text(error, style = MaterialTheme.typography.bodySmall, color = colors.onErrorContainer)
                    }
                    if (state.showBrowserAction) {
                        Button(onOpenBrowser, Modifier.fillMaxWidth().padding(top = 10.dp)) {
                            Icon(Icons.Filled.Lock, stringResource(R.string.open_browser))
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.use_browser))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomControlsSection(
    state: DownloaderUiState,
    showLinkEditor: Boolean,
    canResolve: Boolean,
    onUrlChange: (String) -> Unit,
    onOpenConfig: () -> Unit,
    onResolve: () -> Unit,
    onDownloadAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val ytDlpMedia = state.media?.singleOrNull()?.takeIf { it.backend == MediaBackend.YT_DLP }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surface,
        tonalElevation = 3.dp
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showLinkEditor) {
                OutlinedTextField(
                    value = state.url,
                    onValueChange = onUrlChange,
                    placeholder = {
                        Text(
                            stringResource(R.string.media_link_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Link,
                            stringResource(R.string.media_link),
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            if (state.url.isNotEmpty()) {
                                IconButton(
                                    onClick = { onUrlChange("") },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        stringResource(R.string.clear_link),
                                        tint = colors.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                                        ?.takeIf(String::isNotEmpty)?.let(onUrlChange)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.ContentPaste,
                                    stringResource(R.string.paste),
                                    tint = colors.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true,
                    maxLines = 1,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                    enabled = !state.isSaving,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = colors.surfaceContainerHigh,
                        unfocusedContainerColor = colors.surfaceContainerHigh
                    )
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = state.downloadEngine == DownloadEngine.YT_DLP,
                    onClick = onOpenConfig,
                    label = {
                        Text(
                            if (state.downloadEngine == DownloadEngine.YT_DLP) {
                                buildYtDlpSummary(state)
                            } else {
                                stringResource(R.string.acqua_engine)
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (state.downloadEngine == DownloadEngine.YT_DLP) Icons.Filled.Tune else Icons.Filled.Download,
                            contentDescription = stringResource(R.string.download_engine),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(48.dp),
                    enabled = !state.isSaving && state.savingItemIndex == null
                )

                if (state.media == null) {
                    Button(
                        onClick = onResolve,
                        enabled = state.validity == LinkValidity.VALID && canResolve && !state.isResolving,
                        modifier = Modifier.weight(1f).height(48.dp),
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
                            stringResource(if (state.isResolving) R.string.resolving_media else R.string.fetch_preview),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Button(
                        onClick = onDownloadAll,
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = !state.isResolving && !state.isSaving && !state.saved && state.savingItemIndex == null,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        when {
                            state.isSaving -> {
                                CircularProgressIndicator(Modifier.size(18.dp), color = colors.onPrimary, strokeWidth = 2.dp)
                                Spacer(Modifier.size(8.dp))
                                val total = state.media.size
                                Text(
                                    if (total > 1) pluralStringResource(
                                        R.plurals.saving_progress,
                                        total,
                                        state.savingIndex,
                                        total
                                    ) else stringResource(R.string.saving),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            state.saved -> {
                                Icon(Icons.Filled.Download, null, Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.saved_to_downloads), fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                            else -> {
                                Icon(Icons.Filled.Download, null, Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                val label = when {
                                    state.media.size > 1 -> stringResource(R.string.download_all_count, state.media.size)
                                    ytDlpMedia != null && state.downloadContentType == DownloadContentType.AUDIO -> stringResource(R.string.download_audio)
                                    state.media.firstOrNull()?.kind == MediaKind.AUDIO -> stringResource(R.string.download_audio)
                                    state.media.firstOrNull()?.isVideo == true -> stringResource(R.string.download_video)
                                    else -> stringResource(R.string.download_image)
                                }
                                Text(
                                    label,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun buildYtDlpSummary(state: DownloaderUiState): String {
    val type = if (state.downloadContentType == DownloadContentType.VIDEO) {
        stringResource(R.string.video)
    } else {
        stringResource(R.string.audio)
    }
    val detail = if (state.downloadContentType == DownloadContentType.VIDEO) {
        if (state.maximumVideoHeight == 0) stringResource(R.string.best) else "${state.maximumVideoHeight}p"
    } else {
        when (state.audioFormat) {
            AudioOutputFormat.ORIGINAL -> stringResource(R.string.original_audio)
            AudioOutputFormat.M4A -> "M4A"
            AudioOutputFormat.MP3 -> "MP3"
        }
    }
    return "${stringResource(R.string.ytdlp_engine)} · $type ($detail)"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadConfigBottomSheet(
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
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.downloadEngine == DownloadEngine.ACQUA,
                    onClick = { onEngineChange(DownloadEngine.ACQUA) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text(
                        stringResource(R.string.acqua_engine),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                SegmentedButton(
                    selected = state.downloadEngine == DownloadEngine.YT_DLP,
                    onClick = { onEngineChange(DownloadEngine.YT_DLP) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Filled.Tune, null, Modifier.size(16.dp)) }
                ) {
                    Text(
                        stringResource(R.string.ytdlp_engine),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }

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
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = state.downloadContentType == DownloadContentType.VIDEO,
                        onClick = { onContentTypeChange(DownloadContentType.VIDEO) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = { Icon(Icons.Filled.Movie, null, Modifier.size(18.dp)) }
                    ) {
                        Text(stringResource(R.string.video))
                    }
                    SegmentedButton(
                        selected = state.downloadContentType == DownloadContentType.AUDIO,
                        onClick = { onContentTypeChange(DownloadContentType.AUDIO) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { Icon(Icons.Filled.MusicNote, null, Modifier.size(18.dp)) }
                    ) {
                        Text(stringResource(R.string.audio))
                    }
                }

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
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        AudioOutputFormat.entries.forEachIndexed { index, format ->
                            SegmentedButton(
                                selected = state.audioFormat == format,
                                onClick = { onAudioFormatChange(format) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = AudioOutputFormat.entries.size)
                            ) {
                                Text(
                                    when (format) {
                                        AudioOutputFormat.ORIGINAL -> stringResource(R.string.original_audio)
                                        AudioOutputFormat.M4A -> "M4A"
                                        AudioOutputFormat.MP3 -> "MP3"
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 12.dp))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.embed_metadata), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)) },
                    supportingContent = { Text(stringResource(R.string.embed_metadata_explanation), style = MaterialTheme.typography.bodySmall) },
                    trailingContent = { Switch(checked = state.embedMetadata, onCheckedChange = onEmbedMetadataChange) },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.embed_thumbnail), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)) },
                    supportingContent = { Text(stringResource(R.string.embed_thumbnail_explanation), style = MaterialTheme.typography.bodySmall) },
                    trailingContent = { Switch(checked = state.embedThumbnail, onCheckedChange = onEmbedThumbnailChange) },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
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
                    stringResource(if (state.isResolving) R.string.resolving_media else R.string.fetch_preview),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun downloadProgressDetails(
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
private fun CompactDownloadStatus(
    state: DownloaderUiState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
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
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = colors.secondaryContainer)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(44.dp),
                    color = colors.primary,
                    trackColor = colors.onSecondaryContainer.copy(alpha = 0.16f)
                )
                Text(
                    "${state.downloadProgress.coerceIn(0f, 100f).toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
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
