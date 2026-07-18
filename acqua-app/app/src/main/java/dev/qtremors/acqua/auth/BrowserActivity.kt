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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.qtremors.acqua.R
import org.json.JSONTokener
import kotlin.math.hypot

class BrowserActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var addressBar: EditText
    private lateinit var progressBar: ProgressBar
    private var currentUrl: String? = null
    private var isAddingLogin = false
    private var loginName = ""
    private var addressHeader: View? = null
    private var downloadRequestPending = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isAddingLogin = intent.getBooleanExtra(EXTRA_ADD_LOGIN, false)
        loginName = intent.getStringExtra(EXTRA_LOGIN_NAME).orEmpty()
        val requested = intent.getStringExtra(EXTRA_INITIAL_URL)?.let(WebLink::normalize)
            ?: if (isAddingLogin) null else BrowserSessionRegistry.lastOrigin(this)
        requested?.takeIf { isAddingLogin }
            ?.let { BrowserSessionRegistry.saveWebsiteLogin(this, loginName, it, null) }

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
        if (requested == null) {
            addressHeader = createHeader()
            content.addView(addressHeader, LinearLayout.LayoutParams.MATCH_PARENT, 64.dp)
        }
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
                    view?.url?.takeIf(::isBrowsablePage)?.let { currentUrl = it }
                }

                override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                    super.onReceivedIcon(view, icon)
                    if (icon != null) view?.url?.let {
                        BrowserSessionRegistry.updateWebsiteIcon(this@BrowserActivity, it, icon)
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
        root.addView(
            createFloatingControls(root),
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.START or Gravity.TOP
            }
        )
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else closeBrowser()
            }
        })

        if (requested != null) {
            if (::addressBar.isInitialized) addressBar.setText(requested)
            loadInitialPage(requested)
        } else {
            showStartPage()
        }
    }

    private fun createHeader(): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(6.dp, 0, 8.dp, 0)
        setBackgroundColor(SURFACE)

        addView(iconButton(android.R.drawable.ic_menu_close_clear_cancel, "Close") {
            closeBrowser()
        }, LinearLayout.LayoutParams(52.dp, 52.dp))

        addressBar = EditText(context).apply {
            hint = "Enter a website"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(160, 174, 184))
            setSingleLine(true)
            textSize = 14f
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            backgroundTintList = ColorStateList.valueOf(Color.rgb(105, 124, 136))
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    navigateToInput()
                    true
                } else false
            }
        }
        addView(addressBar, LinearLayout.LayoutParams(0, 52.dp, 1f))

        addView(TextView(context).apply {
            text = "Go"
            setTextColor(PRIMARY)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(10.dp, 0, 10.dp, 0)
            setOnClickListener { navigateToInput() }
        }, LinearLayout.LayoutParams(52.dp, 52.dp))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingControls(parent: FrameLayout): View {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(6.dp, 6.dp, 6.dp, 6.dp)
            background = roundedBackground(SURFACE, 18.dp.toFloat())
            elevation = 10.dp.toFloat()
            visibility = View.GONE
        }
        lateinit var ball: ImageButton

        fun moveWithinBounds(targetX: Float, targetY: Float) {
            val minX = parent.paddingLeft.toFloat()
            val minY = parent.paddingTop.toFloat()
            val maxX = (parent.width - parent.paddingRight - wrapper.width)
                .coerceAtLeast(parent.paddingLeft).toFloat()
            val maxY = (parent.height - parent.paddingBottom - wrapper.height)
                .coerceAtLeast(parent.paddingTop).toFloat()
            wrapper.x = targetX.coerceIn(minX, maxX)
            wrapper.y = targetY.coerceIn(minY, maxY)
        }

        fun saveBallPosition() {
            if (parent.width <= 0 || parent.height <= 0 || ball.width <= 0) return
            val centerX = wrapper.x + ball.left + ball.width / 2f
            val centerY = wrapper.y + ball.top + ball.height / 2f
            getSharedPreferences(BROWSER_UI_PREFS, MODE_PRIVATE).edit()
                .putFloat(KEY_BUBBLE_X, centerX / parent.width)
                .putFloat(KEY_BUBBLE_Y, centerY / parent.height)
                .apply()
        }

        fun setMenuVisible(visible: Boolean) {
            val centerX = wrapper.x + ball.left + ball.width / 2f
            val centerY = wrapper.y + ball.top + ball.height / 2f
            menu.visibility = if (visible) View.VISIBLE else View.GONE
            wrapper.post {
                moveWithinBounds(
                    centerX - ball.left - ball.width / 2f,
                    centerY - ball.top - ball.height / 2f
                )
            }
        }

        fun menuAction(icon: Int, label: String, action: () -> Unit): View =
            LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12.dp, 0, 14.dp, 0)
                background = roundedBackground(Color.TRANSPARENT, 12.dp.toFloat())
                isClickable = true
                isFocusable = true
                contentDescription = label
                addView(ImageView(context).apply {
                    setImageResource(icon)
                    imageTintList = ColorStateList.valueOf(Color.WHITE)
                }, LinearLayout.LayoutParams(24.dp, 24.dp))
                addView(TextView(context).apply {
                    text = label
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(12.dp, 0, 0, 0)
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 48.dp).apply {
                    gravity = Gravity.CENTER_VERTICAL
                })
                setOnClickListener {
                    setMenuVisible(false)
                    action()
                }
            }

        menu.addView(menuAction(android.R.drawable.ic_popup_sync, "Refresh") {
            if (currentUrl == null) showStartPage() else webView.reload()
        }, LinearLayout.LayoutParams(156.dp, 48.dp))
        menu.addView(menuAction(android.R.drawable.stat_sys_download_done, "Download") {
            downloadCurrentPage()
        }, LinearLayout.LayoutParams(156.dp, 48.dp))
        menu.addView(menuAction(android.R.drawable.ic_menu_revert, "Go to Acqua") {
            finishBrowser()
        }, LinearLayout.LayoutParams(156.dp, 48.dp))
        wrapper.addView(menu, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        ball = ImageButton(this).apply {
            setImageResource(R.mipmap.ic_launcher_round)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedBackground(PRIMARY, 30.dp.toFloat(), GradientDrawable.OVAL)
            contentDescription = "Acqua browser controls"
            elevation = 12.dp.toFloat()
            setPadding(3.dp, 3.dp, 3.dp, 3.dp)
            val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
            var downRawX = 0f
            var downRawY = 0f
            var startX = 0f
            var startY = 0f
            var dragging = false
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        startX = wrapper.x
                        startY = wrapper.y
                        dragging = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - downRawX
                        val deltaY = event.rawY - downRawY
                        if (!dragging && hypot(deltaX.toDouble(), deltaY.toDouble()) >= touchSlop.toDouble()) {
                            dragging = true
                        }
                        if (dragging) moveWithinBounds(startX + deltaX, startY + deltaY)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (dragging) {
                            saveBallPosition()
                        } else {
                            performClick()
                            setMenuVisible(menu.visibility != View.VISIBLE)
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> true
                    else -> false
                }
            }
        }
        wrapper.addView(ball, LinearLayout.LayoutParams(58.dp, 58.dp).apply {
            gravity = Gravity.END
            topMargin = 8.dp
        })
        parent.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            wrapper.post { moveWithinBounds(wrapper.x, wrapper.y) }
        }
        wrapper.post {
            val preferences = getSharedPreferences(BROWSER_UI_PREFS, MODE_PRIVATE)
            val centerX = preferences.getFloat(KEY_BUBBLE_X, 0.9f) * parent.width
            val centerY = preferences.getFloat(KEY_BUBBLE_Y, 0.86f) * parent.height
            moveWithinBounds(
                centerX - ball.left - ball.width / 2f,
                centerY - ball.top - ball.height / 2f
            )
        }
        return wrapper
    }

    private fun roundedBackground(
        color: Int,
        radius: Float,
        backgroundShape: Int = GradientDrawable.RECTANGLE
    ) = GradientDrawable().apply {
        shape = backgroundShape
        setColor(color)
        cornerRadius = radius
    }

    private fun iconButton(icon: Int, description: String, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(Color.WHITE)
        setBackgroundColor(Color.TRANSPARENT)
        contentDescription = description
        setOnClickListener { action() }
    }

    private fun navigateToInput() {
        val normalized = WebLink.normalize(addressBar.text.toString())
        if (normalized == null) {
            Toast.makeText(this, "Enter a valid HTTP or HTTPS website.", Toast.LENGTH_SHORT).show()
            return
        }
        loadInitialPage(normalized)
    }

    private fun loadInitialPage(url: String) {
        val cookieManager = CookieManager.getInstance()
        val saved = SecureSessionStore.load(this)
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
                Toast.makeText(this@BrowserActivity, "This link cannot be opened in Acqua.", Toast.LENGTH_SHORT).show()
            }
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            if (favicon != null) url?.let {
                BrowserSessionRegistry.updateWebsiteIcon(this@BrowserActivity, it, favicon)
            }
            url?.takeIf { isBrowsablePage(it) }?.let {
                currentUrl = it
                if (::addressBar.isInitialized) addressBar.setText(it)
                addressHeader?.visibility = View.GONE
            }
        }

        override fun onPageFinished(view: WebView, url: String?) {
            super.onPageFinished(view, url)
            progressBar.visibility = View.GONE
            url?.let { finishedUrl ->
                if (isBrowsablePage(finishedUrl)) {
                    currentUrl = finishedUrl
                    if (::addressBar.isInitialized) addressBar.setText(finishedUrl)
                    BrowserSessionRegistry.record(this@BrowserActivity, finishedUrl)
                    saveKnownPlatformSession(finishedUrl, view.settings.userAgentString.orEmpty())
                    CookieManager.getInstance().flush()
                }
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            super.onReceivedError(view, request, error)
            if (request.isForMainFrame) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@BrowserActivity, "Page failed to load: ${error.description}", Toast.LENGTH_LONG).show()
            }
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            Toast.makeText(this@BrowserActivity, "The website could not be verified securely.", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveKnownPlatformSession(url: String, userAgent: String) {
        if (!WebLink.isInstagramHost(url)) return
        val cookies = CookieManager.getInstance().getCookie(INSTAGRAM_ORIGIN).orEmpty()
        if (cookies.contains("sessionid=")) {
            runCatching { SecureSessionStore.save(this, SavedLoginSession(cookies, userAgent)) }
        } else {
            SecureSessionStore.clear(this)
        }
    }

    private fun isBrowsablePage(url: String): Boolean =
        WebLink.normalize(url) != null && WebLink.host(url) != "acqua.local"

    private fun showStartPage() {
        currentUrl = null
        if (::addressBar.isInitialized) addressBar.setText("")
        webView.loadDataWithBaseURL(
            "https://acqua.local/",
            """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
            <body style="margin:0;background:#0c1014;color:#dce8ef;font-family:sans-serif;display:grid;place-items:center;height:100vh;text-align:center">
              <main><h2 style="margin:0 0 8px">Acqua Browser</h2><p style="margin:0;color:#9fb2bd">Enter a website above to browse or sign in.</p></main>
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
            downloadRequestPending = false
            val activeUrl = sequenceOf(
                decodeJavascriptString(rawValue),
                webView.url,
                currentUrl
            ).mapNotNull { it?.let(WebLink::normalize) }
                .firstOrNull { isBrowsablePage(it) }
            if (activeUrl == null) {
                Toast.makeText(this, "Open a website before downloading.", Toast.LENGTH_SHORT).show()
                return@evaluateJavascript
            }
            currentUrl = activeUrl
            finishBrowser(downloadCurrentPage = true, resultUrl = activeUrl)
        }
    }

    private fun finishBrowser(
        downloadCurrentPage: Boolean = false,
        resultUrl: String? = currentUrl
    ) {
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(EXTRA_CURRENT_URL, resultUrl)
                .putExtra(EXTRA_DOWNLOAD_CURRENT_PAGE, downloadCurrentPage)
        )
        finish()
    }

    private fun closeBrowser() {
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_CURRENT_URL, currentUrl))
        finish()
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
        const val EXTRA_CURRENT_URL = "browser_current_url"
        const val EXTRA_ADD_LOGIN = "browser_add_login"
        const val EXTRA_LOGIN_NAME = "browser_login_name"
        const val EXTRA_DOWNLOAD_CURRENT_PAGE = "browser_download_current_page"
        private const val BROWSER_UI_PREFS = "acqua_browser_ui"
        private const val KEY_BUBBLE_X = "bubble_x"
        private const val KEY_BUBBLE_Y = "bubble_y"
        private const val INSTAGRAM_ORIGIN = "https://www.instagram.com"
        private val BACKGROUND = Color.rgb(12, 16, 20)
        private val SURFACE = Color.rgb(36, 43, 48)
        private val PRIMARY = Color.rgb(129, 216, 255)
    }
}
