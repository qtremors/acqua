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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.qtremors.acqua.R
import dev.qtremors.acqua.data.network.MediaDownloader
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.feature.about.AboutDestination
import dev.qtremors.acqua.feature.about.AboutScreen
import dev.qtremors.acqua.feature.about.LegalDocumentScreen
import dev.qtremors.acqua.feature.about.OpenSourceNoticesScreen
import dev.qtremors.acqua.feature.browser.BrowserScreen
import dev.qtremors.acqua.feature.browser.BrowserViewModel
import dev.qtremors.acqua.feature.downloader.DownloaderScreen
import dev.qtremors.acqua.feature.downloader.DownloaderViewModel
import dev.qtremors.acqua.feature.history.HistoryScreen
import dev.qtremors.acqua.feature.history.HistoryViewModel
import dev.qtremors.acqua.feature.settings.SettingsScreen
import dev.qtremors.acqua.feature.settings.SettingsViewModel
import dev.qtremors.acqua.platform.FileActions
import dev.qtremors.acqua.platform.HapticSignal
import dev.qtremors.acqua.platform.performHaptic
import dev.qtremors.acqua.ui.components.AcquaFabAction
import dev.qtremors.acqua.ui.components.AcquaFloatingToolbar
import dev.qtremors.acqua.ui.components.AcquaTabItem
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    downloaderViewModel: DownloaderViewModel,
    browserViewModel: BrowserViewModel,
    historyViewModel: HistoryViewModel,
    settingsViewModel: SettingsViewModel,
    mediaDownloader: MediaDownloader,
    fileActions: FileActions,
    initialUrl: String,
    urlHandoff: String?,
    browserRevision: Int,
    downloadRequestRevision: Int,
    backupManager: dev.qtremors.acqua.data.backup.PreferencesBackupManager? = null,
    appUpdater: dev.qtremors.acqua.data.updater.AppUpdater? = null,
    currentThemeState: dev.qtremors.acqua.ui.theme.ThemeState = dev.qtremors.acqua.ui.theme.ThemeState(),
    onThemeChange: (dev.qtremors.acqua.ui.theme.ThemeState) -> Unit = {},
    onUrlHandoffConsumed: () -> Unit,
    resolveInBrowser: suspend (String, Boolean) -> List<ResolvedMedia>,
    requestDownloadAccess: (needsNotification: Boolean, action: () -> Unit) -> Unit,
    openBrowser: (String?, Boolean, String?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
    var overlay by remember { mutableStateOf<AboutDestination?>(null) }
    var showAddWebsiteDialog by remember { mutableStateOf(false) }
    var showManageWebsiteDialog by remember { mutableStateOf(false) }

    val browserState by browserViewModel.state.collectAsState()
    val downloaderState by downloaderViewModel.state.collectAsState()
    val historyState by historyViewModel.state.collectAsState()

    BackHandler(enabled = overlay != null || pagerState.currentPage != 0) {
        if (overlay == AboutDestination.NOTICES || overlay == AboutDestination.LICENSE) {
            overlay = AboutDestination.ABOUT
        } else if (overlay == AboutDestination.ABOUT) {
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

    // Context-aware Floating Action Button for each tab
    val currentFabAction = remember(pagerState.currentPage, downloaderState, browserState) {
        when (pagerState.currentPage) {
            0 -> {
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
            1 -> {
                AcquaFabAction(
                    icon = Icons.Filled.Add,
                    contentDescriptionRes = R.string.save_website,
                    onClick = { showAddWebsiteDialog = true }
                )
            }
            2 -> {
                AcquaFabAction(
                    icon = Icons.Filled.FolderOpen,
                    contentDescriptionRes = R.string.open_downloads_folder,
                    onClick = { fileActions.openDownloads() }
                )
            }
            3 -> {
                AcquaFabAction(
                    icon = Icons.Filled.Sync,
                    contentDescriptionRes = R.string.check_for_updates,
                    onClick = {
                        settingsViewModel.updateYtDlp(force = true)
                        Toast.makeText(context, R.string.checking_for_updates, Toast.LENGTH_SHORT).show()
                    }
                )
            }
            else -> null
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Content padding taking into account the floating toolbar at bottom
    val screenPadding = remember(statusBarTop, imeBottom, navBottom) {
        val baseBottom = if (imeBottom > 0.dp) 0.dp else navBottom + 80.dp
        PaddingValues(
            top = statusBarTop,
            bottom = baseBottom,
            start = 0.dp,
            end = 0.dp
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
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isBrowsingWebsite && overlay == null
            ) { page ->
                when (page) {
                    0 -> DownloaderScreen(
                        viewModel = downloaderViewModel,
                        mediaDownloader = mediaDownloader,
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
                        contentPadding = screenPadding,
                        modifier = Modifier.fillMaxSize(),
                        onOpenAbout = { overlay = AboutDestination.ABOUT }
                    )
                    1 -> BrowserScreen(
                        viewModel = browserViewModel,
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
                    2 -> HistoryScreen(
                        historyViewModel,
                        fileActions,
                        active = pagerState.currentPage == 2,
                        onRefetch = {
                            downloaderViewModel.updateUrl(it)
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(0)
                            }
                        },
                        contentPadding = screenPadding,
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> SettingsScreen(
                        settingsViewModel,
                        backupManager = backupManager,
                        themeState = currentThemeState,
                        onThemeChange = onThemeChange,
                        onOpenAbout = { overlay = AboutDestination.ABOUT },
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
                                if (targetState == AboutDestination.NOTICES || targetState == AboutDestination.LICENSE) {
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
                                AboutDestination.ABOUT -> AboutScreen(
                                    appUpdater = appUpdater,
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
                                    AboutDestination.ABOUT -> R.string.about_title
                                    AboutDestination.NOTICES -> R.string.about_open_source_notices
                                    AboutDestination.LICENSE -> R.string.about_license
                                    else -> R.string.about_title
                                }
                            ),
                            onBackClick = {
                                overlay = if (
                                    overlay == AboutDestination.NOTICES ||
                                    overlay == AboutDestination.LICENSE
                                ) {
                                    AboutDestination.ABOUT
                                } else {
                                    null
                                }
                            }
                        )
                    }
                }
            }

            // Modern Expressive Floating Toolbar Navigation Shell for Main Tabs
            if (overlay == null && !isBrowsingWebsite) {
                val navItems = remember {
                    listOf(
                        AcquaTabItem(
                            icon = Icons.Filled.Download,
                            labelRes = R.string.downloader,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            }
                        ),
                        AcquaTabItem(
                            icon = Icons.Filled.Public,
                            labelRes = R.string.browser,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            }
                        ),
                        AcquaTabItem(
                            icon = Icons.Filled.History,
                            labelRes = R.string.history,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(2)
                                }
                            }
                        ),
                        AcquaTabItem(
                            icon = Icons.Filled.Settings,
                            labelRes = R.string.settings,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(3)
                                }
                            }
                        )
                    )
                }

                AcquaFloatingToolbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(1f),
                    items = navItems,
                    selectedIndex = pagerState.currentPage,
                    fabAction = currentFabAction
                )
            }
        }
    }
}
