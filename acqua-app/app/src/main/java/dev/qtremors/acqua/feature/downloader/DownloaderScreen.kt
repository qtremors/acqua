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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
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
    contentPadding: PaddingValues = PaddingValues(),
    onMediaSaved: () -> Unit = {},
    showLinkEditor: Boolean = true,
    onOpenAbout: () -> Unit = {},
    showHeaderCard: Boolean = true,
    onStatusClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val currentOnMediaSaved by rememberUpdatedState(onMediaSaved)
    val colors = MaterialTheme.colorScheme
    val previewCache = remember(mediaDownloader) { MediaPreviewCache(context, mediaDownloader) }
    var showConfigSheet by remember { mutableStateOf(false) }

    val pullRefreshState = rememberPullToRefreshState()
    var isRefreshing by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            context.performHaptic(HapticSignal.CLICK)
            onOpenAbout()
            isRefreshing = false
        }
    }

    var lastHapticBucket by remember { mutableIntStateOf(0) }
    LaunchedEffect(pullRefreshState.distanceFraction) {
        val fraction = pullRefreshState.distanceFraction
        val currentBucket = (fraction * 10).toInt()

        if (fraction >= 1f && lastHapticBucket < 10) {
            context.performHaptic(HapticSignal.CLICK)
            lastHapticBucket = 10
        } else if (fraction < 1f && currentBucket != lastHapticBucket) {
            if (currentBucket > lastHapticBucket) {
                context.performHaptic(HapticSignal.CLICK)
            }
            lastHapticBucket = currentBucket
        }

        if (fraction == 0f) {
            lastHapticBucket = 0
        }
    }

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

    val displayFraction = if (isRefreshing) 1f else pullRefreshState.distanceFraction
    val thresholdPassed = displayFraction >= 1f
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val cardExpansion by animateDpAsState(
        targetValue = 120.dp * displayFraction.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardExpansion"
    )

    val containerColor by animateColorAsState(
        targetValue = if (thresholdPassed) colors.primary else colors.surfaceContainerLow,
        animationSpec = tween(durationMillis = 300),
        label = "containerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (thresholdPassed) colors.onPrimary else colors.primary,
        animationSpec = tween(durationMillis = 300),
        label = "contentColor"
    )

    val textScale by animateFloatAsState(
        targetValue = if (thresholdPassed) 1.25f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "textScale"
    )

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { isRefreshing = true },
        state = pullRefreshState,
        indicator = { },
        modifier = modifier.fillMaxSize(),
        enabled = showLinkEditor && showHeaderCard
    ) {
        Column(
            modifier = Modifier.fillMaxSize().imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showLinkEditor && showHeaderCard) {
                Card(
                    onClick = {
                        context.performHaptic(HapticSignal.CLICK)
                        onOpenAbout()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset(y = (-4).dp)
                        .height(56.dp + statusBarPadding + cardExpansion + 4.dp),
                    shape = RoundedCornerShape(
                        topStart = 0.dp,
                        topEnd = 0.dp,
                        bottomStart = 28.dp,
                        bottomEnd = 28.dp
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = containerColor,
                        contentColor = if (thresholdPassed) colors.onPrimary else colors.onSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Spacer(modifier = Modifier.height(statusBarPadding + 4.dp))
                            val targetHeaderTitle = if (displayFraction > 0.2f || thresholdPassed) {
                                stringResource(R.string.settings_title)
                            } else {
                                stringResource(R.string.app_name)
                            }
                            AnimatedContent(
                                targetState = targetHeaderTitle,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(150))
                                },
                                label = "HeaderTitleTransition"
                            ) { headerTitle ->
                                Text(
                                    text = headerTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (thresholdPassed) FontWeight.Bold else FontWeight.SemiBold,
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = textScale
                                        scaleY = textScale
                                    },
                                    color = if (thresholdPassed) colors.onPrimary else colors.onSurface
                                )
                            }
                        }
                    }
                }
            }

            val contentScrollState = rememberScrollState()
            LaunchedEffect(state.media) {
                if (state.media != null) {
                    contentScrollState.scrollTo(0)
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = if (showLinkEditor && showHeaderCard) 4.dp else contentPadding.calculateTopPadding())
            ) {
                val boxMaxHeight = maxHeight
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = boxMaxHeight)
                        .verticalScroll(contentScrollState),
                    contentAlignment = if (state.media != null) Alignment.TopCenter else Alignment.Center
                ) {
                    when {
                        state.validity == LinkValidity.EMPTY -> EmptyState(
                            variant = EmptyStateVariant.ReadyToDownload,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(boxMaxHeight)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        state.validity == LinkValidity.INVALID -> EmptyState(
                            variant = EmptyStateVariant.InvalidLink,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(boxMaxHeight)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
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
            }

            if (state.activeDownloadCount > 0) {
                CompactDownloadStatus(
                    state = state,
                    onCancel = viewModel::cancelActiveDownload,
                    onClick = onStatusClick,
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
                },
                contentPadding = contentPadding
            )
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
        modifier = modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        state.media?.let { media ->
            val configuration = LocalConfiguration.current
            val screenWidth = configuration.screenWidthDp.dp
            val screenHeight = configuration.screenHeightDp.dp
            val isMulti = media.size > 1
            val loopMultiplier = 1000
            val pageCount = if (isMulti) media.size * loopMultiplier else 1
            val initialPage = if (isMulti) (loopMultiplier / 2) * media.size else 0
            val pagerState = rememberPagerState(
                initialPage = initialPage,
                pageCount = { pageCount }
            )
            val loadedDimensions = remember { mutableStateMapOf<Int, Pair<Int, Int>>() }

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
                        onBitmapDimensions = { w, h -> loadedDimensions[index] = Pair(w, h) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            MediaMetadataContainer(
                item = activeItem,
                index = activeIndex,
                totalCount = media.size,
                contentType = state.downloadContentType,
                audioFormat = state.audioFormat,
                filenamePattern = state.filenamePattern,
                audioFilenamePattern = state.audioFilenamePattern,
                isSaving = state.savingItemIndex == activeIndex,
                isSaved = state.savedItemIndex == activeIndex,
                downloadsEnabled = !state.isSaving && state.savingItemIndex == null,
                onDownload = {
                    val width = if (activeItem.isVideo) activeItem.width else activeDimensions?.first ?: activeItem.width
                    val height = if (activeItem.isVideo) activeItem.height else activeDimensions?.second ?: activeItem.height
                    onDownloadOne(activeItem, activeIndex, width, height)
                },
                fallbackTitle = media.firstOrNull()?.title,
                fallbackAuthor = media.firstOrNull()?.username,
                bitmapWidth = activeDimensions?.first ?: 0,
                bitmapHeight = activeDimensions?.second ?: 0,
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
    onClick: (() -> Unit)? = null,
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
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth(),
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
                    modifier = Modifier.size(38.dp),
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
