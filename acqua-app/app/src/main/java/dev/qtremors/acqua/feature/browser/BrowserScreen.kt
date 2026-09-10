package dev.qtremors.acqua.feature.browser

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.qtremors.acqua.R
import dev.qtremors.acqua.auth.LivePageExtractionFailure
import dev.qtremors.acqua.auth.LivePageExtractionOutcome
import dev.qtremors.acqua.auth.LivePageMediaCollector
import dev.qtremors.acqua.data.session.InstagramSessionStore
import dev.qtremors.acqua.data.session.SavedInstagramSession
import dev.qtremors.acqua.data.session.SavedWebsite
import dev.qtremors.acqua.data.session.SavedWebsiteRepository
import dev.qtremors.acqua.domain.BrowserDestination
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.feature.downloader.DownloadActivity
import dev.qtremors.acqua.platform.HapticSignal
import dev.qtremors.acqua.platform.performHaptic
import dev.qtremors.acqua.resolver.web.RenderedPageResolverActivity
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

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onExitToDownloader: () -> Unit,
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
    val savedWebsitesRepo = remember { SavedWebsiteRepository(context) }

    var showAddDialog by remember { mutableStateOf(false) }
    var showManageDialog by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var editingWebsite by remember { mutableStateOf<SavedWebsite?>(null) }
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    var isEditingAddress by remember { mutableStateOf(false) }
    var searchInput by remember { mutableStateOf("") }
    var extractionPending by remember { mutableStateOf(false) }
    var extractionJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val currentActive by rememberUpdatedState(active)

    val liveMediaCollector = remember { LivePageMediaCollector() }

    DisposableEffect(activeWebView, state.currentUrl, active) {
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
            viewModel.goHome()
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
                viewModel.clearAll()
            }
        )
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
                        useSessions = state.useSessions,
                        isDesktopSite = state.isDesktopSite,
                        navigationAction = state.navigationAction,
                        onConsumeNavigationAction = { viewModel.consumeNavigationAction() },
                        onPageStateChanged = { url, title, canBack, canForward, isSec ->
                            viewModel.updatePageState(url, title, canBack, canForward, isSec)
                        },
                        onProgressChanged = { progress, loading ->
                            viewModel.updateProgress(progress, loading)
                        },
                        onIconReceived = { pageUrl, bitmap ->
                            savedWebsitesRepo.updateIcon(pageUrl, bitmap)
                        },
                        onWebViewReady = { webView ->
                            extractionJob?.cancel()
                            activeWebView = webView
                        }
                    )
                }

                // Bottom Browser Toolbar (Home, Bookmarks, Search, Download, 3-Dot Menu)
                BrowserBottomToolbar(
                    canGoBack = state.canGoBack,
                    canGoForward = state.canGoForward,
                    isLoading = state.isLoading,
                    isDesktopSite = state.isDesktopSite,
                    useSessions = state.useSessions,
                    currentUrl = state.currentUrl.orEmpty(),
                    onHome = {
                        context.performHaptic(HapticSignal.CLICK)
                        viewModel.goHome()
                    },
                    onBookmarks = {
                        context.performHaptic(HapticSignal.CLICK)
                        showBookmarksSheet = true
                    },
                    onSearch = {
                        context.performHaptic(HapticSignal.CLICK)
                        searchInput = state.currentUrl.orEmpty()
                        isEditingAddress = true
                    },
                    onDownloadMedia = {
                        val wv = activeWebView
                        if (wv != null && !extractionPending) {
                            extractionPending = true
                            context.performHaptic(HapticSignal.CLICK)
                            Toast.makeText(context, R.string.resolving_media, Toast.LENGTH_SHORT).show()
                            val sourceUrl = state.currentUrl.orEmpty()
                            extractionJob = scope.launch {
                                try {
                                    extractMediaFromPage(context, wv, sourceUrl, liveMediaCollector) {
                                        currentActive && viewModel.state.value.currentUrl == sourceUrl
                                    }
                                } finally {
                                    extractionPending = false
                                }
                            }
                        }
                    },
                    onReload = { viewModel.reload() },
                    onStop = { viewModel.stop() },
                    onGoBack = { viewModel.goBack() },
                    onGoForward = { viewModel.goForward() },
                    onAddBookmark = {
                        state.currentUrl?.let { url ->
                            val host = WebLink.host(url) ?: url
                            viewModel.addWebsite(host, url)
                            Toast.makeText(context, R.string.saved_to_downloads, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onToggleDesktopSite = { viewModel.toggleDesktopSite() },
                    onToggleSessions = { viewModel.setUseSessions(!state.useSessions) },
                    onCopyLink = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("URL", state.currentUrl.orEmpty()))
                        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                    },
                    onShareLink = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, state.currentUrl.orEmpty())
                        }
                        context.startActivity(Intent.createChooser(shareIntent, null))
                    },
                    onClearData = {
                        showManageDialog = true
                    },
                    onOpenExternal = {
                        state.currentUrl?.let {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                            }
                        }
                    },
                    onExitToDownloader = onExitToDownloader
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
@Composable
private fun BrowserStartPage(
    state: BrowserUiState,
    contentPadding: PaddingValues,
    onSearchOrNavigate: (String) -> Unit,
    onOpenWebsite: (String) -> Unit,
    onAddWebsite: () -> Unit,
    onEditWebsite: (SavedWebsite) -> Unit,
    onManageWebsiteData: () -> Unit,
    onToggleSessions: (Boolean) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 18.dp,
                end = 18.dp,
                top = 14.dp,
                bottom = contentPadding.calculateBottomPadding() + 28.dp
            )
    ) {
        // 1. Search bar pill
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = colors.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.browser_search_or_type_url),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (query.isNotBlank()) onSearchOrNavigate(query.trim())
                    }),
                    modifier = Modifier.weight(1f)
                )
                if (query.isNotBlank()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Clear, stringResource(R.string.clear_link), tint = colors.onSurfaceVariant)
                    }
                }
            }
        }

        // 2. Smart, compact Session Protection bar
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (state.useSessions) colors.primary.copy(alpha = 0.15f)
                            else colors.surfaceContainerHighest
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.useSessions) Icons.Filled.Lock else Icons.Filled.Security,
                        contentDescription = null,
                        tint = if (state.useSessions) colors.primary else colors.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.browser_sessions),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface
                    )
                    Text(
                        text = if (state.useSessions) stringResource(R.string.browser_sessions_active_desc)
                               else stringResource(R.string.browser_sessions_inactive_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onManageWebsiteData,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.manage_website_data),
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Switch(
                    checked = state.useSessions,
                    onCheckedChange = onToggleSessions
                )
            }
        }

        // 3. Saved Websites Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.saved_websites),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )
            if (state.websites.isNotEmpty()) {
                Text(
                    text = "${state.websites.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary
                )
            }
        }

        // Speed Dial FlowRow (wraps and shows all shortcuts nicely)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.websites.forEach { website ->
                WebsiteTile(
                    website = website,
                    onOpen = { onOpenWebsite(website.origin) },
                    onEdit = { onEditWebsite(website) }
                )
            }
            AddWebsiteTile(onClick = onAddWebsite)
        }
    }
}

