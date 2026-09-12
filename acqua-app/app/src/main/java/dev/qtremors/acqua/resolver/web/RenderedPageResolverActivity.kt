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
import dev.qtremors.acqua.R
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
import org.json.JSONTokener
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

class RenderedPageResolverActivity : ComponentActivity() {
    private val savedWebsites by lazy { SavedWebsiteRepository(applicationContext) }
    private val instagramSessions by lazy { InstagramSessionStore(applicationContext) }
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
    private val collectedMedia = linkedMapOf<String, ResolvedMedia>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AppSettingsRepository(this).screenProtectionEnabled()) {
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
                continueOrFinish(getString(R.string.rendered_data_unreadable))
                return@evaluateJavascript
            }

            if (payload.optBoolean("loginPage")) {
                finishWithExpiredSession(getString(R.string.session_expired))
                return@evaluateJavascript
            }

            val username = payload.optString("username").takeIf { it.isNotBlank() }
            val sourceTimestampMillis = payload.optLong("sourceTimestampMillis").takeIf { it > 0L }
            pageExpectsVideo = pageExpectsVideo || payload.optBoolean("expectsVideo") ||
                payload.optInt("videoElementCount") > 0
            latestVideoPoster = payload.optString("videoPoster")
                .takeIf { it.startsWith("https://") || it.startsWith("http://") }
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
                if (!url.startsWith("https://") && !url.startsWith("http://")) continue
                collectedMedia[url] = ResolvedMedia(
                    url = url,
                    kind = if (item.optBoolean("isVideo")) MediaKind.VIDEO else MediaKind.IMAGE,
                    thumbnailUrl = item.optString("thumbnail").takeIf {
                        it.startsWith("https://") || it.startsWith("http://")
                    },
                    width = item.optInt("width").coerceAtLeast(0),
                    height = item.optInt("height").coerceAtLeast(0),
                    username = username,
                    referer = webView.url,
                    sourceTimestampMillis = sourceTimestampMillis
                )
            }

            if (pageExpectsVideo && collectedMedia.values.none { it.isVideo }) {
                capturedVideoUrls.take(MAX_CAPTURED_VIDEO_URLS).forEach { url ->
                    collectedMedia[url] = ResolvedMedia(
                        url = url,
                        kind = MediaKind.VIDEO,
                        thumbnailUrl = latestVideoPoster,
                        width = latestVideoWidth,
                        height = latestVideoHeight,
                        username = username,
                        referer = webView.url,
                        sourceTimestampMillis = sourceTimestampMillis
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
                    finishWithError(getString(R.string.content_unavailable))
                }
                extractionAttempts >= MAX_EXTRACTION_ATTEMPTS -> {
                    if (collectedMedia.isNotEmpty() && (!pageExpectsVideo || hasVideo)) {
                        finishWithMedia(normalizeCollectedMedia(collectedMedia.values.toList()))
                    } else if (pageExpectsVideo) {
                        finishWithError(getString(R.string.video_url_missing))
                    } else {
                        finishWithError(getString(R.string.no_media_found))
                    }
                }
                else -> scheduleExtraction()
            }
        }
    }

    private fun continueOrFinish(message: String) {
        if (extractionAttempts >= MAX_EXTRACTION_ATTEMPTS) finishWithError(message) else scheduleExtraction()
    }

    private fun normalizeCollectedMedia(items: List<ResolvedMedia>): List<ResolvedMedia> {
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
        private const val MAX_CAPTURED_VIDEO_URLS = 12

        fun parseMediaResults(data: Intent?): List<ResolvedMedia> {
            val raw = data?.getStringExtra(EXTRA_MEDIA_JSON).orEmpty()
            return parseMediaJson(raw)
        }

        internal fun parseMediaJson(raw: String): List<ResolvedMedia> {
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

        internal fun decodeJavascriptResult(value: String?): JSONObject? = runCatching {
            val decoded = JSONTokener(value.orEmpty()).nextValue() as? String ?: return@runCatching null
            JSONObject(decoded)
        }.getOrNull()

        internal val EXTRACTION_SCRIPT = """
            (function() {
              const path = location.pathname || '';
              const host = (location.hostname || '').toLowerCase();
              const isInstagram = host === 'instagram.com' || host.endsWith('.instagram.com') || host === 'instagr.am' || host.endsWith('.instagr.am');
              const singleVideoRoute = isInstagram && (path.indexOf('/reel/') === 0 || path.indexOf('/tv/') === 0);
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
                sourceTimestampMillis: 0,
                networkVideoUrls: []
              };
              result.loginPage = isInstagram && location.pathname.indexOf('/accounts/login') === 0;
              const html = document.documentElement ? document.documentElement.innerHTML : '';
              result.errorPage = html.indexOf('PolarisErrorRoot') >= 0 || html.indexOf('httpErrorPage') >= 0;

              const article = document.querySelector('main article') || document.querySelector('article');
              const scope = article || document.querySelector('main') || document.body;
              if (!scope) return JSON.stringify(result);

              const profileLink = isInstagram ? scope.querySelector('header a[href^="/"]') : null;
              if (profileLink) {
                const segment = profileLink.getAttribute('href').split('/').filter(Boolean)[0] || '';
                if (segment && !['p', 'reel', 'tv', 'stories', 'explore'].includes(segment)) result.username = segment;
              }
              const publishedTime = scope.querySelector('time[datetime]');
              if (publishedTime) {
                const parsedTime = Date.parse(publishedTime.getAttribute('datetime') || '');
                if (Number.isFinite(parsedTime) && parsedTime > 0) result.sourceTimestampMillis = parsedTime;
              }

              const seen = new Set();
              const add = function(url, isVideo, thumbnail, width, height) {
                try { url = new URL(url, location.href).href; } catch (_) { return; }
                if (!url || !/^https?:\/\//i.test(url) || seen.has(url)) return;
                seen.add(url);
                result.items.push({
                  url: url,
                  isVideo: !!isVideo,
                  thumbnail: thumbnail || '',
                  width: Number(width) || 0,
                  height: Number(height) || 0
                });
              };

              const directPath = (location.pathname || '').toLowerCase();
              if (/\.(jpe?g|png|gif|webp)$/.test(directPath)) add(location.href, false, '', 0, 0);
              if (/\.(mp4|m4v|webm)$/.test(directPath)) add(location.href, true, '', 0, 0);

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
                return /^https?:\/\//i.test(source);
              });
              if (videos.length > 0 && !hasDirectVideo && window.performance) {
                result.networkVideoUrls = Array.from(performance.getEntriesByType('resource') || [])
                  .map(function(entry) { return entry.name || ''; })
                  .filter(function(url) {
                    const lower = url.toLowerCase();
                    return /^https?:\/\//.test(lower) &&
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

              const isStory = isInstagram && path.indexOf('/stories/') === 0;
              if (isInstagram && !isStory && !singleVideoRoute && article) {
                const nextButton = Array.from(article.querySelectorAll('button, [role="button"]')).find(function(button) {
                  const labelled = button.matches('[aria-label]') ? button : button.querySelector('[aria-label]');
                  const label = ((labelled && labelled.getAttribute('aria-label')) || '').toLowerCase();
                  const rect = button.getBoundingClientRect();
                  return rect.width > 0 && rect.height > 0 && button.getAttribute('aria-disabled') !== 'true' &&
                    (label === 'next' || label.indexOf('next') >= 0);
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
