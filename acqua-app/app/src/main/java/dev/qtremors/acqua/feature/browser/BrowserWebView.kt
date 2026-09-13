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

internal const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
internal fun BrowserWebViewContainer(
    url: String,
    active: Boolean,
    savedState: Bundle?,
    onSaveState: (String?, Bundle) -> Unit,
    useSessions: Boolean,
    savedSession: SavedInstagramSession?,
    isDesktopSite: Boolean,
    navigationAction: dev.qtremors.acqua.feature.browser.BrowserNavigationAction?,
    onConsumeNavigationAction: () -> Unit,
    onPageStateChanged: (url: String?, title: String?, canBack: Boolean, canForward: Boolean, isSecure: Boolean) -> Unit,
    onProgressChanged: (progress: Int, isLoading: Boolean) -> Unit,
    onIconReceived: (url: String, icon: Bitmap) -> Unit,
    onPageVisited: (url: String) -> Unit,
    onSessionCaptured: (SavedInstagramSession) -> Unit,
    onScrollDirection: (Boolean) -> Unit,
    onWebViewReady: (WebView?) -> Unit
) {
    val context = LocalContext.current
    val currentUseSessions by rememberUpdatedState(useSessions)
    val currentOnScrollDirection by rememberUpdatedState(onScrollDirection)

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentLoadedUrl by remember { mutableStateOf<String?>(null) }

    DisposableEffect(webViewRef) {
        val webView = webViewRef
        onDispose { webView?.clearFocus() }
    }

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
        if (active) {
            wv.onResume()
        } else {
            wv.clearFocus()
            wv.onPause()
        }
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

                var lastTouchY = 0f
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> lastTouchY = event.y
                        MotionEvent.ACTION_MOVE -> {
                            val delta = event.y - lastTouchY
                            if (kotlin.math.abs(delta) > 16 * resources.displayMetrics.density) {
                                currentOnScrollDirection(delta > 0 || scrollY == 0)
                                lastTouchY = event.y
                            }
                        }
                    }
                    false
                }
                setOnScrollChangeListener { _, _, scrollY, _, _ ->
                    if (scrollY == 0) currentOnScrollDirection(true)
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
                            onPageVisited(it)
                            if (currentUseSessions && WebLink.isInstagramHost(it)) {
                                val cookies = CookieManager.getInstance().getCookie("https://www.instagram.com").orEmpty()
                                if (cookies.contains("sessionid=")) {
                                    runCatching {
                                        onSessionCaptured(
                                            SavedInstagramSession(cookies, view.settings.userAgentString.orEmpty())
                                        )
                                    }
                                }
                            }
                            CookieManager.getInstance().flush()
                        }
                    }
                }

                // Inject Instagram session cookies if enabled
                if (useSessions && WebLink.isInstagramHost(url)) {
                    val saved = savedSession
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
                if (savedState == null || restoreState(savedState) == null) {
                    loadUrl(url)
                } else {
                    onPageStateChanged(this.url, title, canGoBack(), canGoForward(), this.url?.startsWith("https://") == true)
                }
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
            webView.clearFocus()
            val history = Bundle()
            if (webView.saveState(history) != null) onSaveState(webView.url, history)
            onWebViewReady(null)
            webViewRef = null
            webView.setOnTouchListener(null)
            webView.setOnScrollChangeListener(null)
            webView.stopLoading()
            webView.onPause()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.removeAllViews()
            webView.destroy()
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Format address display (e.g. x.com/home)
 */
internal fun formatDisplayAddress(url: String): String {
    val normalized = WebLink.normalize(url) ?: url
    val host = WebLink.host(normalized) ?: return normalized
    val withoutScheme = normalized.removePrefix("https://").removePrefix("http://").removePrefix("www.")
    return withoutScheme.take(45)
}

/**
 * Media Extraction Helper from live page
 */
internal suspend fun extractMediaFromPage(
    context: Context,
    webView: WebView,
    sourceUrl: String,
    collector: LivePageMediaCollector,
    onDownloadRequest: (BrowserDownloadRequest) -> Unit,
    isCurrentPage: () -> Boolean
) {
    collector.reset()
    while (true) {
        // Never combine media from a new page with the original source URL.
        if (!isCurrentPage() || WebLink.mediaPageIdentity(webView.url.orEmpty()) != WebLink.mediaPageIdentity(sourceUrl)) return
        val rawValue = withTimeoutOrNull(2_000L) {
            suspendCancellableCoroutine<String?> { continuation ->
                webView.evaluateJavascript(WebExtractionEngine.script(context)) { value ->
                    if (continuation.isActive) continuation.resume(value)
                }
            }
        }
        if (!isCurrentPage() || WebLink.mediaPageIdentity(webView.url.orEmpty()) != WebLink.mediaPageIdentity(sourceUrl)) return
        val payload = WebExtractionEngine.decodeResult(rawValue)
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
                onDownloadRequest(BrowserDownloadRequest(sourceUrl, json.toString()))
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
