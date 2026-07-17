package dev.qtremors.acqua.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import dev.qtremors.acqua.R
import dev.qtremors.acqua.downloader.MediaResult
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

class AuthenticatedMediaResolverActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var finishedWithResult = false
    private var extractionAttempts = 0
    private var stablePasses = 0
    private var lastCollectedCount = 0
    private var sawCarouselAdvance = false
    private var pageExpectsVideo = false
    private var latestVideoPoster: String? = null
    private var latestVideoWidth = 0
    private var latestVideoHeight = 0
    private val capturedVideoUrls = ConcurrentLinkedDeque<String>()
    private val collectedMedia = linkedMapOf<String, MediaResult>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetUrl = intent.getStringExtra(EXTRA_URL)
        if (!isAllowedInstagramUrl(targetUrl)) {
            finishWithError("The requested media link is invalid.")
            return
        }

        val session = SecureSessionStore.load(this)
        if (session == null || !session.cookies.contains("sessionid")) {
            finishWithExpiredSession("No saved login session is available.")
            return
        }

        buildContentView(session.userAgent)
        restoreSessionAndLoad(session, targetUrl!!)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildContentView(userAgent: String) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(18, 18, 18))
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp, 0, 8.dp, 0)
            setBackgroundColor(Color.rgb(42, 42, 42))
        }
        toolbar.addView(
            TextView(this).apply {
                text = getString(R.string.resolving_media)
                setTextColor(Color.WHITE)
                textSize = 18f
            },
            LinearLayout.LayoutParams(0, 56.dp, 1f)
        )
        toolbar.addView(
            ImageButton(this).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                contentDescription = getString(R.string.cancel)
                setColorFilter(Color.WHITE)
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener { finishWithError("Media resolution was cancelled.") }
            },
            LinearLayout.LayoutParams(56.dp, 56.dp)
        )
        root.addView(toolbar, LinearLayout.LayoutParams.MATCH_PARENT, 56.dp)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
        }
        root.addView(progressBar, LinearLayout.LayoutParams.MATCH_PARENT, 3.dp)

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
                if (userAgent.isNotBlank()) userAgentString = userAgent
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
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        root.addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun restoreSessionAndLoad(session: SavedLoginSession, targetUrl: String) {
        val cookieManager = CookieManager.getInstance().apply { setAcceptCookie(true) }
        val cookies = session.cookies.split(';')
            .mapNotNull { raw ->
                val parts = raw.trim().split('=', limit = 2)
                if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) null
                else parts[0] to parts[1]
            }

        if (cookies.none { it.first == "sessionid" }) {
            finishWithExpiredSession("The saved login session is incomplete.")
            return
        }

        cookieManager.removeAllCookies {
            runOnUiThread {
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
        }
    }

    private fun resolverWebViewClient() = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            return !isAllowedInstagramUri(request.url)
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
            if (url?.contains("/accounts/login") == true) {
                finishWithExpiredSession("The saved login session has expired. Please sign in again.")
                return
            }
            extractionAttempts = 0
            stablePasses = 0
            lastCollectedCount = 0
            scheduleExtraction(250L)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            super.onReceivedError(view, request, error)
            if (request.isForMainFrame) {
                finishWithError("The media page failed to load: ${error.description}")
            }
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            if (request.isForMainFrame && errorResponse.statusCode in listOf(401, 403, 404)) {
                finishWithError("The media page is unavailable (HTTP ${errorResponse.statusCode}).")
            }
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            finishWithError("The media page could not be verified securely.")
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
        if (!looksLikeMp4 || !lowerUrl.startsWith("https://")) return

        val parsed = rawUrl.toHttpUrlOrNull() ?: return
        val normalized = parsed.newBuilder()
            .removeAllQueryParameters("bytestart")
            .removeAllQueryParameters("byteend")
            .build()
            .toString()
        capturedVideoUrls.remove(normalized)
        capturedVideoUrls.addLast(normalized)
        while (capturedVideoUrls.size > MAX_CAPTURED_VIDEO_URLS) {
            capturedVideoUrls.pollFirst()
        }
    }

    private fun scheduleExtraction(delayMillis: Long = EXTRACTION_INTERVAL_MS) {
        if (finishedWithResult || isFinishing) return
        webView.postDelayed({ extractRenderedMedia() }, delayMillis)
    }

    private fun extractRenderedMedia() {
        if (finishedWithResult || isFinishing) return
        extractionAttempts++
        webView.evaluateJavascript(EXTRACTION_SCRIPT) { rawValue ->
            if (finishedWithResult || isFinishing) return@evaluateJavascript

            val payload = decodeJavascriptResult(rawValue)
            if (payload == null) {
                continueOrFinish("The rendered page did not return readable media data.")
                return@evaluateJavascript
            }

            if (payload.optBoolean("loginPage")) {
                finishWithExpiredSession("The saved login session has expired. Please sign in again.")
                return@evaluateJavascript
            }

            val username = payload.optString("username").takeIf { it.isNotBlank() }
            pageExpectsVideo = pageExpectsVideo || payload.optBoolean("expectsVideo") ||
                payload.optInt("videoElementCount") > 0
            latestVideoPoster = payload.optString("videoPoster")
                .takeIf { it.startsWith("https://") }
                ?: latestVideoPoster
            latestVideoWidth = payload.optInt("videoWidth").takeIf { it > 0 } ?: latestVideoWidth
            latestVideoHeight = payload.optInt("videoHeight").takeIf { it > 0 } ?: latestVideoHeight
            if (payload.optInt("videoElementCount") > 0) {
                val networkUrls = payload.optJSONArray("networkVideoUrls") ?: JSONArray()
                for (index in 0 until networkUrls.length()) {
                    captureVideoUrl(networkUrls.optString(index))
                }
            }
            val items = payload.optJSONArray("items") ?: JSONArray()
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val url = item.optString("url")
                if (!url.startsWith("https://")) continue
                collectedMedia[url] = MediaResult(
                    url = url,
                    isVideo = item.optBoolean("isVideo"),
                    thumbnailUrl = item.optString("thumbnail").takeIf { it.startsWith("https://") },
                    width = item.optInt("width").coerceAtLeast(0),
                    height = item.optInt("height").coerceAtLeast(0),
                    username = username
                )
            }

            if (pageExpectsVideo && collectedMedia.values.none { it.isVideo }) {
                capturedVideoUrls.take(MAX_CAPTURED_VIDEO_URLS).forEach { url ->
                    collectedMedia[url] = MediaResult(
                        url = url,
                        isVideo = true,
                        thumbnailUrl = latestVideoPoster,
                        width = latestVideoWidth,
                        height = latestVideoHeight,
                        username = username
                    )
                }
            }

            val clickedNext = payload.optBoolean("clickedNext")
            sawCarouselAdvance = sawCarouselAdvance || clickedNext
            if (collectedMedia.size == lastCollectedCount && !clickedNext) stablePasses++ else stablePasses = 0
            lastCollectedCount = collectedMedia.size
            val hasVideo = collectedMedia.values.any { it.isVideo }

            when {
                collectedMedia.isNotEmpty() && (!pageExpectsVideo || hasVideo) &&
                    !clickedNext && stablePasses >= REQUIRED_STABLE_PASSES -> {
                    finishWithMedia(normalizeCollectedMedia(collectedMedia.values.toList()))
                }
                payload.optBoolean("errorPage") && collectedMedia.isEmpty() -> {
                    finishWithError("This content is unavailable to the signed-in account.")
                }
                extractionAttempts >= MAX_EXTRACTION_ATTEMPTS -> {
                    if (collectedMedia.isNotEmpty() && (!pageExpectsVideo || hasVideo)) {
                        finishWithMedia(normalizeCollectedMedia(collectedMedia.values.toList()))
                    } else if (pageExpectsVideo) {
                        finishWithError("The page showed a video but did not expose a complete downloadable video URL.")
                    } else {
                        finishWithError("No downloadable photos or videos were found on the rendered page.")
                    }
                }
                else -> scheduleExtraction()
            }
        }
    }

    private fun continueOrFinish(message: String) {
        if (extractionAttempts >= MAX_EXTRACTION_ATTEMPTS) finishWithError(message) else scheduleExtraction()
    }

    private fun normalizeCollectedMedia(items: List<MediaResult>): List<MediaResult> {
        val videos = items.filter { it.isVideo }
        if (videos.isNotEmpty() && !sawCarouselAdvance) {
            val capturedPoster = videos.firstNotNullOfOrNull { it.thumbnailUrl }
                ?: items.firstOrNull { !it.isVideo }?.url
                ?: latestVideoPoster
            return videos.map { video ->
                if (video.thumbnailUrl != null || capturedPoster == null) video
                else video.copy(thumbnailUrl = capturedPoster)
            }
        }

        val posterUrls = items.asSequence()
            .filter { it.isVideo }
            .mapNotNull { it.thumbnailUrl }
            .toSet()
        return items.filterNot { !it.isVideo && it.url in posterUrls }
    }

    private fun finishWithMedia(items: List<MediaResult>) {
        if (items.isEmpty()) {
            finishWithError("No downloadable photos or videos were found on the rendered page.")
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
                )
            }
        }
        finishWithResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_MEDIA_JSON, json.toString()))
    }

    private fun refreshStoredSession() {
        val cookies = CookieManager.getInstance().getCookie(INSTAGRAM_ORIGIN).orEmpty()
        if (cookies.contains("sessionid")) {
            runCatching {
                SecureSessionStore.save(
                    this,
                    SavedLoginSession(cookies, webView.settings.userAgentString.orEmpty())
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
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
        }
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
        private const val MAX_CAPTURED_VIDEO_URLS = 12

        fun parseMediaResults(data: Intent?): List<MediaResult> {
            val raw = data?.getStringExtra(EXTRA_MEDIA_JSON).orEmpty()
            if (raw.isBlank()) return emptyList()
            val array = JSONArray(raw)
            return buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val url = item.optString("url")
                    if (!url.startsWith("https://")) continue
                    add(
                        MediaResult(
                            url = url,
                            isVideo = item.optBoolean("isVideo"),
                            thumbnailUrl = item.optString("thumbnailUrl").takeIf { it.startsWith("https://") },
                            width = item.optInt("width"),
                            height = item.optInt("height"),
                            username = item.optString("username").takeIf { it.isNotBlank() && it != "null" }
                        )
                    )
                }
            }
        }

        private fun isAllowedInstagramUrl(url: String?): Boolean = runCatching {
            val uri = Uri.parse(url)
            uri.scheme == "https" && isAllowedInstagramUri(uri)
        }.getOrDefault(false)

        private fun isAllowedInstagramUri(uri: Uri): Boolean {
            val host = uri.host?.lowercase().orEmpty()
            return host == "instagram.com" || host.endsWith(".instagram.com") ||
                host == "instagr.am" || host.endsWith(".instagr.am")
        }

        private fun decodeJavascriptResult(value: String?): JSONObject? = runCatching {
            val decoded = JSONTokener(value.orEmpty()).nextValue() as? String ?: return@runCatching null
            JSONObject(decoded)
        }.getOrNull()

        private val EXTRACTION_SCRIPT = """
            (function() {
              const path = location.pathname || '';
              const singleVideoRoute = path.indexOf('/reel/') === 0 || path.indexOf('/tv/') === 0;
              const result = {
                items: [],
                clickedNext: false,
                loginPage: false,
                errorPage: false,
                username: '',
                expectsVideo: singleVideoRoute,
                videoElementCount: 0,
                videoPoster: '',
                videoWidth: 0,
                videoHeight: 0,
                networkVideoUrls: []
              };
              result.loginPage = location.pathname.indexOf('/accounts/login') === 0;
              const html = document.documentElement ? document.documentElement.innerHTML : '';
              result.errorPage = html.indexOf('PolarisErrorRoot') >= 0 || html.indexOf('httpErrorPage') >= 0;

              const article = document.querySelector('main article') || document.querySelector('article');
              const scope = article || document.querySelector('main') || document.body;
              if (!scope) return JSON.stringify(result);

              const profileLink = scope.querySelector('header a[href^="/"]');
              if (profileLink) {
                const segment = profileLink.getAttribute('href').split('/').filter(Boolean)[0] || '';
                if (segment && !['p', 'reel', 'tv', 'stories', 'explore'].includes(segment)) result.username = segment;
              }

              const seen = new Set();
              const add = function(url, isVideo, thumbnail, width, height) {
                if (!url || url.indexOf('https://') !== 0 || url.indexOf('blob:') === 0 || seen.has(url)) return;
                seen.add(url);
                result.items.push({
                  url: url,
                  isVideo: !!isVideo,
                  thumbnail: thumbnail || '',
                  width: Number(width) || 0,
                  height: Number(height) || 0
                });
              };

              const videos = Array.from(scope.querySelectorAll('video'));
              result.videoElementCount = videos.length;
              const visibleVideo = videos.find(function(video) {
                const rect = video.getBoundingClientRect();
                return rect.width > 160 && rect.height > 160;
              }) || videos[0];
              if (visibleVideo) {
                result.videoPoster = visibleVideo.poster || '';
                result.videoWidth = visibleVideo.videoWidth || visibleVideo.clientWidth || 0;
                result.videoHeight = visibleVideo.videoHeight || visibleVideo.clientHeight || 0;
              }
              const hasDirectVideo = videos.some(function(video) {
                const source = video.currentSrc || video.src || (video.querySelector('source') || {}).src || '';
                return source.indexOf('https://') === 0;
              });
              if (videos.length > 0 && !hasDirectVideo && window.performance) {
                result.networkVideoUrls = Array.from(performance.getEntriesByType('resource') || [])
                  .map(function(entry) { return entry.name || ''; })
                  .filter(function(url) {
                    const lower = url.toLowerCase();
                    return lower.indexOf('https://') === 0 &&
                      (lower.indexOf('.mp4') >= 0 || lower.indexOf('mime_type=video') >= 0 || lower.indexOf('video%2fmp4') >= 0);
                  })
                  .slice(-12);
              }
              videos.forEach(function(video) {
                const source = video.currentSrc || video.src || (video.querySelector('source') || {}).src || '';
                add(source, true, video.poster || '', video.videoWidth || video.clientWidth, video.videoHeight || video.clientHeight);
              });

              const bestImageSource = function(image) {
                let best = image.currentSrc || image.src || '';
                let bestWidth = image.naturalWidth || 0;
                (image.srcset || '').split(',').forEach(function(candidate) {
                  const parts = candidate.trim().split(/\s+/);
                  const width = parts.length > 1 ? parseInt(parts[1], 10) || 0 : 0;
                  if (parts[0] && width >= bestWidth) {
                    best = parts[0];
                    bestWidth = width;
                  }
                });
                return best;
              };

              Array.from(scope.querySelectorAll('img')).forEach(function(image) {
                const rect = image.getBoundingClientRect();
                if (rect.width < 160 || rect.height < 160 || image.naturalWidth < 300 || image.naturalHeight < 200) return;
                const overlapsVideo = videos.some(function(video) {
                  const vr = video.getBoundingClientRect();
                  const overlapWidth = Math.max(0, Math.min(rect.right, vr.right) - Math.max(rect.left, vr.left));
                  const overlapHeight = Math.max(0, Math.min(rect.bottom, vr.bottom) - Math.max(rect.top, vr.top));
                  return overlapWidth * overlapHeight > rect.width * rect.height * 0.5;
                });
                if (!overlapsVideo) add(bestImageSource(image), false, '', image.naturalWidth, image.naturalHeight);
              });

              if (result.items.length === 0) {
                const metaVideo = document.querySelector('meta[property="og:video:secure_url"], meta[property="og:video"]');
                const metaImage = document.querySelector('meta[property="og:image"]');
                if (metaVideo) add(metaVideo.content, true, metaImage ? metaImage.content : '', 0, 0);
                else if (metaImage) add(metaImage.content, false, '', 0, 0);
              }

              const isStory = path.indexOf('/stories/') === 0;
              if (!isStory && !singleVideoRoute && article) {
                const nextButton = Array.from(article.querySelectorAll('button')).find(function(button) {
                  const labelled = button.matches('[aria-label]') ? button : button.querySelector('[aria-label]');
                  const label = ((labelled && labelled.getAttribute('aria-label')) || '').toLowerCase();
                  return label === 'next' || label.indexOf('next') >= 0;
                });
                if (nextButton && !nextButton.disabled) {
                  nextButton.click();
                  result.clickedNext = true;
                }
              }

              return JSON.stringify(result);
            })();
        """.trimIndent()
    }
}
