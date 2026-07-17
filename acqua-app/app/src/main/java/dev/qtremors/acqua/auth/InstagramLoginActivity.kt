package dev.qtremors.acqua.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class InstagramLoginActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var sessionCaptured = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(12, 16, 20))
        }
        ViewCompat.setOnApplyWindowInsetsListener(page) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }
        page.addView(createHeader(), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            64.dp
        ))

        val browserContainer = FrameLayout(this)
        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(12, 16, 20))
            settings.run {
                javaScriptEnabled = true
                domStorageEnabled = true
                javaScriptCanOpenWindowsAutomatically = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = true
                }
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = WebChromeClient()
            webViewClient = createLoginWebViewClient()
        }
        browserContainer.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        progressBar = ProgressBar(this)
        browserContainer.addView(progressBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))

        page.addView(browserContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        setContentView(page)
        webView.loadUrl(LOGIN_URL)
    }

    private fun createHeader(): View {
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8.dp, 0, 16.dp, 0)
            setBackgroundColor(Color.rgb(42, 42, 44))

            addView(ImageButton(context).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
                setBackgroundColor(Color.TRANSPARENT)
                contentDescription = "Close"
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(56.dp, 56.dp))

            addView(TextView(context).apply {
                text = "Instagram Web Login"
                setTextColor(Color.WHITE)
                textSize = 20f
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))

            addView(View(context), LinearLayout.LayoutParams(56.dp, 56.dp))
        }
    }

    private fun createLoginWebViewClient() = object : WebViewClient() {
        override fun onPageCommitVisible(view: WebView, url: String?) {
            super.onPageCommitVisible(view, url)
            progressBar.visibility = View.GONE
        }

        override fun onPageFinished(view: WebView, url: String?) {
            super.onPageFinished(view, url)
            progressBar.visibility = View.GONE
            captureAuthenticatedSession(view)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            super.onReceivedError(view, request, error)
            if (request.isForMainFrame) {
                progressBar.visibility = View.GONE
                Toast.makeText(
                    this@InstagramLoginActivity,
                    "Login page failed to load: ${error.description}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun captureAuthenticatedSession(view: WebView) {
        if (sessionCaptured) return
        val cookies = CookieManager.getInstance().getCookie(INSTAGRAM_ORIGIN) ?: return
        if (!cookies.contains("sessionid")) return

        runCatching {
            SecureSessionStore.save(
                this,
                SavedLoginSession(cookies, view.settings.userAgentString)
            )
        }.onSuccess {
            sessionCaptured = true
            getSharedPreferences("acqua_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("acqua_use_session_cookies", true)
                .apply()
            setResult(Activity.RESULT_OK)
            finish()
        }.onFailure {
            Toast.makeText(
                this,
                "Could not secure the login session on this device.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            CookieManager.getInstance().removeAllCookies {
                CookieManager.getInstance().flush()
            }
        }
        webView.stopLoading()
        webView.removeAllViews()
        webView.destroy()
        super.onDestroy()
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val LOGIN_URL = "https://www.instagram.com/accounts/login/"
        private const val INSTAGRAM_ORIGIN = "https://www.instagram.com"
    }
}
