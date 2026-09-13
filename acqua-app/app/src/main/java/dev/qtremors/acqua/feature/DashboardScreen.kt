package dev.qtremors.acqua.feature

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.qtremors.acqua.R
import dev.qtremors.acqua.data.network.HttpMediaClient
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.appinfo.AboutDestination
import dev.qtremors.acqua.feature.about.AboutScreen
import dev.qtremors.acqua.feature.about.LegalDocumentScreen
import dev.qtremors.acqua.feature.about.OpenSourceNoticesScreen
import dev.qtremors.acqua.feature.settings.SettingsScreen
import dev.qtremors.acqua.feature.browser.BrowserScreen
import dev.qtremors.acqua.feature.browser.BrowserViewModel
import dev.qtremors.acqua.feature.browser.BrowserDownloadRequest
import dev.qtremors.acqua.feature.downloader.DownloadHubScreen
import dev.qtremors.acqua.feature.downloader.DownloaderScreen
import dev.qtremors.acqua.feature.downloader.DownloaderViewModel
import dev.qtremors.acqua.feature.downloader.history.HistoryScreen
import dev.qtremors.acqua.feature.downloader.history.HistoryViewModel
import dev.qtremors.acqua.feature.settings.SettingsViewModel
import dev.qtremors.acqua.feature.updater.AppUpdatesScreen
import dev.qtremors.acqua.feature.updater.AppUpdatesViewModel
import dev.qtremors.acqua.platform.FileActions
import dev.qtremors.acqua.platform.HapticSignal
import dev.qtremors.acqua.platform.performHaptic
import dev.qtremors.acqua.ui.components.AcquaFabAction
import dev.qtremors.acqua.ui.components.AcquaFloatingToolbar
import dev.qtremors.acqua.ui.components.AcquaNavigationRail
import dev.qtremors.acqua.ui.components.AcquaTabItem
import kotlinx.coroutines.launch
import androidx.window.core.layout.WindowSizeClass

data class DashboardStateHolders(
    val downloader: DownloaderViewModel,
    val browser: BrowserViewModel,
    val history: HistoryViewModel,
    val settings: SettingsViewModel,
    val updates: AppUpdatesViewModel
)

data class DashboardServices(
    val mediaDownloader: HttpMediaClient,
    val fileActions: FileActions,
    val backupManager: dev.qtremors.acqua.data.backup.PreferencesBackupManager?
)

data class DashboardRouteState(
    val initialUrl: String,
    val urlHandoff: String?,
    val browserRevision: Int,
    val downloadRequestRevision: Int,
    val theme: dev.qtremors.acqua.ui.theme.ThemeState
)

data class DashboardActions(
    val onThemeChange: (dev.qtremors.acqua.ui.theme.ThemeState) -> Unit,
    val onUrlHandoffConsumed: () -> Unit,
    val resolveInBrowser: suspend (String, Boolean) -> List<ResolvedMedia>,
    val requestDownloadAccess: (needsNotification: Boolean, action: () -> Unit) -> Unit,
    val openBrowser: (String?, Boolean, String?) -> Unit,
    val onBrowserDownloadRequest: (BrowserDownloadRequest) -> Unit
)

