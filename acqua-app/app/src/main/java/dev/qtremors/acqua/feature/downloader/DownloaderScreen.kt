package dev.qtremors.acqua.feature.downloader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.R
import dev.qtremors.acqua.data.network.MediaDownloader
import dev.qtremors.acqua.data.network.MediaPreviewCache
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaResolutionFailure
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.downloader.DownloadQueueState
import dev.qtremors.acqua.downloader.YtDlpFormatSelector.qualityDimension
import dev.qtremors.acqua.platform.HapticSignal
import dev.qtremors.acqua.platform.performHaptic
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        if (state.activeDownloadCount > 0) {
            CompactDownloadStatus(
                state = state,
                onCancel = viewModel::cancelActiveDownload,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
            )
        }
        if (showLinkEditor) {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp), RoundedCornerShape(24.dp),
                CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh)
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        stringResource(R.string.paste_media_link),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = state.url,
                        onValueChange = viewModel::updateUrl,
                        label = { Text(stringResource(R.string.media_link)) },
                        placeholder = { Text(stringResource(R.string.media_link_hint)) },
                        trailingIcon = {
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                                    ?.takeIf(String::isNotEmpty)?.let(viewModel::updateUrl)
                            }) { Icon(Icons.Filled.ContentPaste, stringResource(R.string.paste), tint = colors.primary) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        enabled = !state.isSaving,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            focusedLabelColor = colors.primary,
                            cursorColor = colors.primary
                        )
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        when (state.validity) {
            LinkValidity.EMPTY -> LinkMessageCard(
                icon = { Icon(Icons.Filled.Download, stringResource(R.string.ready), Modifier.size(30.dp)) },
                title = stringResource(R.string.ready_to_download),
                message = stringResource(R.string.supported_link_guidance),
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            LinkValidity.INVALID -> LinkMessageCard(
                error = true,
                icon = { Icon(Icons.Filled.Warning, stringResource(R.string.invalid), Modifier.size(30.dp)) },
                title = stringResource(R.string.invalid_link),
                message = stringResource(R.string.valid_link_guidance),
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            LinkValidity.VALID -> ValidLinkContent(
                state = state,
                previewCache = previewCache,
                useBrowserSessions = useBrowserSessions,
                canResolve = sessionsInitialized,
                onResolve = { viewModel.resolve(useBrowserSessions, browserRequestRevision, resolveInBrowser) },
                onDownloadAll = {
                    requestDownloadAccess(true) {
                        viewModel.downloadAll(useBrowserSessions, resolveInBrowser)
                    }
                },
                onDownloadOne = { item, index, width, height ->
                    requestDownloadAccess(true) { viewModel.downloadOne(item, index, width, height) }
                },
                onEngineChange = viewModel::setDownloadEngine,
                onContentTypeChange = viewModel::setDownloadContentType,
                onVideoHeightChange = viewModel::setMaximumVideoHeight,
                onAudioFormatChange = viewModel::setAudioFormat,
                onEmbedMetadataChange = viewModel::setEmbedMetadata,
                onEmbedThumbnailChange = viewModel::setEmbedThumbnail,
                onDismissError = viewModel::dismissError,
                onCopyError = { error ->
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("AcquaError", error))
                },
                onOpenBrowser = { onOpenBrowser(state.url) }
            )
        }
        Spacer(Modifier.height(24.dp))
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
    Card(
        modifier.fillMaxWidth(), RoundedCornerShape(24.dp),
        CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(60.dp).clip(CircleShape)
                    .background(if (error) colors.errorContainer else colors.primaryContainer),
                contentAlignment = Alignment.Center
            ) { icon() }
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(6.dp))
            Text(message, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ValidLinkContent(
    state: DownloaderUiState,
    previewCache: MediaPreviewCache,
    useBrowserSessions: Boolean,
    canResolve: Boolean,
    onResolve: () -> Unit,
    onDownloadAll: () -> Unit,
    onDownloadOne: (ResolvedMedia, Int, Int, Int) -> Unit,
    onEngineChange: (DownloadEngine) -> Unit,
    onContentTypeChange: (DownloadContentType) -> Unit,
    onVideoHeightChange: (Int) -> Unit,
    onAudioFormatChange: (AudioOutputFormat) -> Unit,
    onEmbedMetadataChange: (Boolean) -> Unit,
    onEmbedThumbnailChange: (Boolean) -> Unit,
    onDismissError: () -> Unit,
    onCopyError: (String) -> Unit,
    onOpenBrowser: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        RoundedCornerShape(24.dp),
        CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.download_engine),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                stringResource(
                    if (state.downloadEngine == DownloadEngine.YT_DLP) {
                        R.string.ytdlp_engine_explanation
                    } else {
                        R.string.acqua_engine_explanation
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.downloadEngine == DownloadEngine.ACQUA,
                    onClick = { onEngineChange(DownloadEngine.ACQUA) },
                    label = { Text(stringResource(R.string.acqua_engine)) },
                    enabled = !state.isSaving && state.savingItemIndex == null
                )
                FilterChip(
                    selected = state.downloadEngine == DownloadEngine.YT_DLP,
                    onClick = { onEngineChange(DownloadEngine.YT_DLP) },
                    label = { Text(stringResource(R.string.ytdlp_engine)) },
                    enabled = !state.isSaving && state.savingItemIndex == null
                )
            }
        }
    }
    val ytDlpMedia = state.media?.singleOrNull()?.takeIf { it.backend == MediaBackend.YT_DLP }
    if (state.downloadEngine == DownloadEngine.YT_DLP) {
        YtDlpDownloadControls(
            state = state,
            media = ytDlpMedia,
            onContentTypeChange = onContentTypeChange,
            onVideoHeightChange = onVideoHeightChange,
            onAudioFormatChange = onAudioFormatChange,
            onEmbedMetadataChange = onEmbedMetadataChange,
            onEmbedThumbnailChange = onEmbedThumbnailChange
        )
    }
    if (state.media == null) {
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            RoundedCornerShape(24.dp),
            CardDefaults.cardColors(containerColor = colors.primaryContainer)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    stringResource(R.string.fetch_preview_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryContainer
                )
                Button(
                    onClick = onResolve,
                    enabled = canResolve && !state.isResolving,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (state.isResolving) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = colors.onPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                    }
                    Text(stringResource(R.string.fetch_preview), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (state.media != null) Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), RoundedCornerShape(24.dp),
        CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh)
    ) {
        Button(
            onClick = onDownloadAll,
            modifier = Modifier.fillMaxWidth().padding(24.dp).height(52.dp),
            enabled = !state.isResolving && !state.isSaving && !state.saved &&
                state.savingItemIndex == null,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
        ) {
            when {
                state.isSaving -> {
                    CircularProgressIndicator(Modifier.size(20.dp), color = colors.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.size(12.dp))
                    val total = state.media?.size ?: 0
                    Text(
                        if (total > 1) pluralStringResource(
                            R.plurals.saving_progress,
                            total,
                            state.savingIndex,
                            total
                        ) else stringResource(R.string.saving)
                    )
                }
                state.saved -> Text(stringResource(R.string.saved_to_downloads), fontWeight = FontWeight.Bold)
                else -> {
                    Icon(Icons.Filled.Download, stringResource(R.string.download))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(
                            if (ytDlpMedia != null && state.downloadContentType == DownloadContentType.AUDIO) {
                                R.string.download_audio
                            } else {
                                R.string.download_media
                            }
                        ),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
    state.media?.let { media ->
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    if (media.size > 1) pluralStringResource(
                        R.plurals.files_found,
                        media.size,
                        media.size
                    ) else stringResource(R.string.preview),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(10.dp))
            }
            val pagerState = rememberPagerState(pageCount = { media.size })
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { index ->
                    val item = media[index]
                        MediaPreviewCard(
                            item, index, useBrowserSessions, previewCache,
                            contentType = state.downloadContentType,
                            audioFormat = state.audioFormat,
                            isSaving = state.savingItemIndex == index,
                            isSaved = state.savedItemIndex == index,
                            downloadsEnabled = !state.isSaving && state.savingItemIndex == null,
                            onDownload = { width, height -> onDownloadOne(item, index, width, height) },
                            modifier = Modifier.fillMaxWidth()
                        )
            }
            if (media.size > 1) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (media.size <= MAX_PREVIEW_DOTS) {
                        media.indices.forEach { page ->
                            Box(
                                Modifier.padding(horizontal = 3.dp)
                                    .size(if (pagerState.currentPage == page) 18.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (pagerState.currentPage == page) colors.primary else colors.outlineVariant
                                    )
                            )
                        }
                    } else {
                        Text(
                            stringResource(R.string.preview_page_count, pagerState.currentPage + 1, media.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    state.error?.let { error ->
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), RoundedCornerShape(18.dp),
            CardDefaults.cardColors(containerColor = colors.errorContainer)
        ) {
            Column(Modifier.padding(16.dp)) {
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
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        IconButton({ onCopyError(error) }) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.copy_error)) }
                        TextButton(onDismissError) { Text(stringResource(R.string.dismiss)) }
                    }
                }
                SelectionContainer { Text(error, style = MaterialTheme.typography.bodySmall) }
                if (state.showBrowserAction) {
                    Button(onOpenBrowser, Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Icon(Icons.Filled.Lock, stringResource(R.string.open_browser))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.use_browser))
                    }
                }
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
    Card(
        modifier.fillMaxWidth(),
        RoundedCornerShape(20.dp),
        CardDefaults.cardColors(containerColor = colors.secondaryContainer)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                WavyCircularProgress(
                    progress = (state.downloadProgress / 100f).coerceIn(0f, 1f),
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    "${state.downloadProgress.coerceIn(0f, 100f).toInt()}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    pluralStringResource(
                        R.plurals.active_downloads,
                        state.activeDownloadCount,
                        state.activeDownloadCount
                    ),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    listOf(status, details).filter(String::isNotEmpty).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSecondaryContainer
                )
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, stringResource(R.string.cancel_download))
            }
        }
    }
}