/**
 * Top Address Bar when browsing a website (matching Reference Image 2):
 * - SSL lock indicator
 * - Current host or formatted URL
 * - Shield icon
 * - Smooth progress bar underneath while loading
 */
@Composable
private fun BrowserTopBar(
    currentUrl: String,
    isSecure: Boolean,
    isLoading: Boolean,
    progress: Int,
    isEditing: Boolean,
    onStartEditing: () -> Unit,
    onCancelEditing: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var editingText by remember(isEditing, currentUrl) { mutableStateOf(currentUrl) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .background(colors.surfaceContainerHigh)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            if (isEditing) {
                IconButton(onClick = onCancelEditing, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = colors.onSurface)
                }
                OutlinedTextField(
                    value = editingText,
                    onValueChange = { editingText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.outline
                    ),
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        if (editingText.isNotBlank()) onNavigate(editingText.trim())
                    }),
                    trailingIcon = {
                        if (editingText.isNotBlank()) {
                            IconButton(onClick = { editingText = "" }) {
                                Icon(Icons.Filled.Clear, stringResource(R.string.clear_link))
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                )
                IconButton(
                    onClick = { if (editingText.isNotBlank()) onNavigate(editingText.trim()) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Filled.Public, stringResource(R.string.go), tint = colors.primary)
                }
            } else {
                Surface(
                    onClick = onStartEditing,
                    shape = RoundedCornerShape(22.dp),
                    color = colors.surfaceContainerHighest.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Icon(
                            imageVector = if (isSecure) Icons.Filled.Lock else Icons.Filled.Security,
                            contentDescription = stringResource(if (isSecure) R.string.secure_connection else R.string.insecure_connection),
                            tint = if (isSecure) colors.onSurfaceVariant else colors.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = formatDisplayAddress(currentUrl),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = stringResource(R.string.secure_connection),
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        if (isLoading && progress < 100) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                color = colors.primary,
                trackColor = colors.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
            )
        }
    }
}

/**
 * Bottom Browser Toolbar (matching Reference Image 2):
 * - Home icon
 * - Bookmarks icon
 * - Search icon
 * - Download Page Media action
 * - 3-Dot Overflow Menu
 */
@Composable
private fun BrowserBottomToolbar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    isLoading: Boolean,
    isDesktopSite: Boolean,
    useSessions: Boolean,
    currentUrl: String,
    onHome: () -> Unit,
    onBookmarks: () -> Unit,
    onSearch: () -> Unit,
    onDownloadMedia: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onAddBookmark: () -> Unit,
    onToggleDesktopSite: () -> Unit,
    onToggleSessions: () -> Unit,
    onCopyLink: () -> Unit,
    onShareLink: () -> Unit,
    onClearData: () -> Unit,
    onOpenExternal: () -> Unit,
    onExitToDownloader: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = colors.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            // 1. Home button
            IconButton(onClick = onHome, modifier = Modifier.size(46.dp)) {
                Icon(Icons.Filled.Home, stringResource(R.string.home), tint = colors.onSurface)
            }

            // 2. Bookmarks button
            IconButton(onClick = onBookmarks, modifier = Modifier.size(46.dp)) {
                Icon(Icons.Filled.BookmarkBorder, stringResource(R.string.bookmarks), tint = colors.onSurface)
            }

            // 3. Search button
            IconButton(onClick = onSearch, modifier = Modifier.size(46.dp)) {
                Icon(Icons.Filled.Search, stringResource(R.string.search_or_enter_website), tint = colors.onSurface)
            }

            // 4. Download media action
            IconButton(onClick = onDownloadMedia, modifier = Modifier.size(46.dp)) {
                Icon(Icons.Filled.Download, stringResource(R.string.download_page_media), tint = colors.primary)
            }

            // 5. 3-Dot Overflow Menu
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(46.dp)) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options), tint = colors.onSurface)
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.width(250.dp)
                ) {
                    // Navigation row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(onClick = { onGoBack(); menuExpanded = false }, enabled = canGoBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                        IconButton(onClick = { onGoForward(); menuExpanded = false }, enabled = canGoForward) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.forward))
                        }
                        IconButton(onClick = {
                            if (isLoading) onStop() else onReload()
                            menuExpanded = false
                        }) {
                            Icon(
                                if (isLoading) Icons.Filled.Clear else Icons.Filled.Refresh,
                                stringResource(if (isLoading) R.string.stop else R.string.refresh)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.download_page_media)) },
                        leadingIcon = { Icon(Icons.Filled.Download, null, tint = colors.primary) },
                        onClick = { onDownloadMedia(); menuExpanded = false }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.save_website)) },
                        leadingIcon = { Icon(Icons.Filled.Bookmark, null) },
                        onClick = { onAddBookmark(); menuExpanded = false }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.desktop_site)) },
                        leadingIcon = { Icon(Icons.Filled.DesktopMac, null) },
                        trailingIcon = {
                            Checkbox(checked = isDesktopSite, onCheckedChange = { onToggleDesktopSite(); menuExpanded = false })
                        },
                        onClick = { onToggleDesktopSite(); menuExpanded = false }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.use_browser_sessions)) },
                        leadingIcon = { Icon(Icons.Filled.Security, null) },
                        trailingIcon = {
                            Switch(checked = useSessions, onCheckedChange = { onToggleSessions(); menuExpanded = false })
                        },
                        onClick = { onToggleSessions(); menuExpanded = false }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.copy_link)) },
                        leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                        onClick = { onCopyLink(); menuExpanded = false }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share_link)) },
                        leadingIcon = { Icon(Icons.Filled.Share, null) },
                        onClick = { onShareLink(); menuExpanded = false }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manage_website_data)) },
                        leadingIcon = { Icon(Icons.Filled.DeleteOutline, null) },
                        onClick = { onClearData(); menuExpanded = false }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.open_in_external_browser)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                        onClick = { onOpenExternal(); menuExpanded = false }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.exit_to_app)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.primary) },
                        onClick = { onExitToDownloader(); menuExpanded = false }
                    )
                }
            }
        }
    }
}

