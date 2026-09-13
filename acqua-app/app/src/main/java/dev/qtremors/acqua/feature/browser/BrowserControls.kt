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
import androidx.compose.material3.rememberModalBottomSheetState
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
import dev.qtremors.acqua.R
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

@Composable
internal fun BrowserTopBar(
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
internal fun BrowserBottomToolbar(
    isBookmarked: Boolean,
    onHome: () -> Unit,
    onToggleBookmark: () -> Unit,
    onSearch: () -> Unit,
    onDownloadMedia: () -> Unit,
    onOpenMenu: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

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
            IconButton(onClick = onToggleBookmark, modifier = Modifier.size(46.dp)) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = stringResource(
                        if (isBookmarked) R.string.remove_bookmark else R.string.save_website
                    ),
                    tint = if (isBookmarked) colors.primary else colors.onSurface
                )
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
            IconButton(onClick = onOpenMenu, modifier = Modifier.size(46.dp)) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options), tint = colors.onSurface)
            }
        }
    }
}

/**
 * Material 3 Expressive Browser Overflow Menu:
 * - Header status card with website favicon, page title, host, and SSL lock badge
 * - Segmented cards grouping related actions:
 *     1. Media & Bookmarks (Download media, Bookmark toggle, Bookmarks list)
 *     2. Page Options (Desktop site switch, Session protection switch)
 *     3. Link & Tools (Copy link, Share link, Open external, Manage website data)
 *     4. Exit navigation (Return to Downloader)
 * - Pinned Bottom Quick Actions Pill (Back, Forward, Stop/Refresh, Home) right at thumb level
 */
@Composable
internal fun BrowserExpressiveMenu(
    currentUrl: String,
    pageTitle: String?,
    favicon: Bitmap?,
    isBookmarked: Boolean,
    isDesktopSite: Boolean,
    useSessions: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isLoading: Boolean,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onHome: () -> Unit,
    onDownloadMedia: () -> Unit,
    onToggleBookmark: () -> Unit,
    onBookmarks: () -> Unit,
    onToggleDesktopSite: () -> Unit,
    onToggleSessions: () -> Unit,
    onCopyLink: () -> Unit,
    onShareLink: () -> Unit,
    onClearData: () -> Unit,
    onOpenExternal: () -> Unit,
    onExitToDownloader: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val host = remember(currentUrl) {
        WebLink.host(currentUrl)?.removePrefix("www.").orEmpty()
    }
    val isHttps = remember(currentUrl) {
        currentUrl.startsWith("https://", ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 8.dp)
    ) {
        // Scrollable content area containing header and segmented cards
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Info Card (Favicon, Title, Host, Security Badge)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colors.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val safeFavicon = favicon?.takeUnless { it.isRecycled }
                    if (safeFavicon != null) {
                        Image(
                            bitmap = safeFavicon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = if (isHttps) colors.primaryContainer else colors.surfaceContainerHighest,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isHttps) Icons.Filled.Lock else Icons.Filled.Security,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isHttps) colors.onPrimaryContainer else colors.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pageTitle?.takeIf { it.isNotBlank() } ?: host.ifBlank { currentUrl },
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = host.ifBlank { currentUrl },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Segment 1: Media & Bookmarks
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ExpressiveMenuItem(
                        title = stringResource(R.string.download_page_media),
                        icon = Icons.Filled.Download,
                        iconTint = colors.primary,
                        onClick = onDownloadMedia
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = colors.outlineVariant.copy(alpha = 0.35f)
                    )
                    ExpressiveMenuItem(
                        title = stringResource(if (isBookmarked) R.string.remove_bookmark else R.string.save_website),
                        icon = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        iconTint = if (isBookmarked) colors.primary else colors.onSurface,
                        onClick = onToggleBookmark
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = colors.outlineVariant.copy(alpha = 0.35f)
                    )
                    ExpressiveMenuItem(
                        title = stringResource(R.string.bookmarks),
                        icon = Icons.Filled.BookmarkBorder,
                        iconTint = colors.onSurface,
                        onClick = onBookmarks
                    )
                }
            }

            // Segment 2: Page Options (Desktop site, Sessions)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ExpressiveMenuItem(
                        title = stringResource(R.string.desktop_site),
                        icon = Icons.Filled.DesktopMac,
                        onClick = onToggleDesktopSite,
                        checked = isDesktopSite,
                        trailing = {
                            Switch(
                                checked = isDesktopSite,
                                onCheckedChange = null,
                                modifier = Modifier.clearAndSetSemantics { }
                            )
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = colors.outlineVariant.copy(alpha = 0.35f)
                    )
                    ExpressiveMenuItem(
                        title = stringResource(R.string.use_browser_sessions),
                        icon = Icons.Filled.Security,
                        onClick = onToggleSessions,
                        checked = useSessions,
                        trailing = {
                            Switch(
                                checked = useSessions,
                                onCheckedChange = null,
                                modifier = Modifier.clearAndSetSemantics { }
                            )
                        }
                    )
                }
            }

            // Segment 3: Link & Tools
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ExpressiveMenuItem(
                        title = stringResource(R.string.copy_link),
                        icon = Icons.Filled.ContentCopy,
                        onClick = onCopyLink
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = colors.outlineVariant.copy(alpha = 0.35f)
                    )
                    ExpressiveMenuItem(
                        title = stringResource(R.string.share_link),
                        icon = Icons.Filled.Share,
                        onClick = onShareLink
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = colors.outlineVariant.copy(alpha = 0.35f)
                    )
                    ExpressiveMenuItem(
                        title = stringResource(R.string.open_in_external_browser),
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        onClick = onOpenExternal
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = colors.outlineVariant.copy(alpha = 0.35f)
                    )
                    ExpressiveMenuItem(
                        title = stringResource(R.string.manage_website_data),
                        icon = Icons.Filled.DeleteOutline,
                        onClick = onClearData
                    )
                }
            }

            // Segment 4: App Navigation
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                ExpressiveMenuItem(
                    title = stringResource(R.string.exit_to_app),
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    iconTint = colors.primary,
                    onClick = onExitToDownloader
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Pinned Bottom Quick Actions Pill (Back, Forward, Refresh/Stop, Home)
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = colors.surfaceContainerHighest,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onGoBack,
                    enabled = canGoBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
                IconButton(
                    onClick = onGoForward,
                    enabled = canGoForward,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.forward)
                    )
                }
                IconButton(
                    onClick = { if (isLoading) onStop() else onReload() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        if (isLoading) Icons.Filled.Clear else Icons.Filled.Refresh,
                        contentDescription = stringResource(if (isLoading) R.string.stop else R.string.refresh)
                    )
                }
                IconButton(
                    onClick = onHome,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Filled.Home,
                        contentDescription = stringResource(R.string.home)
                    )
                }
            }
        }
    }
}

@Composable
internal fun ExpressiveMenuItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    checked: Boolean? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val actionModifier = if (checked == null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier.toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = { onClick() }
        )
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(actionModifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

/**
 * Container hosting the interactive WebView
 */
// The touch listener observes scrolling and does not consume WebView click events.
