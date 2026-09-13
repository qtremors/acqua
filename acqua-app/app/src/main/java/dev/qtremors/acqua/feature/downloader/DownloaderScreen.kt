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
import dev.qtremors.acqua.R
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
fun DownloaderScreen(
    viewModel: DownloaderViewModel,
    mediaDownloader: HttpMediaClient,
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
    val resources = LocalResources.current
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
                is DownloaderEvent.DownloadComplete -> {
                    context.performHaptic(HapticSignal.COMPLETE)
                    Toast.makeText(
                        context,
                        event.mediaKind.downloadedMessageResource(),
                        Toast.LENGTH_SHORT
                    ).show()
                    currentOnMediaSaved()
                }
                is DownloaderEvent.ItemSaved -> {
                    context.performHaptic(HapticSignal.COMPLETE)
                    Toast.makeText(
                        context,
                        event.mediaKind.downloadedMessageResource(),
                        Toast.LENGTH_SHORT
                    ).show()
                    currentOnMediaSaved()
                }
                is DownloaderEvent.ItemSaveFailed -> Toast.makeText(
                    context,
                    resources.getString(event.failure.messageResource()),
                    Toast.LENGTH_LONG
                ).show()
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
            modifier = Modifier
                .fillMaxHeight()
                .align(Alignment.TopCenter)
                .widthIn(max = 1000.dp)
                .fillMaxWidth()
                .imePadding(),
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
                            onMediaDimensions = viewModel::updateMediaDimensions,
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

internal fun DownloaderProblem.messageResource(): Int = when (this) {
    is DownloaderProblem.Resolution -> when (failure) {
        MediaResolutionFailure.INVALID_LINK -> R.string.invalid_link
        MediaResolutionFailure.SESSION_REQUIRED,
        MediaResolutionFailure.SESSION_EXPIRED -> R.string.browser_session_needed
        MediaResolutionFailure.NETWORK -> R.string.network_error
        MediaResolutionFailure.ENGINE_REQUIRED,
        MediaResolutionFailure.RUNTIME_UNAVAILABLE -> R.string.download_engine_error
        MediaResolutionFailure.UNSUPPORTED_MEDIA -> R.string.could_not_resolve_media
        MediaResolutionFailure.UNKNOWN -> R.string.could_not_resolve_media
    }
    is DownloaderProblem.Download -> failure.messageResource()
}

internal fun DownloadFailure.messageResource(): Int = when (this) {
    DownloadFailure.INVALID_REQUEST -> R.string.could_not_resolve_media
    DownloadFailure.RUNTIME -> R.string.ytdlp_error_runtime
    DownloadFailure.NETWORK -> R.string.ytdlp_error_network
    DownloadFailure.STORAGE -> R.string.ytdlp_error_storage
    DownloadFailure.UPDATE_SERVICE -> R.string.ytdlp_error_update_service
    DownloadFailure.PLATFORM_LIMIT -> R.string.download_error_platform_limit
    DownloadFailure.UNKNOWN -> R.string.ytdlp_error_unknown
}