@Composable
private fun WavyCircularProgress(progress: Float, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "download wave")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "download wave rotation"
    )
    Canvas(modifier) {
        val stroke = 2.5.dp.toPx()
        val centerRadius = (size.minDimension - stroke * 3f) / 2f
        drawCircle(
            color = color.copy(alpha = 0.16f),
            radius = centerRadius,
            style = Stroke(stroke)
        )
        val sweep = if (progress > 0f) progress.coerceAtLeast(0.04f) * 2f * PI.toFloat() else 2f * PI.toFloat()
        val samples = 72
        val path = Path()
        for (step in 0..samples) {
            val fraction = step / samples.toFloat()
            val angle = -PI.toFloat() / 2f + Math.toRadians(rotation.toDouble()).toFloat() + sweep * fraction
            val ripple = sin(fraction * 12f * PI.toFloat()) * stroke * 0.65f
            val radius = centerRadius + ripple
            val x = center.x + cos(angle) * radius
            val y = center.y + sin(angle) * radius
            if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(color = color, path = path, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

private const val MAX_PREVIEW_DOTS = 7

@Composable
private fun YtDlpDownloadControls(
    state: DownloaderUiState,
    media: ResolvedMedia?,
    onContentTypeChange: (DownloadContentType) -> Unit,
    onVideoHeightChange: (Int) -> Unit,
    onAudioFormatChange: (AudioOutputFormat) -> Unit,
    onEmbedMetadataChange: (Boolean) -> Unit,
    onEmbedThumbnailChange: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val availableHeights = media?.formats.orEmpty().asSequence()
        .filter { it.hasVideo && it.qualityDimension > 0 }
        .map { it.qualityDimension }
        .distinct()
        .sortedDescending()
        .toList()
        .ifEmpty { listOf(2160, 1440, 1080, 720, 480, 360) }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        RoundedCornerShape(24.dp),
        CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.download_options),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(10.dp))
            media?.title?.let {
                Text(it, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                media.username?.let { author ->
                    Text(author, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Spacer(Modifier.height(14.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.downloadContentType == DownloadContentType.VIDEO,
                    onClick = { onContentTypeChange(DownloadContentType.VIDEO) },
                    label = { Text(stringResource(R.string.video)) },
                    leadingIcon = { Icon(Icons.Filled.Movie, null, Modifier.size(18.dp)) }
                )
                FilterChip(
                    selected = state.downloadContentType == DownloadContentType.AUDIO,
                    onClick = { onContentTypeChange(DownloadContentType.AUDIO) },
                    label = { Text(stringResource(R.string.audio)) },
                    leadingIcon = { Icon(Icons.Filled.MusicNote, null, Modifier.size(18.dp)) }
                )
            }
            if (state.downloadContentType == DownloadContentType.VIDEO) {
                Text(
                    stringResource(R.string.quality),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioOutputFormat.entries.forEach { format ->
                        FilterChip(
                            selected = state.audioFormat == format,
                            onClick = { onAudioFormatChange(format) },
                            label = {
                                Text(
                                    when (format) {
                                        AudioOutputFormat.ORIGINAL -> stringResource(R.string.original_audio)
                                        AudioOutputFormat.M4A -> "M4A"
                                        AudioOutputFormat.MP3 -> "MP3"
                                    }
                                )
                            }
                        )
                    }
                }
            }
            DownloadOptionSwitch(
                title = stringResource(R.string.embed_metadata),
                checked = state.embedMetadata,
                onCheckedChange = onEmbedMetadataChange
            )
            DownloadOptionSwitch(
                title = stringResource(R.string.embed_thumbnail),
                checked = state.embedThumbnail,
                onCheckedChange = onEmbedThumbnailChange
            )
        }
    }
}

@Composable
private fun DownloadOptionSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onCheckedChange)
    }
}