@Composable
fun DashboardScreen(
    stateHolders: DashboardStateHolders,
    services: DashboardServices,
    routeState: DashboardRouteState,
    actions: DashboardActions
) {
    val (downloaderViewModel, browserViewModel, historyViewModel, settingsViewModel, appUpdatesViewModel) = stateHolders
    val (mediaDownloader, fileActions, backupManager) = services
    val (initialUrl, urlHandoff, browserRevision, downloadRequestRevision, currentThemeState) = routeState
    val (onThemeChange, onUrlHandoffConsumed, resolveInBrowser, requestDownloadAccess, openBrowser, onBrowserDownloadRequest) = actions
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var savedTab by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(initialPage = savedTab, pageCount = { 3 })
    var overlay by rememberSaveable { mutableStateOf<AboutDestination?>(null) }
    var showAddWebsiteDialog by rememberSaveable { mutableStateOf(false) }
    var showManageWebsiteDialog by rememberSaveable { mutableStateOf(false) }

    val browserState by browserViewModel.state.collectAsState()
    val downloaderState by downloaderViewModel.state.collectAsState()
    val historyState by historyViewModel.state.collectAsState()
    val appUpdatesState by appUpdatesViewModel.state.collectAsState()
    val trackedRepos by appUpdatesViewModel.trackedRepos.collectAsState()
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val useNavigationRail = adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
    )

    BackHandler(enabled = overlay != null || pagerState.currentPage != 0) {
        if (overlay == AboutDestination.NOTICES || overlay == AboutDestination.LICENSE) {
            overlay = AboutDestination.SETTINGS
        } else if (overlay == AboutDestination.ABOUT) {
            overlay = AboutDestination.SETTINGS
        } else if (overlay == AboutDestination.SETTINGS) {
            overlay = null
        } else if (pagerState.currentPage != 0) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(0)
            }
        }
    }

    LaunchedEffect(initialUrl) { downloaderViewModel.setInitialUrl(initialUrl) }
    LaunchedEffect(browserRevision) {
        if (browserRevision > 0) browserViewModel.refresh()
    }
    LaunchedEffect(urlHandoff) {
        urlHandoff?.let { url ->
            downloaderViewModel.updateUrl(url)
            coroutineScope.launch {
                pagerState.animateScrollToPage(0)
            }
            onUrlHandoffConsumed()
        }
    }

    val isBrowsingWebsite = pagerState.currentPage == 1 && browserState.currentUrl != null && overlay == null

    var downloadHubSubTab by rememberSaveable { mutableIntStateOf(0) }
    var showHistorySearch by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        savedTab = pagerState.currentPage
    }

    LaunchedEffect(pagerState.currentPage, downloadHubSubTab) {
        if (pagerState.currentPage != 0 || downloadHubSubTab != 1) {
            showHistorySearch = false
        }
    }

    // Context-aware Floating Action Button for each tab
    val currentFabAction = remember(
        pagerState.currentPage,
        downloadHubSubTab,
        downloaderState,
        browserState,
        appUpdatesState,
        trackedRepos
    ) {
        when (pagerState.currentPage) {
            0 -> {
                if (downloadHubSubTab == 1) {
                    AcquaFabAction(
                        icon = Icons.Filled.Search,
                        contentDescriptionRes = R.string.hist_search,
                        onClick = {
                            showHistorySearch = true
                        }
                    )
                } else {
                    val hasMedia = !downloaderState.media.isNullOrEmpty()
                    val canDownload = hasMedia && !downloaderState.isSaving && !downloaderState.saved && downloaderState.savingItemIndex == null
                    if (canDownload) {
                        AcquaFabAction(
                            icon = Icons.Filled.Download,
                            contentDescriptionRes = R.string.download,
                            onClick = {
                                requestDownloadAccess(true) {
                                    downloaderViewModel.downloadAll(browserState.useSessions, resolveInBrowser)
                                }
                            }
                        )
                    } else {
                        AcquaFabAction(
                            icon = Icons.Filled.ContentPaste,
                            contentDescriptionRes = R.string.paste_link,
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                if (!clipText.isNullOrBlank()) {
                                    downloaderViewModel.updateUrl(clipText)
                                    context.performHaptic(HapticSignal.CLICK)
                                    if (browserState.initialized) {
                                        downloaderViewModel.resolve(browserState.useSessions, downloadRequestRevision, resolveInBrowser)
                                    }
                                } else {
                                    Toast.makeText(context, R.string.no_link_in_clipboard, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
            1 -> {
                AcquaFabAction(
                    icon = Icons.Filled.Add,
                    contentDescriptionRes = R.string.save_website,
                    onClick = { showAddWebsiteDialog = true }
                )
            }
            2 -> {
                val pendingUpdate = trackedRepos.firstOrNull { it.isUpdateAvailable }
                if (appUpdatesState.hasRefreshed && pendingUpdate != null) {
                    AcquaFabAction(
                        icon = Icons.Filled.SystemUpdate,
                        contentDescriptionRes = R.string.update,
                        onClick = { appUpdatesViewModel.download(pendingUpdate) }
                    )
                } else {
                    AcquaFabAction(
                        icon = Icons.Filled.Refresh,
                        contentDescriptionRes = R.string.check_for_updates,
                        onClick = appUpdatesViewModel::refresh
                    )
                }
            }
            else -> null
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Content padding taking into account the floating toolbar at bottom
    val screenPadding = remember(statusBarTop, imeBottom, navBottom, useNavigationRail) {
        val baseBottom = if (imeBottom > 0.dp) 0.dp else navBottom + if (useNavigationRail) 16.dp else 80.dp
        PaddingValues(
            top = statusBarTop,
            bottom = baseBottom,
            start = 0.dp,
            end = 0.dp
        )
    }

    val navItems = remember(trackedRepos) {
        listOf(
            AcquaTabItem(Icons.Filled.Download, R.string.downloader) {
                coroutineScope.launch { pagerState.animateScrollToPage(0) }
            },
            AcquaTabItem(Icons.Filled.Public, R.string.browser) {
                coroutineScope.launch { pagerState.animateScrollToPage(1) }
            },
            AcquaTabItem(
                icon = Icons.Filled.SystemUpdate,
                labelRes = R.string.github_tracker,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                hasBadge = trackedRepos.any { it.isUpdateAvailable }
            )
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {},
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Tabbed Content Navigation using HorizontalPager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().padding(
                    start = if (useNavigationRail && overlay == null && !isBrowsingWebsite) 80.dp else 0.dp
                ),
                userScrollEnabled = !isBrowsingWebsite && overlay == null
            ) { page ->
                when (page) {
                    0 -> DownloadHubScreen(
                        downloaderViewModel = downloaderViewModel,
                        historyViewModel = historyViewModel,
                        mediaDownloader = mediaDownloader,
                        fileActions = fileActions,
                        useBrowserSessions = browserState.useSessions,
                        sessionsInitialized = browserState.initialized,
                        browserRequestRevision = downloadRequestRevision,
                        resolveInBrowser = resolveInBrowser,
                        requestDownloadAccess = requestDownloadAccess,
                        onOpenBrowser = { url ->
                            browserViewModel.loadUrl(url)
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(1)
                            }
                        },
                        onOpenAbout = { overlay = AboutDestination.SETTINGS },
                        contentPadding = screenPadding,
                        onSubTabChange = { downloadHubSubTab = it },
                        showHistorySearch = showHistorySearch,
                        onOpenHistorySearch = { showHistorySearch = true },
                        onDismissHistorySearch = { showHistorySearch = false },
                        modifier = Modifier.fillMaxSize()
                    )
                    1 -> BrowserScreen(
                        viewModel = browserViewModel,
                        onDownloadRequest = onBrowserDownloadRequest,
                        active = pagerState.currentPage == 1 && overlay == null,
                        onExitToDownloader = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(0)
                            }
                        },
                        contentPadding = screenPadding,
                        showAddDialogExternal = showAddWebsiteDialog,
                        onAddDialogDismissed = { showAddWebsiteDialog = false },
                        showManageDialogExternal = showManageWebsiteDialog,
                        onManageDialogDismissed = { showManageWebsiteDialog = false },
                        modifier = Modifier.fillMaxSize().padding(
                            top = if (isBrowsingWebsite) 0.dp else statusBarTop
                        )
                    )
                    else -> AppUpdatesScreen(
                        viewModel = appUpdatesViewModel,
                        contentPadding = screenPadding,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // About Overlay with Slide-Down Transition from Top
            AnimatedVisibility(
                visible = overlay != null,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = tween(300)
                ) + fadeOut()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = overlay,
                            transitionSpec = {
                                val order = listOf(
                                    AboutDestination.SETTINGS,
                                    AboutDestination.NOTICES,
                                    AboutDestination.LICENSE,
                                    AboutDestination.ABOUT
                                )
                                val targetIndex = order.indexOf(targetState)
                                val initialIndex = order.indexOf(initialState)
                                if (targetIndex > initialIndex) {
                                    (slideInHorizontally { it } + fadeIn()).togetherWith(
                                        slideOutHorizontally { -it } + fadeOut()
                                    )
                                } else {
                                    (slideInHorizontally { -it } + fadeIn()).togetherWith(
                                        slideOutHorizontally { it } + fadeOut()
                                    )
                                }
                            },
                            label = "about_overlay_subdestination"
                        ) { destination ->
                            when (destination) {
                                AboutDestination.SETTINGS -> SettingsScreen(
                                    viewModel = settingsViewModel,
                                    backupManager = backupManager,
                                    themeState = currentThemeState,
                                    onThemeChange = onThemeChange,
                                    onOpenUpdates = {
                                        overlay = null
                                        coroutineScope.launch { pagerState.animateScrollToPage(2) }
                                    },
                                    onOpenNotices = { overlay = AboutDestination.NOTICES },
                                    onOpenLicense = { overlay = AboutDestination.LICENSE },
                                    onOpenAbout = { overlay = AboutDestination.ABOUT },
                                    contentPadding = screenPadding,
                                    modifier = Modifier.fillMaxSize()
                                )
                                AboutDestination.ABOUT -> AboutScreen(
                                    onOpenNotices = { overlay = AboutDestination.NOTICES },
                                    onOpenLicense = { overlay = AboutDestination.LICENSE },
                                    contentPadding = screenPadding,
                                    modifier = Modifier.fillMaxSize()
                                )
                                AboutDestination.NOTICES -> OpenSourceNoticesScreen(
                                    contentPadding = screenPadding,
                                    modifier = Modifier.fillMaxSize()
                                )
                                AboutDestination.LICENSE -> LegalDocumentScreen(
                                    title = stringResource(R.string.about_license),
                                    assetName = "LICENSE.md",
                                    introduction = stringResource(R.string.acqua_legal_notice),
                                    contentPadding = screenPadding,
                                    modifier = Modifier.fillMaxSize()
                                )
                                null -> Unit
                            }
                        }

                        AcquaFloatingToolbar(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .zIndex(1f),
                            title = stringResource(
                                when (overlay) {
                                    AboutDestination.SETTINGS -> R.string.settings_title
                                    AboutDestination.ABOUT -> R.string.about_title
                                    AboutDestination.NOTICES -> R.string.about_open_source_notices
                                    AboutDestination.LICENSE -> R.string.about_license
                                    else -> R.string.settings_title
                                }
                            ),
                            onBackClick = {
                                overlay = when (overlay) {
                                    AboutDestination.NOTICES,
                                    AboutDestination.LICENSE -> AboutDestination.SETTINGS
                                    AboutDestination.ABOUT -> AboutDestination.SETTINGS
                                    else -> null
                                }
                            }
                        )
                    }
                }
            }

            // Modern Expressive Floating Toolbar Navigation Shell for Main Tabs
            if (overlay == null && !isBrowsingWebsite && !useNavigationRail) {
                AcquaFloatingToolbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(1f),
                    items = navItems,
                    selectedIndex = pagerState.currentPage,
                    fabAction = currentFabAction
                )
            } else if (overlay == null && !isBrowsingWebsite) {
                AcquaNavigationRail(
                    items = navItems,
                    selectedIndex = pagerState.currentPage,
                    fabAction = currentFabAction,
                    modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().zIndex(1f)
                )
            }
        }
    }
}
