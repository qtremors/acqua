package dev.qtremors.acqua.feature.downloader

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.R
import dev.qtremors.acqua.data.network.HttpMediaClient
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.feature.downloader.history.HistoryScreen
import dev.qtremors.acqua.feature.downloader.history.HistoryViewModel
import dev.qtremors.acqua.platform.FileActions
import dev.qtremors.acqua.platform.HapticSignal
import dev.qtremors.acqua.platform.performHaptic
import dev.qtremors.acqua.ui.components.ExpressiveSegmentedButtonRow
import dev.qtremors.acqua.ui.theme.LocalReducedMotionEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadHubScreen(
    downloaderViewModel: DownloaderViewModel,
    historyViewModel: HistoryViewModel,
    mediaDownloader: HttpMediaClient,
    fileActions: FileActions,
    useBrowserSessions: Boolean,
    sessionsInitialized: Boolean,
    browserRequestRevision: Int,
    resolveInBrowser: suspend (String, Boolean) -> List<ResolvedMedia>,
    requestDownloadAccess: (needsNotification: Boolean, action: () -> Unit) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onSubTabChange: (Int) -> Unit = {},
    showHistorySearch: Boolean = false,
    onOpenHistorySearch: () -> Unit = {},
    onDismissHistorySearch: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val subPagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val downloaderState by downloaderViewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(subPagerState.currentPage) {
        onSubTabChange(subPagerState.currentPage)
    }

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

    val textScale by animateFloatAsState(
        targetValue = if (thresholdPassed) 1.25f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "textScale"
    )

    BackHandler(enabled = subPagerState.currentPage != 0 && !showHistorySearch) {
        coroutineScope.launch {
            subPagerState.animateScrollToPage(0)
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { isRefreshing = true },
        state = pullRefreshState,
        indicator = { },
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header card (Acqua brand & pull down to open About)
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

            // Sub-navigation tabs: Downloader vs Progress & History
            ExpressiveDownloadTabSwitcher(
                selectedIndex = subPagerState.currentPage,
                activeDownloadCount = downloaderState.activeDownloadCount,
                onTabSelected = { page ->
                    coroutineScope.launch { subPagerState.animateScrollToPage(page) }
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // Pager for the two sub-views
            HorizontalPager(
                state = subPagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                userScrollEnabled = true
            ) { page ->
                when (page) {
                    0 -> DownloaderScreen(
                        viewModel = downloaderViewModel,
                        mediaDownloader = mediaDownloader,
                        useBrowserSessions = useBrowserSessions,
                        sessionsInitialized = sessionsInitialized,
                        browserRequestRevision = browserRequestRevision,
                        resolveInBrowser = resolveInBrowser,
                        requestDownloadAccess = requestDownloadAccess,
                        onOpenBrowser = onOpenBrowser,
                        contentPadding = contentPadding,
                        showHeaderCard = false,
                        onOpenAbout = onOpenAbout,
                        onStatusClick = {
                            coroutineScope.launch { subPagerState.animateScrollToPage(1) }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    1 -> HistoryScreen(
                        viewModel = historyViewModel,
                        fileActions = fileActions,
                        active = subPagerState.currentPage == 1,
                        onRefetch = { url ->
                            downloaderViewModel.updateUrl(url)
                            coroutineScope.launch { subPagerState.animateScrollToPage(0) }
                        },
                        contentPadding = contentPadding,
                        activeQueue = downloaderState.downloadQueue,
                        onCancelActiveDownload = downloaderViewModel::cancelDownload,
                        showSearchInput = showHistorySearch,
                        onOpenSearch = onOpenHistorySearch,
                        onDismissSearch = onDismissHistorySearch,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
private data class DownloadTabItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val badgeCount: Int = 0
)

@Composable
private fun ExpressiveDownloadTabSwitcher(
    selectedIndex: Int,
    activeDownloadCount: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        DownloadTabItem(
            title = stringResource(R.string.tab_downloader),
            selectedIcon = Icons.Filled.Download,
            unselectedIcon = Icons.Outlined.Download,
            badgeCount = 0
        ),
        DownloadTabItem(
            title = stringResource(R.string.tab_progress_history),
            selectedIcon = Icons.Filled.History,
            unselectedIcon = Icons.Outlined.History,
            badgeCount = activeDownloadCount
        )
    )

    ExpressiveSegmentedButtonRow(
        items = tabs,
        selectedIndex = selectedIndex,
        onSelect = onTabSelected,
        label = { it.title },
        leadingIcon = { tab, isSelected ->
            Icon(
                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
        },
        trailingBadge = { tab, isSelected ->
            if (tab.badgeCount > 0) {
                Badge(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                ) {
                    Text(
                        text = "${tab.badgeCount}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        modifier = modifier
    )
}
