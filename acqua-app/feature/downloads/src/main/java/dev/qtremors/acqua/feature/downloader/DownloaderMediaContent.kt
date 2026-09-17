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
import androidx.compose.material3.rememberModalBottomSheetState
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
internal fun MediaContentSection(
    state: DownloaderUiState,
    previewCache: MediaPreviewCache,
    onMediaDimensions: (Int, Int, Int) -> Unit,
    onDownloadOne: (ResolvedMedia, Int, Int, Int) -> Unit,
    onDismissError: () -> Unit,
    onCopyError: (String) -> Unit,
    onOpenBrowser: () -> Unit,
    useBrowserSessions: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        state.media?.let { media ->
            val density = LocalDensity.current
            val containerSize = LocalWindowInfo.current.containerSize
            val screenWidth = with(density) { containerSize.width.toDp() }
            val screenHeight = with(density) { containerSize.height.toDp() }
            val isMulti = media.size > 1
            val loopMultiplier = 1000
            val pageCount = if (isMulti) media.size * loopMultiplier else 1
            val initialPage = if (isMulti) (loopMultiplier / 2) * media.size else 0
            val pagerState = rememberPagerState(
                initialPage = initialPage,
                pageCount = { pageCount }
            )
            val loadedDimensions = remember(media) { mutableStateMapOf<Int, Pair<Int, Int>>() }

            val activeIndex = pagerState.currentPage % media.size
            val activeItem = media[activeIndex]
            val activeDimensions = loadedDimensions[activeIndex]
            val isAudio = activeItem.isAudioPreview(state.downloadContentType)

            val activeWidth = if (activeItem.isVideo) activeItem.width else activeDimensions?.first ?: activeItem.width
            val activeHeight = if (activeItem.isVideo) activeItem.height else activeDimensions?.second ?: activeItem.height
            val activeAspect = when {
                isAudio -> 1f
                activeWidth > 0 && activeHeight > 0 -> (activeWidth.toFloat() / activeHeight.toFloat()).coerceIn(0.4f, 2.5f)
                activeItem.isVideo -> 16f / 9f
                else -> 1f
            }

            val slotWidth = if (isAudio || !isMulti) {
                screenWidth - 32.dp
            } else {
                (screenWidth * 0.75f).coerceIn(230.dp, 310.dp)
            }
            val horizontalPadding = ((screenWidth - slotWidth) / 2).coerceAtLeast(16.dp)
            val maxContainerHeight = if (isAudio) {
                slotWidth.coerceAtMost(screenHeight * 0.48f)
            } else {
                (screenHeight * 0.36f).coerceIn(224.dp, 320.dp)
            }

            val (_, targetContainerHeight) = computeCardDimensions(activeAspect, maxContainerHeight, slotWidth)
            val animatedContainerHeight by animateDpAsState(
                targetValue = targetContainerHeight,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "containerHeight"
            )

            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fixed(slotWidth),
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                pageSpacing = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(animatedContainerHeight)
            ) { page ->
                val index = page % media.size
                val item = media[index]
                val dimensions = loadedDimensions[index]
                val isItemAudio = item.isAudioPreview(state.downloadContentType)

                val itemW = if (item.isVideo) item.width else dimensions?.first ?: item.width
                val itemH = if (item.isVideo) item.height else dimensions?.second ?: item.height
                val itemAspect = when {
                    isItemAudio -> 1f
                    itemW > 0 && itemH > 0 -> (itemW.toFloat() / itemH.toFloat()).coerceIn(0.4f, 2.5f)
                    item.isVideo -> 16f / 9f
                    else -> 1f
                }

                val (itemCardWidth, itemCardHeight) = computeCardDimensions(
                    itemAspect,
                    animatedContainerHeight,
                    slotWidth
                )

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    MediaPreviewCard(
                        item = item,
                        index = index,
                        useBrowserSessions = useBrowserSessions,
                        previewCache = previewCache,
                        contentType = state.downloadContentType,
                        cardWidth = itemCardWidth,
                        cardHeight = itemCardHeight,
                        onBitmapDimensions = { width, height ->
                            loadedDimensions[index] = width to height
                            onMediaDimensions(index, width, height)
                        }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            MediaMetadataContainer(
                content = MediaMetadataContent(
                    activeItem,
                    activeIndex,
                    media.size,
                    state.downloadContentType,
                    state.audioFormat,
                    state.filenamePattern,
                    state.audioFilenamePattern
                ),
                status = MediaMetadataStatus(
                    isSaving = state.savingItemIndex == activeIndex,
                    isSaved = state.savedItemIndex == activeIndex,
                    downloadsEnabled = !state.isSaving && state.savingItemIndex == null
                ),
                onDownload = {
                    val width = if (activeItem.isVideo) activeItem.width else activeDimensions?.first ?: activeItem.width
                    val height = if (activeItem.isVideo) activeItem.height else activeDimensions?.second ?: activeItem.height
                    onDownloadOne(activeItem, activeIndex, width, height)
                },
                fallback = MediaMetadataFallback(
                    title = media.firstOrNull()?.title,
                    author = media.firstOrNull()?.username,
                    bitmapWidth = activeDimensions?.first ?: 0,
                    bitmapHeight = activeDimensions?.second ?: 0
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }

        if (state.media == null && state.error == null) {
            EmptyState(
                variant = EmptyStateVariant.ReadyToDownload,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        state.error?.let { error ->
            val errorText = stringResource(error.messageResource())
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
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
                            IconButton({ onCopyError(errorText) }) {
                                Icon(Icons.Filled.ContentCopy, stringResource(R.string.copy_error), tint = colors.onErrorContainer)
                            }
                            TextButton(onDismissError) {
                                Text(stringResource(R.string.dismiss), color = colors.onErrorContainer)
                            }
                        }
                    }
                    SelectionContainer {
                        Text(errorText, style = MaterialTheme.typography.bodySmall, color = colors.onErrorContainer)
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
internal fun BottomControlsSection(
    state: DownloaderUiState,
    showLinkEditor: Boolean,
    canResolve: Boolean,
    onUrlChange: (String) -> Unit,
    onOpenConfig: () -> Unit,
    onResolve: () -> Unit,
    onDownloadAll: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val ytDlpMedia = state.media?.singleOrNull()?.takeIf { it.backend == MediaBackend.YT_DLP }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = contentPadding.calculateBottomPadding() + 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    trailingIcon = if (state.url.isNotEmpty()) {
                        {
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
                    } else null,
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
                            stringResource(if (state.isResolving) R.string.resolving_media else R.string.fetch),
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
                                Text(
                                    stringResource(state.lastDownloadedKind.downloadedMessageResource()),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
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

internal fun MediaKind?.downloadedMessageResource(): Int = when (this) {
    MediaKind.VIDEO -> R.string.video_downloaded
    MediaKind.IMAGE -> R.string.image_downloaded
    MediaKind.AUDIO -> R.string.audio_downloaded
    null -> R.string.media_downloaded
}

@Composable
internal fun buildYtDlpSummary(state: DownloaderUiState): String {
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
