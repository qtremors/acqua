package dev.qtremors.acqua.resolver.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.data.session.InstagramSessionStore
import dev.qtremors.acqua.data.session.SavedInstagramSession
import dev.qtremors.acqua.data.session.SavedWebsiteRepository
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class RenderedPageResolverActivity : ComponentActivity() {
    private val dependencies by lazy { application as RenderedPageResolverDependencies }
    private val savedWebsites by lazy { dependencies.resolverSavedWebsites }
    private val instagramSessions by lazy { dependencies.resolverInstagramSessions }
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var finishedWithResult = false
    private val mediaCollector = LivePageMediaCollector(
        maximumAttempts = MAX_EXTRACTION_ATTEMPTS,
        requiredStablePasses = REQUIRED_STABLE_PASSES
    )
    private val extractionScript by lazy { WebExtractionEngine.script(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (dependencies.resolverSettings.screenProtectionEnabled()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        val targetUrl = intent.getStringExtra(EXTRA_URL)?.let(WebLink::normalize)
        if (targetUrl == null) {
            finishWithError(getString(R.string.requested_link_invalid))
            return
        }

        buildContentView()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishWithError(getString(R.string.resolution_cancelled))
            }
        })
        loadWithAvailableSession(targetUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildContentView() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(18, 18, 18))
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                mediaPlaybackRequiresUserGesture = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressBar.progress = newProgress
                    progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
                }
            }
            webViewClient = resolverWebViewClient()
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        root.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            progressBar,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 3.dp)
        )
        setContentView(root)
        Toast.makeText(this, getString(R.string.resolving_media), Toast.LENGTH_SHORT).show()
    }

    private fun loadWithAvailableSession(targetUrl: String) {
        val cookieManager = CookieManager.getInstance().apply { setAcceptCookie(true) }
        val existingCookies = cookieManager.getCookie(targetUrl).orEmpty()
        val savedSession = instagramSessions.load()
            ?.takeIf { WebLink.isInstagramHost(targetUrl) && !existingCookies.contains("sessionid=") }

        if (savedSession == null) {
            webView.loadUrl(targetUrl)
            return
        }

        val cookies = savedSession.cookies.split(';')
            .mapNotNull { raw ->
                val parts = raw.trim().split('=', limit = 2)
                if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) null
                else parts[0] to parts[1]
            }

        if (cookies.isEmpty()) {
            webView.loadUrl(targetUrl)
            return
        }

        var remaining = cookies.size
        cookies.forEach { (name, value) ->
            val httpOnly = if (name == "sessionid") "; HttpOnly" else ""
            val cookie = "$name=$value; Domain=.instagram.com; Path=/; Secure; SameSite=None$httpOnly"
            cookieManager.setCookie(INSTAGRAM_ORIGIN, cookie) {
                runOnUiThread {
                    remaining--
                    if (remaining == 0 && !isFinishing) {
                        cookieManager.flush()
                        webView.loadUrl(targetUrl)
                    }
                }
            }
        }
    }

    private fun resolverWebViewClient() = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            val scheme = request.url.scheme?.lowercase(Locale.ROOT)
            return scheme != "http" && scheme != "https"
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            captureVideoRequest(request)
            return super.shouldInterceptRequest(view, request)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            super.onPageFinished(view, url)
            url?.let(savedWebsites::record)
            if (url != null && WebLink.isInstagramHost(url) && url.contains("/accounts/login")) {
                finishWithExpiredSession(getString(R.string.session_expired))
                return
            }
            mediaCollector.reset()
            scheduleExtraction(250L)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            super.onReceivedError(view, request, error)
            if (request.isForMainFrame) {
                finishWithError(getString(R.string.media_page_failed, error.description))
            }
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            if (request.isForMainFrame && errorResponse.statusCode in listOf(401, 403, 404)) {
                finishWithError(getString(R.string.media_page_http_error, errorResponse.statusCode))
            }
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            finishWithError(getString(R.string.media_page_not_secure))
        }
    }

    private fun captureVideoRequest(request: WebResourceRequest) {
        captureVideoUrl(request.url.toString())
    }

    private fun captureVideoUrl(rawUrl: String) {
        val lowerUrl = rawUrl.lowercase(Locale.ROOT)
        val looksLikeMp4 = lowerUrl.contains(".mp4") ||
            lowerUrl.contains("mime_type=video") ||
            lowerUrl.contains("video%2fmp4")
        if (!looksLikeMp4 || (!lowerUrl.startsWith("https://") && !lowerUrl.startsWith("http://"))) return

        val parsed = rawUrl.toHttpUrlOrNull() ?: return
        val normalized = parsed.newBuilder()
            .removeAllQueryParameters("bytestart")
            .removeAllQueryParameters("byteend")
            .build()
            .toString()
        mediaCollector.captureNetworkVideo(normalized)
    }

    private fun scheduleExtraction(delayMillis: Long = EXTRACTION_INTERVAL_MS) {
        if (finishedWithResult || isFinishing) return
        webView.postDelayed({ extractRenderedMedia() }, delayMillis)
    }

    private fun extractRenderedMedia() {
        if (finishedWithResult || isFinishing) return
        webView.evaluateJavascript(extractionScript) { rawValue ->
            if (finishedWithResult || isFinishing) return@evaluateJavascript
            val payload = WebExtractionEngine.decodeResult(rawValue)
            when (val outcome = mediaCollector.consume(payload, webView.url.orEmpty())) {
                LivePageExtractionOutcome.Continue -> scheduleExtraction()
                is LivePageExtractionOutcome.Complete -> finishWithMedia(outcome.media)
                is LivePageExtractionOutcome.Failed -> when (outcome.reason) {
                    LivePageExtractionFailure.SESSION_EXPIRED ->
                        finishWithExpiredSession(getString(R.string.session_expired))
                    LivePageExtractionFailure.CONTENT_UNAVAILABLE ->
                        finishWithError(getString(R.string.content_unavailable))
                    LivePageExtractionFailure.NO_MEDIA ->
                        finishWithError(getString(R.string.no_media_found))
                }
            }
        }
    }

    private fun finishWithMedia(items: List<ResolvedMedia>) {
        if (items.isEmpty()) {
            finishWithError(getString(R.string.no_media_found))
            return
        }

        refreshStoredSession()
        val json = JSONArray().apply {
            items.forEach { item ->
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
        finishWithResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_MEDIA_JSON, json.toString()))
    }

    private fun refreshStoredSession() {
        val currentPage = webView.url.orEmpty()
        if (!WebLink.isInstagramHost(currentPage)) return
        val cookies = CookieManager.getInstance().getCookie(INSTAGRAM_ORIGIN).orEmpty()
        if (cookies.contains("sessionid")) {
            runCatching {
                instagramSessions.save(
                    SavedInstagramSession(cookies, webView.settings.userAgentString.orEmpty())
                )
            }
        }
    }

    private fun finishWithExpiredSession(message: String) {
        finishWithResult(RESULT_SESSION_EXPIRED, Intent().putExtra(EXTRA_ERROR, message))
    }

    private fun finishWithError(message: String) {
        finishWithResult(RESULT_RESOLUTION_ERROR, Intent().putExtra(EXTRA_ERROR, message))
    }

    private fun finishWithResult(resultCode: Int, data: Intent) {
        if (finishedWithResult) return
        finishedWithResult = true
        setResult(resultCode, data)
        finish()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.webChromeClient = null
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.removeAllViews()
            webView.destroy()
        }
        CookieManager.getInstance().flush()
        super.onDestroy()
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val RESULT_SESSION_EXPIRED = Activity.RESULT_FIRST_USER + 1
        const val RESULT_RESOLUTION_ERROR = Activity.RESULT_FIRST_USER + 2
        const val EXTRA_URL = "authenticated_media_url"
        const val EXTRA_MEDIA_JSON = "authenticated_media_json"
        const val EXTRA_ERROR = "authenticated_media_error"

        private const val INSTAGRAM_ORIGIN = "https://www.instagram.com"
        private const val EXTRACTION_INTERVAL_MS = 500L
        private const val MAX_EXTRACTION_ATTEMPTS = 30
        private const val REQUIRED_STABLE_PASSES = 4

        fun parseMediaResults(data: Intent?): List<ResolvedMedia> {
            val raw = data?.getStringExtra(EXTRA_MEDIA_JSON).orEmpty()
            return parseMediaJson(raw)
        }

        fun parseMediaJson(raw: String): List<ResolvedMedia> {
            if (raw.isBlank()) return emptyList()
            val array = JSONArray(raw)
            return buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val url = item.optString("url")
                    if (!url.startsWith("https://") && !url.startsWith("http://")) continue
                    add(
                        ResolvedMedia(
                            url = url,
                            kind = if (item.optBoolean("isVideo")) MediaKind.VIDEO else MediaKind.IMAGE,
                            thumbnailUrl = item.optString("thumbnailUrl").takeIf {
                                it.startsWith("https://") || it.startsWith("http://")
                            },
                            width = item.optInt("width"),
                            height = item.optInt("height"),
                            username = item.optString("username").takeIf { it.isNotBlank() && it != "null" },
                            referer = item.optString("referer").takeIf { it.startsWith("http") },
                            sourceTimestampMillis = item.optLong("sourceTimestampMillis").takeIf { it > 0L }
                        )
                    )
                }
            }
        }

    }
}
