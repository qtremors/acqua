package dev.qtremors.acqua.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.AcquaApp
import dev.qtremors.acqua.domain.BrowserDestination
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.data.session.InstagramSessionStore
import dev.qtremors.acqua.data.session.SavedInstagramSession
import dev.qtremors.acqua.data.session.SavedWebsiteRepository
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.feature.downloader.DownloadActivity
import dev.qtremors.acqua.resolver.web.LivePageExtractionFailure
import dev.qtremors.acqua.resolver.web.LivePageExtractionOutcome
import dev.qtremors.acqua.resolver.web.LivePageMediaCollector
import dev.qtremors.acqua.resolver.web.WebExtractionEngine
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

class BrowserActivity : ComponentActivity() {
    private val dependencies by lazy { (application as AcquaApp).dependencies }
    private val savedWebsites by lazy { dependencies.savedWebsites }
    private val instagramSessions by lazy { dependencies.instagramSessions }
    private lateinit var webView: WebView
    private lateinit var addressBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var backButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var reloadButton: ImageButton
    private var currentUrl: String? = null
    private var isAddingLogin = false
    private var loginName = ""
    private var pageLoading = false
    private var downloadRequestPending = false
    private val livePageMediaCollector = LivePageMediaCollector()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (dependencies.settings.screenProtectionEnabled()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        isAddingLogin = intent.getBooleanExtra(EXTRA_ADD_LOGIN, false)
        loginName = intent.getStringExtra(EXTRA_LOGIN_NAME).orEmpty()
        val requested = intent.getStringExtra(EXTRA_INITIAL_URL)?.let(WebLink::normalize)
            ?: if (isAddingLogin) null else savedWebsites.lastOrigin()
        requested?.takeIf { isAddingLogin }
            ?.let { savedWebsites.save(loginName, it, null) }

        val root = FrameLayout(this).apply {
            setBackgroundColor(BACKGROUND)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BACKGROUND)
        }
        content.addView(createHeader(), LinearLayout.LayoutParams.MATCH_PARENT, 112.dp)
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(PRIMARY)
        }
        content.addView(progressBar, LinearLayout.LayoutParams.MATCH_PARENT, 3.dp)

        val container = FrameLayout(this)
        webView = WebView(this).apply {
            setBackgroundColor(BACKGROUND)
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
                    pageLoading = newProgress < 100
                    updateNavigationControls()
                    view?.url?.takeIf(::isBrowsablePage)?.let { currentUrl = it }
                }

                override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                    super.onReceivedIcon(view, icon)
                    if (icon != null) view?.url?.let {
                        savedWebsites.updateIcon(it, icon)
                    }
                }
            }
            webViewClient = browserClient()
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }
        container.addView(webView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        content.addView(container, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(content, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else closeBrowser()
            }
        })

        val restored = savedInstanceState?.let(webView::restoreState) != null
        if (restored) {
            currentUrl = savedInstanceState.getString(STATE_CURRENT_URL)
                ?: webView.url?.takeIf(::isBrowsablePage)
            addressBar.setText(currentUrl.orEmpty())
            webView.post(::updateNavigationControls)
        } else if (requested != null) {
            addressBar.setText(requested)
            loadInitialPage(requested)
        } else {
            showStartPage()
        }
    }

    private fun createHeader(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(SURFACE)

        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4.dp, 4.dp, 4.dp, 2.dp)
            addView(iconButton(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.close)) {
                closeBrowser()
            }, LinearLayout.LayoutParams(48.dp, 52.dp))

            addressBar = EditText(context).apply {
                hint = getString(R.string.search_or_enter_website)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.rgb(160, 174, 184))
                setSingleLine(true)
                textSize = 14f
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
                imeOptions = EditorInfo.IME_ACTION_GO
                background = GradientDrawable().apply {
                    setColor(ADDRESS_SURFACE)
                    cornerRadius = 24.dp.toFloat()
                }
                setPadding(16.dp, 0, 12.dp, 0)
                setSelectAllOnFocus(true)
                setOnFocusChangeListener { _, focused ->
                    if (!focused) setText(currentUrl.orEmpty())
                }
                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_GO) {
                        navigateToInput()
                        true
                    } else false
                }
            }
            addView(addressBar, LinearLayout.LayoutParams(0, 48.dp, 1f))
            reloadButton = iconButton(R.drawable.browser_refresh, getString(R.string.refresh)) {
                if (pageLoading) webView.stopLoading()
                else if (currentUrl == null) showStartPage()
                else webView.reload()
            }
            addView(reloadButton, LinearLayout.LayoutParams(48.dp, 52.dp))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 58.dp))

        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER
            backButton = navigationButton(R.drawable.browser_back, getString(R.string.back)) {
                if (webView.canGoBack()) webView.goBack()
            }
            forwardButton = navigationButton(R.drawable.browser_forward, getString(R.string.forward)) {
                if (webView.canGoForward()) webView.goForward()
            }
            addView(backButton, weightedNavigationParams())
            addView(forwardButton, weightedNavigationParams())
            addView(navigationButton(R.drawable.browser_home, getString(R.string.home)) { showStartPage() }, weightedNavigationParams())
            addView(navigationButton(R.drawable.browser_download, getString(R.string.download)) { downloadCurrentPage() }, weightedNavigationParams())
            addView(navigationButton(android.R.drawable.ic_menu_revert, getString(R.string.go_to_acqua)) { finishBrowser() }, weightedNavigationParams())
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 54.dp))
    }

    private fun weightedNavigationParams() = LinearLayout.LayoutParams(0, 52.dp, 1f)

    private fun navigationButton(icon: Int, description: String, action: () -> Unit) =
        iconButton(icon, description, action).apply { setPadding(14.dp, 14.dp, 14.dp, 14.dp) }

    private fun iconButton(icon: Int, description: String, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(Color.WHITE)
        setBackgroundColor(Color.TRANSPARENT)
        contentDescription = description
        setOnClickListener { action() }
    }

    private fun navigateToInput() {
        val destination = BrowserDestination.fromInput(addressBar.text.toString())
        if (destination == null) {
            return
        }
        addressBar.clearFocus()
        loadInitialPage(destination)
    }

    private fun updateNavigationControls() {
        if (!::webView.isInitialized || !::backButton.isInitialized) return
        backButton.isEnabled = webView.canGoBack()
        backButton.alpha = if (backButton.isEnabled) 1f else 0.35f
        forwardButton.isEnabled = webView.canGoForward()
        forwardButton.alpha = if (forwardButton.isEnabled) 1f else 0.35f
        reloadButton.setImageResource(
            if (pageLoading) android.R.drawable.ic_menu_close_clear_cancel else R.drawable.browser_refresh
        )
        reloadButton.contentDescription = getString(if (pageLoading) R.string.stop else R.string.refresh)
    }

    private fun loadInitialPage(url: String) {
        val cookieManager = CookieManager.getInstance()
        val saved = instagramSessions.load()
            ?.takeIf {
                WebLink.isInstagramHost(url) &&
                    !cookieManager.getCookie(INSTAGRAM_ORIGIN).orEmpty().contains("sessionid=")
            }
        if (saved == null) {
            webView.loadUrl(url)
            return
        }

        val cookies = saved.cookies.split(';').mapNotNull { raw ->
            val parts = raw.trim().split('=', limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                parts[0] to parts[1]
            } else null
        }
        if (cookies.isEmpty()) {
            webView.loadUrl(url)
            return
        }

        var remaining = cookies.size
        cookies.forEach { (name, value) ->
            val cookie = "$name=$value; Domain=.instagram.com; Path=/; Secure; SameSite=None"
            cookieManager.setCookie(INSTAGRAM_ORIGIN, cookie) {
                runOnUiThread {
                    remaining--
                    if (remaining == 0 && !isFinishing) {
                        cookieManager.flush()
                        webView.loadUrl(url)
                    }
                }
            }
        }
    }

    private fun browserClient() = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val scheme = request.url.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") return false
            if (request.isForMainFrame) {
                Toast.makeText(this@BrowserActivity, R.string.cannot_open_link, Toast.LENGTH_SHORT).show()
            }
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            if (favicon != null) url?.let {
                savedWebsites.updateIcon(it, favicon)
            }
            pageLoading = true
            updateNavigationControls()
            url?.takeIf { isBrowsablePage(it) }?.let {
                currentUrl = it
                if (!addressBar.hasFocus()) addressBar.setText(it)
            }
        }

        override fun onPageFinished(view: WebView, url: String?) {
            super.onPageFinished(view, url)
            progressBar.visibility = View.GONE
            pageLoading = false
            updateNavigationControls()
            url?.let { finishedUrl ->
                if (isBrowsablePage(finishedUrl)) {
                    currentUrl = finishedUrl
                    if (!addressBar.hasFocus()) addressBar.setText(finishedUrl)
                    savedWebsites.record(finishedUrl)
                    saveKnownPlatformSession(finishedUrl, view.settings.userAgentString.orEmpty())
                    CookieManager.getInstance().flush()
                }
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            super.onReceivedError(view, request, error)
            if (request.isForMainFrame) {
                progressBar.visibility = View.GONE
                pageLoading = false
                updateNavigationControls()
                Toast.makeText(
                    this@BrowserActivity,
                    getString(R.string.page_failed_to_load, error.description),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            Toast.makeText(this@BrowserActivity, R.string.website_not_secure, Toast.LENGTH_LONG).show()
        }
    }

    private fun saveKnownPlatformSession(url: String, userAgent: String) {
        if (!WebLink.isInstagramHost(url)) return
        val cookies = CookieManager.getInstance().getCookie(INSTAGRAM_ORIGIN).orEmpty()
        if (cookies.contains("sessionid=")) {
            runCatching { instagramSessions.save(SavedInstagramSession(cookies, userAgent)) }
        } else {
            instagramSessions.clear()
        }
    }

    private fun isBrowsablePage(url: String): Boolean =
        WebLink.normalize(url) != null && WebLink.host(url) != "acqua.local"

    private fun showStartPage() {
        currentUrl = null
        if (!addressBar.hasFocus()) addressBar.setText("")
        webView.loadDataWithBaseURL(
            "https://acqua.local/",
            """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
            <body style="margin:0;background:#0c1014;color:#dce8ef;font-family:sans-serif;display:grid;place-items:center;height:100vh;text-align:center">
              <main><h2 style="margin:0 0 8px">${getString(R.string.browser_home_title)}</h2><p style="margin:0;color:#9fb2bd">${getString(R.string.browser_home_guidance)}</p></main>
            </body>
            """.trimIndent(),
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun downloadCurrentPage() {
        if (downloadRequestPending) return
        downloadRequestPending = true
        webView.evaluateJavascript("window.location.href") { rawValue ->
            val activeUrl = sequenceOf(
                decodeJavascriptString(rawValue),
                webView.url,
                currentUrl
            ).mapNotNull { it?.let(WebLink::normalize) }
                .firstOrNull { isBrowsablePage(it) }
            if (activeUrl == null) {
                downloadRequestPending = false
                Toast.makeText(this, R.string.open_website_before_download, Toast.LENGTH_SHORT).show()
                return@evaluateJavascript
            }
            currentUrl = activeUrl
            Toast.makeText(this, R.string.resolving_media, Toast.LENGTH_SHORT).show()
            beginCurrentPageExtraction(activeUrl)
        }
    }

    private fun beginCurrentPageExtraction(sourceUrl: String) {
        livePageMediaCollector.reset()
        extractCurrentPageMedia(sourceUrl)
    }

    private fun extractCurrentPageMedia(sourceUrl: String) {
        if (!downloadRequestPending || isFinishing) return
        webView.evaluateJavascript(WebExtractionEngine.script(this)) { rawValue ->
            if (!downloadRequestPending || isFinishing) return@evaluateJavascript
            val payload = WebExtractionEngine.decodeResult(rawValue)
            when (val outcome = livePageMediaCollector.consume(payload, sourceUrl)) {
                LivePageExtractionOutcome.Continue -> continueCurrentPageExtraction(sourceUrl)
                is LivePageExtractionOutcome.Complete -> openDownloadPreview(sourceUrl, outcome.media)
                is LivePageExtractionOutcome.Failed -> failCurrentPageExtraction(
                    when (outcome.reason) {
                        LivePageExtractionFailure.SESSION_EXPIRED -> R.string.session_expired
                        LivePageExtractionFailure.CONTENT_UNAVAILABLE -> R.string.content_unavailable
                        LivePageExtractionFailure.NO_MEDIA -> R.string.no_media_found
                    }
                )
            }
        }
    }

    private fun continueCurrentPageExtraction(sourceUrl: String) {
        webView.postDelayed({ extractCurrentPageMedia(sourceUrl) }, DOWNLOAD_EXTRACTION_INTERVAL_MS)
    }

    private fun openDownloadPreview(sourceUrl: String, items: List<ResolvedMedia>) {
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
        downloadRequestPending = false
        startActivity(
            Intent(this, DownloadActivity::class.java)
                .putExtra(DownloadActivity.EXTRA_URL, sourceUrl)
                .putExtra(DownloadActivity.EXTRA_MEDIA_JSON, json.toString())
        )
    }

    private fun failCurrentPageExtraction(message: Int) {
        downloadRequestPending = false
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun finishBrowser() {
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun closeBrowser() {
        finishBrowser()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CURRENT_URL, currentUrl)
        if (::webView.isInitialized) webView.saveState(outState)
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            CookieManager.getInstance().flush()
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.removeAllViews()
            webView.destroy()
        }
        super.onDestroy()
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private fun decodeJavascriptString(value: String?): String? = runCatching {
        JSONTokener(value.orEmpty()).nextValue() as? String
    }.getOrNull()

    companion object {
        const val EXTRA_INITIAL_URL = "browser_initial_url"
        const val EXTRA_ADD_LOGIN = "browser_add_login"
        const val EXTRA_LOGIN_NAME = "browser_login_name"
        private const val STATE_CURRENT_URL = "browser_current_page_url"
        private const val INSTAGRAM_ORIGIN = "https://www.instagram.com"
        private const val DOWNLOAD_EXTRACTION_INTERVAL_MS = 500L
        private val BACKGROUND = Color.rgb(12, 16, 20)
        private val SURFACE = Color.rgb(36, 43, 48)
        private val ADDRESS_SURFACE = Color.rgb(19, 25, 30)
        private val PRIMARY = Color.rgb(129, 216, 255)
    }
}
