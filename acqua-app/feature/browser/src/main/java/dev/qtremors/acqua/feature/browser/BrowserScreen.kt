package dev.qtremors.acqua.feature.browser

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.net.toUri
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DesktopMac
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.resolver.web.LivePageExtractionFailure
import dev.qtremors.acqua.resolver.web.LivePageExtractionOutcome
import dev.qtremors.acqua.resolver.web.LivePageMediaCollector
import dev.qtremors.acqua.data.session.SavedInstagramSession
import dev.qtremors.acqua.data.session.SavedWebsite
import dev.qtremors.acqua.domain.BrowserDestination
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.platform.HapticSignal
import dev.qtremors.acqua.platform.image.BoundedBitmapDecoder
import dev.qtremors.acqua.platform.performHaptic
import dev.qtremors.acqua.resolver.web.WebExtractionEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onExitToDownloader: () -> Unit,
    onDownloadRequest: (BrowserDownloadRequest) -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(),
    initialUrlToLoad: String? = null,
    showAddDialogExternal: Boolean = false,
    onAddDialogDismissed: () -> Unit = {},
    showManageDialogExternal: Boolean = false,
    onManageDialogDismissed: () -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showManageDialog by rememberSaveable { mutableStateOf(false) }
    var showBookmarksSheet by rememberSaveable { mutableStateOf(false) }
    var showMenuSheet by rememberSaveable { mutableStateOf(false) }
    var editingWebsite by remember { mutableStateOf<SavedWebsite?>(null) }
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    var isEditingAddress by rememberSaveable { mutableStateOf(false) }
    var searchInput by rememberSaveable { mutableStateOf("") }
    var extractionPending by remember { mutableStateOf(false) }
    var extractionJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val currentActive by rememberUpdatedState(active)
    val returnToStartPage: () -> Unit = {
        activeWebView?.clearFocus()
        viewModel.goHome()
    }

    val liveMediaCollector = remember { LivePageMediaCollector() }

    val currentHost = state.currentUrl?.let(WebLink::host)?.removePrefix("www.")
    val isBookmarked = remember(state.currentUrl, state.websites) {
        !currentHost.isNullOrBlank() && state.websites.any {
            it.host.equals(currentHost, ignoreCase = true) ||
                it.origin.equals(WebLink.origin(state.currentUrl.orEmpty()), ignoreCase = true)
        }
    }

    val toggleBookmark: () -> Unit = {
        context.performHaptic(HapticSignal.CLICK)
        state.currentUrl?.let { url ->
            if (isBookmarked) {
                viewModel.removeWebsite(url)
                Toast.makeText(context, R.string.bookmark_removed, Toast.LENGTH_SHORT).show()
            } else {
                val title = state.pageTitle?.trim()?.takeIf { it.isNotBlank() }
                val host = WebLink.host(url)?.removePrefix("www.") ?: url
                val name = title ?: host
                viewModel.addWebsite(name, url, activeWebView?.favicon)
                Toast.makeText(context, R.string.bookmark_added, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val triggerMediaDownload: () -> Unit = {
        val wv = activeWebView
        if (wv != null && !extractionPending) {
            extractionPending = true
            context.performHaptic(HapticSignal.CLICK)
            Toast.makeText(context, R.string.resolving_media, Toast.LENGTH_SHORT).show()
            val sourceUrl = state.currentUrl.orEmpty()
            extractionJob = scope.launch {
                try {
                    extractMediaFromPage(
                        context,
                        wv,
                        sourceUrl,
                        liveMediaCollector,
                        onDownloadRequest
                    ) {
                        currentActive &&
                            WebLink.mediaPageIdentity(viewModel.state.value.currentUrl.orEmpty()) ==
                            WebLink.mediaPageIdentity(sourceUrl)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.no_media_found, Toast.LENGTH_LONG).show()
                } finally {
                    extractionPending = false
                }
            }
        }
    }

    DisposableEffect(activeWebView, WebLink.mediaPageIdentity(state.currentUrl.orEmpty()), active) {
        onDispose { extractionJob?.cancel() }
    }

    LaunchedEffect(showAddDialogExternal) {
        if (showAddDialogExternal) {
            showAddDialog = true
            onAddDialogDismissed()
        }
    }

    LaunchedEffect(showManageDialogExternal) {
        if (showManageDialogExternal) {
            showManageDialog = true
            onManageDialogDismissed()
        }
    }

    LaunchedEffect(initialUrlToLoad) {
        if (!initialUrlToLoad.isNullOrBlank()) {
            BrowserDestination.fromInput(initialUrlToLoad)?.let { viewModel.loadUrl(it) }
        }
    }

    // Intercept Back button when browsing
    BackHandler(enabled = active && state.currentUrl != null) {
        val webView = activeWebView
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            returnToStartPage()
        }
    }

    if (showAddDialog) {
        WebsiteDialog(
            title = R.string.save_website,
            confirmLabel = R.string.save_and_open,
            onDismiss = { showAddDialog = false },
            onSave = { name, url ->
                showAddDialog = false
                viewModel.addWebsite(name, url)
                viewModel.loadUrl(url)
            }
        )
    }

    editingWebsite?.let { website ->
        WebsiteDialog(
            title = R.string.edit_website,
            confirmLabel = R.string.save_changes,
            initialName = website.name,
            initialUrl = website.origin,
            onDismiss = { editingWebsite = null },
            onSave = { name, url ->
                viewModel.updateWebsite(website.origin, name, url)
                editingWebsite = null
            }
        )
    }

    if (showManageDialog || showBookmarksSheet) {
        ManageWebsiteDataDialog(
            websites = state.websites,
            onDismiss = {
                showManageDialog = false
                showBookmarksSheet = false
            },
            onOpenWebsite = { url ->
                showBookmarksSheet = false
                showManageDialog = false
                viewModel.loadUrl(url)
            },
            onClearSelected = {
                showManageDialog = false
                showBookmarksSheet = false
                viewModel.clearSelected(it)
            },
            onClearAll = {
                showManageDialog = false
                showBookmarksSheet = false
                activeWebView?.clearFocus()
                viewModel.clearAll()
            }
        )
    }

    if (showMenuSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMenuSheet = false },
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
            ),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            BrowserExpressiveMenu(
                currentUrl = state.currentUrl.orEmpty(),
                pageTitle = state.pageTitle,
                favicon = activeWebView?.favicon,
                isBookmarked = isBookmarked,
                isDesktopSite = state.isDesktopSite,
                useSessions = state.useSessions,
                canGoBack = state.canGoBack,
                canGoForward = state.canGoForward,
                isLoading = state.isLoading,
                onGoBack = {
                    context.performHaptic(HapticSignal.CLICK)
                    showMenuSheet = false
                    viewModel.goBack()
                },
                onGoForward = {
                    context.performHaptic(HapticSignal.CLICK)
                    showMenuSheet = false
                    viewModel.goForward()
                },
                onReload = {
                    context.performHaptic(HapticSignal.CLICK)
                    showMenuSheet = false
                    viewModel.reload()
                },
                onStop = {
                    context.performHaptic(HapticSignal.CLICK)
                    showMenuSheet = false
                    viewModel.stop()
                },
                onHome = {
                    context.performHaptic(HapticSignal.CLICK)
                    showMenuSheet = false
                    returnToStartPage()
                },
                onDownloadMedia = {
                    showMenuSheet = false
                    triggerMediaDownload()
                },
                onToggleBookmark = toggleBookmark,
                onBookmarks = {
                    showMenuSheet = false
                    showBookmarksSheet = true
                },
                onToggleDesktopSite = {
                    context.performHaptic(HapticSignal.CLICK)
                    viewModel.toggleDesktopSite()
                },
                onToggleSessions = {
                    context.performHaptic(HapticSignal.CLICK)
                    viewModel.setUseSessions(!state.useSessions)
                },
                onCopyLink = {
                    showMenuSheet = false
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("URL", state.currentUrl.orEmpty()))
                    Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                },
                onShareLink = {
                    showMenuSheet = false
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, state.currentUrl.orEmpty())
                    }
                    context.startActivity(Intent.createChooser(shareIntent, null))
                },
                onClearData = {
                    showMenuSheet = false
                    showManageDialog = true
                },
                onOpenExternal = {
                    showMenuSheet = false
                    state.currentUrl?.let {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri()))
                        }
                    }
                },
                onExitToDownloader = {
                    showMenuSheet = false
                    onExitToDownloader()
                }
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (state.currentUrl == null) {
            // STATE 1: Start Page
            BrowserStartPage(
                state = state,
                contentPadding = contentPadding,
                onSearchOrNavigate = { input ->
                    val destination = BrowserDestination.fromInput(input)
                    if (destination != null) {
                        viewModel.loadUrl(destination)
                    }
                },
                onOpenWebsite = { url -> viewModel.loadUrl(url) },
                onAddWebsite = { showAddDialog = true },
                onEditWebsite = { editingWebsite = it },
                onManageWebsiteData = { showManageDialog = true },
                onToggleSessions = { viewModel.setUseSessions(it) }
            )
        } else {
            // STATE 2: Browsing Mode (On a website, matching Reference Image 2)
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Address Bar
                BrowserTopBar(
                    currentUrl = state.currentUrl.orEmpty(),
                    isSecure = state.isSecure,
                    isLoading = state.isLoading,
                    progress = state.progress,
                    isEditing = isEditingAddress,
                    onStartEditing = {
                        searchInput = state.currentUrl.orEmpty()
                        isEditingAddress = true
                    },
                    onCancelEditing = { isEditingAddress = false },
                    onNavigate = { query ->
                        isEditingAddress = false
                        BrowserDestination.fromInput(query)?.let { viewModel.loadUrl(it) }
                    }
                )

                // Interactive WebView
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    BrowserWebViewContainer(
                        url = state.currentUrl.orEmpty(),
                        active = active,
                        savedState = viewModel.savedWebViewState,
                        onSaveState = viewModel::saveWebViewState,
                        useSessions = state.useSessions,
                        savedSession = state.savedSession,
                        isDesktopSite = state.isDesktopSite,
                        navigationAction = state.navigationAction,
                        onConsumeNavigationAction = { viewModel.consumeNavigationAction() },
                        onPageStateChanged = { url, title, canBack, canForward, isSec ->
                            viewModel.updatePageState(url, title, canBack, canForward, isSec)
                        },
                        onProgressChanged = { progress, loading ->
                            viewModel.updateProgress(progress, loading)
                        },
                        onIconReceived = viewModel::updateWebsiteIcon,
                        onPageVisited = viewModel::recordVisit,
                        onSessionCaptured = viewModel::saveSession,
                        onWebViewReady = { webView ->
                            extractionJob?.cancel()
                            activeWebView = webView
                        }
                    )
                }

                // Bottom Browser Toolbar (Home, Bookmarks, Search, Download, 3-Dot Menu)
                BrowserBottomToolbar(
                    isBookmarked = isBookmarked,
                    onHome = {
                        context.performHaptic(HapticSignal.CLICK)
                        returnToStartPage()
                    },
                    onToggleBookmark = toggleBookmark,
                    onSearch = {
                        context.performHaptic(HapticSignal.CLICK)
                        searchInput = state.currentUrl.orEmpty()
                        isEditingAddress = true
                    },
                    onDownloadMedia = triggerMediaDownload,
                    onOpenMenu = {
                        context.performHaptic(HapticSignal.CLICK)
                        showMenuSheet = true
                    }
                )
            }
        }
    }
}

/**
 * Start Page:
 * - Search bar pill
 * - Smart, compact Session Protection bar
 * - Saved Websites speed-dial grid
 */