/**
 * Container hosting the interactive WebView
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserWebViewContainer(
    url: String,
    active: Boolean,
    useSessions: Boolean,
    isDesktopSite: Boolean,
    navigationAction: dev.qtremors.acqua.feature.browser.BrowserNavigationAction?,
    onConsumeNavigationAction: () -> Unit,
    onPageStateChanged: (url: String?, title: String?, canBack: Boolean, canForward: Boolean, isSecure: Boolean) -> Unit,
    onProgressChanged: (progress: Int, isLoading: Boolean) -> Unit,
    onIconReceived: (url: String, icon: Bitmap) -> Unit,
    onWebViewReady: (WebView?) -> Unit
) {
    val context = LocalContext.current
    val instagramSessions = remember { InstagramSessionStore(context) }
    val savedWebsitesRepo = remember { SavedWebsiteRepository(context) }
    val currentUseSessions by rememberUpdatedState(useSessions)

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentLoadedUrl by remember { mutableStateOf<String?>(null) }

    // Handle incoming navigation actions from ViewModel
    LaunchedEffect(navigationAction, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        when (navigationAction) {
            dev.qtremors.acqua.feature.browser.BrowserNavigationAction.RELOAD -> wv.reload()
            dev.qtremors.acqua.feature.browser.BrowserNavigationAction.STOP -> wv.stopLoading()
            dev.qtremors.acqua.feature.browser.BrowserNavigationAction.BACK -> if (wv.canGoBack()) wv.goBack()
            dev.qtremors.acqua.feature.browser.BrowserNavigationAction.FORWARD -> if (wv.canGoForward()) wv.goForward()
            null -> {}
        }
        if (navigationAction != null) onConsumeNavigationAction()
    }

    // Handle desktop mode toggle
    LaunchedEffect(isDesktopSite, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        val userAgent = if (isDesktopSite) DESKTOP_USER_AGENT else WebSettings.getDefaultUserAgent(context)
        if (wv.settings.userAgentString != userAgent) {
            wv.settings.userAgentString = userAgent
            wv.reload()
        }
    }

    LaunchedEffect(active, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (active) wv.onResume() else wv.onPause()
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.rgb(12, 16, 20))

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    mediaPlaybackRequiresUserGesture = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    if (isDesktopSite) userAgentString = DESKTOP_USER_AGENT
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
                }

                val webViewInstance = this
                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    setAcceptThirdPartyCookies(webViewInstance, false)
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgressChanged(newProgress, newProgress < 100)
                        view?.let {
                            val activeUrl = it.url
                            val isSecure = activeUrl?.startsWith("https://") == true
                            onPageStateChanged(activeUrl, it.title, it.canGoBack(), it.canGoForward(), isSecure)
                        }
                    }

                    override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                        super.onReceivedIcon(view, icon)
                        if (icon != null && view?.url != null) {
                            onIconReceived(view.url!!, icon)
                        }
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val scheme = request.url.scheme?.lowercase()
                        if (scheme == "http" || scheme == "https") return false
                        return true
                    }

                    override fun onPageStarted(view: WebView, pageUrl: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, pageUrl, favicon)
                        onProgressChanged(15, true)
                        pageUrl?.let {
                            currentLoadedUrl = it
                            val isSecure = it.startsWith("https://")
                            onPageStateChanged(it, view.title, view.canGoBack(), view.canGoForward(), isSecure)
                            if (favicon != null) onIconReceived(it, favicon)
                        }
                    }

                    override fun onPageFinished(view: WebView, pageUrl: String?) {
                        super.onPageFinished(view, pageUrl)
                        onProgressChanged(100, false)
                        pageUrl?.let {
                            currentLoadedUrl = it
                            val isSecure = it.startsWith("https://")
                            onPageStateChanged(it, view.title, view.canGoBack(), view.canGoForward(), isSecure)
                            savedWebsitesRepo.record(it)
                            if (currentUseSessions && WebLink.isInstagramHost(it)) {
                                val cookies = CookieManager.getInstance().getCookie("https://www.instagram.com").orEmpty()
                                if (cookies.contains("sessionid=")) {
                                    runCatching {
                                        instagramSessions.save(SavedInstagramSession(cookies, view.settings.userAgentString.orEmpty()))
                                    }
                                }
                            }
                            CookieManager.getInstance().flush()
                        }
                    }
                }

                // Inject Instagram session cookies if enabled
                if (useSessions && WebLink.isInstagramHost(url)) {
                    val saved = instagramSessions.load()
                    if (saved != null) {
                        val cookieManager = CookieManager.getInstance()
                        saved.cookies.split(';').forEach { raw ->
                            val parts = raw.trim().split('=', limit = 2)
                            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                                cookieManager.setCookie(
                                    "https://www.instagram.com",
                                    "${parts[0]}=${parts[1]}; Domain=.instagram.com; Path=/; Secure; SameSite=None"
                                )
                            }
                        }
                        cookieManager.flush()
                    }
                }

                currentLoadedUrl = url
                loadUrl(url)
                webViewRef = this
                onWebViewReady(this)
            }
        },
        update = { webView ->
            if (!url.isBlank() && url != currentLoadedUrl && url != webView.url) {
                currentLoadedUrl = url
                webView.loadUrl(url)
            }
        },
        onRelease = { webView ->
            onWebViewReady(null)
            webViewRef = null
            webView.stopLoading()
            webView.onPause()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.removeAllViews()
            webView.destroy()
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Format address display (e.g. x.com/home)
 */
private fun formatDisplayAddress(url: String): String {
    val normalized = WebLink.normalize(url) ?: url
    val host = WebLink.host(normalized) ?: return normalized
    val withoutScheme = normalized.removePrefix("https://").removePrefix("http://").removePrefix("www.")
    return withoutScheme.take(45)
}

/**
 * Media Extraction Helper from live page
 */
private suspend fun extractMediaFromPage(
    context: Context,
    webView: WebView,
    sourceUrl: String,
    collector: LivePageMediaCollector,
    isCurrentPage: () -> Boolean
) {
    collector.reset()
    while (true) {
        // Never combine media from a new page with the original source URL.
        if (!isCurrentPage() || WebLink.normalize(webView.url.orEmpty()) != WebLink.normalize(sourceUrl)) return
        val rawValue = suspendCancellableCoroutine<String?> { continuation ->
            webView.evaluateJavascript(RenderedPageResolverActivity.EXTRACTION_SCRIPT) { value ->
                if (continuation.isActive) continuation.resume(value)
            }
        }
        if (!isCurrentPage() || WebLink.normalize(webView.url.orEmpty()) != WebLink.normalize(sourceUrl)) return
        val payload = RenderedPageResolverActivity.decodeJavascriptResult(rawValue)
        when (val outcome = collector.consume(payload, sourceUrl)) {
            LivePageExtractionOutcome.Continue -> {
                delay(500L)
            }
            is LivePageExtractionOutcome.Complete -> {
                val json = JSONArray().apply {
                    outcome.media.forEach { item ->
                        put(
                            JSONObject()
                                .put("url", item.url)
                                .put("isVideo", item.isVideo)
                                .put("thumbnailUrl", item.thumbnailUrl)
                                .put("width", item.width)
                                .put("height", item.height)
                                .put("username", item.username)
                                .put("referer", item.referer)
                                .put("sourceTimestampMillis", item.sourceTimestampMillis)
                        )
                    }
                }
                context.startActivity(
                    Intent(context, DownloadActivity::class.java)
                        .putExtra(DownloadActivity.EXTRA_URL, sourceUrl)
                        .putExtra(DownloadActivity.EXTRA_MEDIA_JSON, json.toString())
                )
                return
            }
            is LivePageExtractionOutcome.Failed -> {
                val msg = when (outcome.reason) {
                    LivePageExtractionFailure.SESSION_EXPIRED -> R.string.session_expired
                    LivePageExtractionFailure.CONTENT_UNAVAILABLE -> R.string.content_unavailable
                    LivePageExtractionFailure.NO_MEDIA -> R.string.no_media_found
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                return
            }
        }
    }
}

@Composable
private fun WebsiteDialog(
    title: Int,
    confirmLabel: Int,
    initialName: String = "",
    initialUrl: String = "",
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var error by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    name, { name = it.take(40); error = null },
                    label = { Text(stringResource(R.string.name)) },
                    placeholder = { Text(stringResource(R.string.website_name_hint)) },
                    singleLine = true,
                    isError = error == R.string.enter_name,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    url, { url = it; error = null },
                    label = { Text(stringResource(R.string.website_url)) },
                    placeholder = { Text(stringResource(R.string.website_url_hint)) },
                    singleLine = true,
                    isError = error == R.string.enter_valid_website,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val normalized = WebLink.normalize(url)
                when {
                    name.isBlank() -> error = R.string.enter_name
                    normalized == null -> error = R.string.enter_valid_website
                    else -> onSave(name.trim(), normalized)
                }
            }) { Text(stringResource(confirmLabel)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun ManageWebsiteDataDialog(
    websites: List<SavedWebsite>,
    onDismiss: () -> Unit,
    onOpenWebsite: ((String) -> Unit)? = null,
    onClearSelected: (Set<String>) -> Unit,
    onClearAll: () -> Unit
) {
    var selected by remember(websites) { mutableStateOf(emptySet<String>()) }
    val allOrigins = websites.mapTo(mutableSetOf(), SavedWebsite::origin)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.saved_websites)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (websites.isEmpty()) Text(stringResource(R.string.no_saved_websites)) else {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(stringResource(R.string.select_websites), style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = { selected = if (selected == allOrigins) emptySet() else allOrigins }) {
                            Text(stringResource(if (selected == allOrigins) R.string.deselect_all else R.string.select_all))
                        }
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        websites.forEach { website ->
                            val checked = website.origin in selected
                            Surface(
                                onClick = {
                                    if (onOpenWebsite != null) {
                                        onOpenWebsite(website.origin)
                                    } else {
                                        selected = if (checked) selected - website.origin else selected + website.origin
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked, onCheckedChange = {
                                        selected = if (checked) selected - website.origin else selected + website.origin
                                    })
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(website.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(website.host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.website_data_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onClearAll, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.clear_all_browser_data), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onClearSelected(selected) }, enabled = selected.isNotEmpty()) {
                Text(stringResource(R.string.clear_selected))
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WebsiteTile(
    website: SavedWebsite,
    onOpen: () -> Unit,
    onEdit: () -> Unit
) {
    val iconKey = website.iconFile?.let { "${it.absolutePath}:${it.lastModified()}" }
    val icon by produceState<ImageBitmap?>(null, iconKey) {
        value = withContext(Dispatchers.IO) {
            website.iconFile?.takeIf(File::isFile)?.let { BitmapFactory.decodeFile(it.absolutePath) }?.asImageBitmap()
        }
    }
    Surface(
        modifier = Modifier
            .width(96.dp)
            .height(112.dp)
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onEdit
            ),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            Modifier.padding(10.dp),
            Arrangement.Center,
            Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                icon?.let {
                    Image(
                        it,
                        stringResource(R.string.website_icon_description, website.name, website.host),
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } ?: Icon(Icons.Filled.Public, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                website.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AddWebsiteTile(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(96.dp)
            .height(112.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.save_website),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.save_website),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
