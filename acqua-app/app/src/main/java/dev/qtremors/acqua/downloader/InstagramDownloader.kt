package dev.qtremors.acqua.downloader

import android.graphics.BitmapFactory
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.FormBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class AgeGateException(message: String) : Exception(message)
class ExpiredSessionException(message: String) : Exception(message)

data class MediaResult(
    val url: String,
    val isVideo: Boolean,
    val thumbnailUrl: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val fileSize: Long? = null,
    val username: String? = null,
    val referer: String? = null,
    val requestCookies: String? = null
) {
    val previewUrl: String? get() = thumbnailUrl ?: url.takeIf { !isVideo }
}

object InstagramDownloader {

    private data class ScopedRequestCookies(val host: String, val value: String)

    private val SHORTCODE_REGEX = Pattern.compile(
        "(?:instagram\\.com|instagr\\.am)/(?:reel|p|tv)/([A-Za-z0-9_-]+)"
    )
    private val STORY_REGEX = Pattern.compile(
        "(?:instagram\\.com|instagr\\.am)/stories/([A-Za-z0-9._]+)/([0-9]+)"
    )

    private data class StoryRequest(val username: String, val mediaId: String)

    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()
    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore.getOrPut(url.host) { mutableListOf() }.apply {
                removeAll { c -> cookies.any { it.name == c.name } }
                addAll(cookies)
            }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookieStore[url.host] ?: emptyList()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .cookieJar(cookieJar)
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val scoped = request.tag(ScopedRequestCookies::class.java)
                ?: return@addNetworkInterceptor chain.proceed(request)
            val safeRequest = request.newBuilder().apply {
                if (request.url.host.equals(scoped.host, ignoreCase = true)) {
                    header("Cookie", scoped.value)
                } else {
                    removeHeader("Cookie")
                }
            }.build()
            chain.proceed(safeRequest)
        }
        .build()

    private const val DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"
    private const val MOBILE_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
    private const val MEDIA_SIGNATURE_BYTES = 65_536

    private var activeUserAgent: String? = null
    private var hasActiveSession: Boolean = false

    private fun getUserAgent(fallback: String): String {
        return activeUserAgent ?: fallback
    }

    fun setSessionCookies(cookiesString: String, userAgent: String?) {
        activeUserAgent = userAgent?.takeIf { it.isNotBlank() }
        cookieStore.clear()

        val cookies = mutableListOf<Cookie>()
        cookiesString.split(";").forEach { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                val name = parts[0].trim()
                val value = parts[1].trim()
                if (name.isNotEmpty() && value.isNotEmpty()) {
                    val cookie = Cookie.Builder()
                        .domain("instagram.com")
                        .path("/")
                        .name(name)
                        .value(value)
                        .secure()
                        .build()
                    cookies.add(cookie)
                }
            }
        }

        if (cookies.isNotEmpty()) {
            cookieStore["www.instagram.com"] = cookies
            cookieStore["instagram.com"] = cookies
            hasActiveSession = true
        }
    }

    fun clearSessionCookies() {
        cookieStore.clear()
        activeUserAgent = null
        hasActiveSession = false
    }

    fun getMediaItems(postUrl: String): List<MediaResult> {
        extractStory(postUrl)?.let { story ->
            return tryStory(story)
        }

        val shortcode = extractShortcode(postUrl)
            ?: throw IllegalArgumentException("Invalid Instagram URL: $postUrl")

        if (hasActiveSession) {
            return getAuthenticatedMediaItems(shortcode)
        }

        val embedError: String
        val embedException: Exception?
        try {
            return tryEmbedPage(shortcode)
        } catch (e: AgeGateException) {
            throw e
        } catch (e: ExpiredSessionException) {
            throw e
        } catch (e: Exception) {
            embedException = e
            embedError = e.message ?: e.javaClass.simpleName
        }

        val graphqlError: String
        try {
            return tryGraphQL(shortcode)
        } catch (e: AgeGateException) {
            throw e
        } catch (e: ExpiredSessionException) {
            throw e
        } catch (e: Exception) {
            graphqlError = e.message ?: e.javaClass.simpleName
        }

        // If both failed and one of them detected a private error from profile or redirect
        if (embedException is UnsupportedOperationException) {
            throw embedException
        }

        throw Exception(
            "Instagram extraction failed.\n\n" +
            "Embed Page Error: $embedError\n" +
            "GraphQL Query Error: $graphqlError"
        )
    }

    private fun getAuthenticatedMediaItems(shortcode: String): List<MediaResult> {
        val graphqlError = try {
            return tryGraphQL(shortcode)
        } catch (e: ExpiredSessionException) {
            throw e
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }

        val embedError = try {
            return tryEmbedPage(shortcode)
        } catch (e: ExpiredSessionException) {
            throw e
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }

        throw Exception(
            "Authenticated media extraction failed.\n\n" +
                "Authenticated Query Error: $graphqlError\n" +
                "Public Embed Error: $embedError"
        )
    }

    private fun tryStory(story: StoryRequest): List<MediaResult> {
        val userId = fetchPublicUserId(story.username)
        val reelsJson = fetchStoryReels(userId, story.mediaId, story.username)
        val items = extractStoryMedia(reelsJson, userId, story.mediaId)
        if (items.isNotEmpty()) return items

        val reelCount = JSONObject(reelsJson).optJSONObject("reels")?.length() ?: 0
        throw UnsupportedOperationException(
            "Could not load story details. Resolved user ID: $userId, reels count is $reelCount. " +
            "Stories may have expired, or require a login session to bypass safety gating."
        )
    }

    private fun fetchPublicUserId(username: String): String {
        val encodedUsername = URLEncoder.encode(username, "UTF-8")
        val response = client.newCall(
            Request.Builder()
                .url("https://www.instagram.com/api/v1/users/web_profile_info/?username=$encodedUsername")
                .header("User-Agent", getUserAgent(DESKTOP_UA))
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "https://www.instagram.com/$username/")
                .header("X-IG-App-ID", "936619743392459")
                .header("X-ASBD-ID", "129477")
                .header("X-Requested-With", "XMLHttpRequest")
                .get().build()
        ).execute()

        val body = response.body?.string()
            ?: throw Exception("Empty user profile body (HTTP ${response.code})")

        if (response.code == 401 || body.contains("login_required") || body.contains("accounts/login")) {
            if (hasActiveSession) throw ExpiredSessionException("Session expired while retrieving profile.")
            throw AgeGateException("Profile page requires authentication.")
        }

        if (!response.isSuccessful) {
            throw Exception("Profile request failed (HTTP ${response.code}): ${body.take(200)}")
        }

        val user = JSONObject(body)
            .optJSONObject("data")
            ?.optJSONObject("user")
            ?: throw Exception("No user payload found in profile response")

        if (user.optBoolean("is_private", false) && !hasActiveSession) {
            throw AgeGateException("Sign in to access media from @$username that is visible to your account.")
        }

        return user.optString("id").takeIf { it.isNotBlank() }
            ?: throw Exception("No user ID found in profile payload")
    }

    private fun fetchStoryReels(userId: String, mediaId: String, username: String): String {
        val response = client.newCall(
            Request.Builder()
                .url("https://www.instagram.com/api/v1/feed/reels_media/?reel_ids=$userId&media_id=$mediaId")
                .header("User-Agent", getUserAgent(DESKTOP_UA))
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "https://www.instagram.com/stories/$username/$mediaId/")
                .header("X-IG-App-ID", "936619743392459")
                .header("X-ASBD-ID", "129477")
                .header("X-Requested-With", "XMLHttpRequest")
                .get().build()
        ).execute()

        val body = response.body?.string()
            ?: throw Exception("Empty stories body (HTTP ${response.code})")

        if (response.code == 401 || body.contains("login_required") || body.contains("accounts/login")) {
            if (hasActiveSession) throw ExpiredSessionException("Session expired while retrieving story.")
            throw AgeGateException("Story requires login session.")
        }

        if (body.trimStart().startsWith('<')) {
            throw Exception("Expected stories JSON, got HTML (HTTP ${response.code})")
        }
        if (!response.isSuccessful) {
            throw Exception("Stories fetch failed (HTTP ${response.code}): ${body.take(200)}")
        }
        return body
    }

    private fun extractStoryMedia(reelsJson: String, userId: String, mediaId: String): List<MediaResult> {
        val reels = JSONObject(reelsJson).optJSONObject("reels") ?: return emptyList()
        val reel = reels.optJSONObject(userId) ?: run {
            val keys = reels.keys()
            var found: JSONObject? = null
            while (keys.hasNext() && found == null) {
                found = reels.optJSONObject(keys.next())
            }
            found
        } ?: return emptyList()

        val storyItems = reel.optJSONArray("items") ?: return emptyList()
        for (i in 0 until storyItems.length()) {
            val item = storyItems.optJSONObject(i) ?: continue
            val id = item.optString("id")
            val pk = item.optString("pk")
            if (id == mediaId || id.startsWith("${mediaId}_") || pk == mediaId) {
                return extractSingleStoryItem(item)?.let { listOf(it) } ?: emptyList()
            }
        }
        return emptyList()
    }

    private fun extractSingleStoryItem(item: JSONObject): MediaResult? {
        val posterCandidate = highestResolutionCandidate(
            item.optJSONObject("image_versions2")?.optJSONArray("candidates")
        )
        val poster = posterCandidate?.optString("url")?.takeIf { it.isNotBlank() }
        val posterWidth = posterCandidate?.optInt("width") ?: 0
        val posterHeight = posterCandidate?.optInt("height") ?: 0

        val username = item.optJSONObject("user")?.optString("username").takeIf { !it.isNullOrEmpty() }

        highestResolutionCandidate(item.optJSONArray("video_versions"))?.let { video ->
            val videoUrl = video.optString("url").takeIf { it.isNotBlank() }
            if (videoUrl != null) {
                return MediaResult(
                    url = videoUrl,
                    isVideo = true,
                    thumbnailUrl = poster,
                    width = video.optInt("width").takeIf { it > 0 } ?: posterWidth,
                    height = video.optInt("height").takeIf { it > 0 } ?: posterHeight,
                    username = username
                )
            }
        }

        poster?.let {
            return MediaResult(
                url = it,
                isVideo = false,
                thumbnailUrl = it,
                width = posterWidth,
                height = posterHeight,
                username = username
            )
        }

        return null
    }

    private fun highestResolutionCandidate(candidates: JSONArray?): JSONObject? {
        if (candidates == null || candidates.length() == 0) return null

        var best: JSONObject? = null
        var bestArea = -1L
        for (i in 0 until candidates.length()) {
            val candidate = candidates.optJSONObject(i) ?: continue
            val area = candidate.optInt("width").toLong() * candidate.optInt("height").toLong()
            if (best == null || area > bestArea) {
                best = candidate
                bestArea = area
            }
        }
        return best
    }

    private fun tryEmbedPage(shortcode: String): List<MediaResult> {
        val response = client.newCall(
            Request.Builder()
                .url("https://www.instagram.com/p/$shortcode/embed/captioned/")
                .header("User-Agent", getUserAgent(MOBILE_UA))
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Referer", "https://www.instagram.com/")
                .get().build()
        ).execute()

        val html = response.body?.string()
            ?: throw Exception("Empty body in embed page response (HTTP ${response.code})")

        if (response.code == 401 || html.contains("accounts/login") || html.contains("/accounts/login/") || html.contains("login_required")) {
            if (hasActiveSession) throw ExpiredSessionException("Session expired during embed parsing.")
            throw AgeGateException("Embed page requests redirection to Instagram login.")
        }

        if (!response.isSuccessful) {
            throw Exception("Embed page request failed (HTTP ${response.code}): ${html.take(120)}")
        }

        if (html.contains("PolarisErrorRoute") && html.contains("httpErrorPage")) {
            throw UnsupportedOperationException(
                "Instagram returned an unavailable-content page instead of media."
            )
        }

        try {
            val jsonBlock = extractJsonBlock(html, "\\\"shortcode_media\\\":")
                ?.let { unescapeJson(it) }
                ?: extractJsonBlock(html, "\"shortcode_media\":")
            if (jsonBlock != null) {
                val mediaJson = JSONObject(jsonBlock)
                val items = parseShortcodeMedia(mediaJson)
                if (items.isNotEmpty()) return items
            }
        } catch (e: Exception) {
            // Fallback to regex if JSON block parsing fails
        }

        fun String.unescape() = replace("\\\\\\/", "/").replace("\\u0026", "&")

        val embedUsername = Regex("""\\"owner_username\\":\\"([^\\"]+)\\"""").find(html)?.groupValues?.get(1)?.unescape()
            ?: Regex("""class=\\"EmbedHeaderUser\\" href=\\"https://www.instagram.com/([^/]+)/\\"""").find(html)?.groupValues?.get(1)?.unescape()

        Regex("""\\"video_url\\":\\"(https:(?:(?!\\").)*)""").find(html)?.let {
            val poster = Regex("""\\"display_url\\":\\"(https:(?:(?!\\").)*)""")
                .find(html)?.groupValues?.get(1)?.unescape()
            return listOf(MediaResult(it.groupValues[1].unescape(), isVideo = true, thumbnailUrl = poster, username = embedUsername))
        }

        val images = Regex("""\\"display_url\\":\\"(https:(?:(?!\\").)*)""")
            .findAll(html)
            .map { MediaResult(it.groupValues[1].unescape(), isVideo = false, username = embedUsername) }
            .distinctBy { it.url }
            .toList()
        if (images.isNotEmpty()) return images

        Regex("""<meta property="og:image" content="([^"]+)"""").find(html)?.let {
            return listOf(MediaResult(it.groupValues[1].unescape(), isVideo = false, username = embedUsername))
        }

        throw Exception("Failed to find any media URLs in embed markup (len=${html.length})")
    }

    private fun tryGraphQL(shortcode: String): List<MediaResult> {
        client.newCall(
            Request.Builder()
                .url("https://www.instagram.com/")
                .header("User-Agent", getUserAgent(DESKTOP_UA))
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .get().build()
        ).execute().close()

        val csrfToken = cookieStore["www.instagram.com"]
            ?.firstOrNull { it.name == "csrftoken" }?.value ?: ""

        val body = FormBody.Builder()
            .add("variables", """{"shortcode":"$shortcode"}""")
            .add("doc_id", "8845758582119845")
            .build()

        val resp = client.newCall(
            Request.Builder()
                .url("https://www.instagram.com/graphql/query")
                .post(body)
                .header("User-Agent", getUserAgent(DESKTOP_UA))
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "https://www.instagram.com/")
                .header("X-IG-App-ID", "936619743392459")
                .header("X-CSRFToken", csrfToken)
                .build()
        ).execute()

        val respBody = resp.body?.string()
            ?: throw Exception("Empty GraphQL response body (HTTP ${resp.code})")

        if (respBody.trimStart().startsWith('<')) {
            if (respBody.contains("accounts/login") || respBody.contains("/accounts/login/") || respBody.contains("login_required") || resp.code == 401) {
                if (hasActiveSession) throw ExpiredSessionException("Session expired while querying GraphQL.")
                throw AgeGateException("GraphQL query requires login session.")
            }
            throw Exception("Expected GraphQL JSON, got HTML (HTTP ${resp.code}).")
        }

        if (!resp.isSuccessful) {
            throw Exception("GraphQL query failed (HTTP ${resp.code}): ${respBody.take(200)}")
        }

        val json = try {
            JSONObject(respBody)
        } catch (e: Exception) {
            throw Exception("Failed to parse GraphQL JSON: ${respBody.take(150)}")
        }

        if (json.isNull("data")) {
            val apiErr = json.optJSONArray("errors")
                ?.optJSONObject(0)?.optString("message", "Unknown GraphQL error") ?: "data payload is null"
            throw Exception("GraphQL response errors: $apiErr")
        }

        val media = json.getJSONObject("data")
            .optJSONObject("xdt_shortcode_media")
            ?: throw Exception("Missing shortcode media node in GraphQL payload")

        return parseShortcodeMedia(media)
    }

    private fun buildMediaRequest(
        url: String,
        referer: String? = null,
        requestCookies: String? = null
    ) = Request.Builder()
        .url(url)
        .header("User-Agent", getUserAgent(MOBILE_UA))
        .apply {
            header("Referer", referer?.takeIf { it.startsWith("http") } ?: "https://www.instagram.com/")
            requestCookies?.takeIf { it.isNotBlank() }?.let { cookies ->
                url.toHttpUrlOrNull()?.host?.let { host ->
                    tag(ScopedRequestCookies::class.java, ScopedRequestCookies(host, cookies))
                }
            }
        }
        .get().build()

    fun downloadToStream(
        url: String,
        out: OutputStream,
        expectedVideo: Boolean,
        referer: String? = null,
        requestCookies: String? = null
    ): Long {
        if (hasEmbeddedByteRange(url)) {
            throw Exception("A partial browser media segment cannot be saved as a complete file.")
        }
        return client.newCall(buildMediaRequest(url, referer, requestCookies)).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Media download request failed (HTTP ${response.code})")
            }

            val stream = response.body?.byteStream()
                ?: throw Exception("Empty response body stream during media download")
            val prefix = stream.readPrefix(MEDIA_SIGNATURE_BYTES)
            val kind = MediaContentDetector.detect(response.header("Content-Type"), prefix, expectedVideo)
                ?: throw Exception("The media server returned an unsupported or incomplete file.")

            if (kind == DetectedMediaKind.VIDEO != expectedVideo) {
                throw Exception(
                    if (expectedVideo) "The video URL returned an image instead of a complete video."
                    else "The image URL returned video data instead of an image."
                )
            }

            out.write(prefix)
            prefix.size.toLong() + stream.copyTo(out)
        }
    }

    fun getMediaFileSize(url: String): Long {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", getUserAgent(MOBILE_UA))
                .header("Referer", "https://www.instagram.com/")
                .header("Range", "bytes=0-0")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 206 || response.isSuccessful) {
                    val contentRange = response.header("Content-Range")
                    if (contentRange != null) {
                        val parts = contentRange.split("/")
                        if (parts.size == 2) {
                            parts[1].trim().toLongOrNull() ?: 0L
                        } else {
                            0L
                        }
                    } else {
                        response.header("Content-Length")?.toLongOrNull() ?: 0L
                    }
                } else {
                    0L
                }
            }
        }.getOrDefault(0L)
    }

    fun resolveMediaMetadata(item: MediaResult): MediaResult {
        return validateAndResolveMediaMetadata(item) ?: item
    }

    fun validateAndResolveMediaMetadata(item: MediaResult): MediaResult? {
        if (hasEmbeddedByteRange(item.url)) return null
        return runCatching {
            val request = Request.Builder()
                .url(item.url)
                .header("User-Agent", getUserAgent(MOBILE_UA))
                .header("Referer", item.referer?.takeIf { it.startsWith("http") } ?: "https://www.instagram.com/")
                .apply {
                    item.requestCookies?.takeIf { it.isNotBlank() }?.let { cookies ->
                        item.url.toHttpUrlOrNull()?.host?.let { host ->
                            tag(ScopedRequestCookies::class.java, ScopedRequestCookies(host, cookies))
                        }
                    }
                }
                .header("Range", "bytes=0-65535")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code != 206 && !response.isSuccessful) return@use null

                val totalSize = response.header("Content-Range")
                    ?.substringAfterLast('/')
                    ?.trim()
                    ?.toLongOrNull()
                    ?: response.header("Content-Length")?.toLongOrNull()
                    ?: 0L

                val bytes = response.body?.byteStream()?.readPrefix(MEDIA_SIGNATURE_BYTES)
                    ?: return@use null
                val mediaKind = MediaContentDetector.detect(
                    response.header("Content-Type"),
                    bytes,
                    item.isVideo
                ) ?: return@use null

                if (mediaKind == DetectedMediaKind.VIDEO) {
                    return@use item.copy(
                        isVideo = true,
                        thumbnailUrl = item.thumbnailUrl?.takeIf { it != item.url },
                        fileSize = totalSize.takeIf { it > 0L } ?: item.fileSize
                    )
                }

                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                val resolvedWidth = options.outWidth.takeIf { it > 0 } ?: item.width
                val resolvedHeight = options.outHeight.takeIf { it > 0 } ?: item.height

                item.copy(
                    isVideo = false,
                    thumbnailUrl = item.url,
                    width = resolvedWidth,
                    height = resolvedHeight,
                    fileSize = totalSize.takeIf { it > 0L } ?: item.fileSize
                )
            }
        }.getOrNull()
    }

    private fun hasEmbeddedByteRange(url: String): Boolean =
        url.contains("bytestart=", ignoreCase = true) ||
            url.contains("byteend=", ignoreCase = true)

    private fun InputStream.readPrefix(maxBytes: Int): ByteArray {
        val buffer = ByteArray(maxBytes)
        var total = 0
        while (total < maxBytes) {
            val read = read(buffer, total, maxBytes - total)
            if (read <= 0) break
            total += read
        }
        return buffer.copyOf(total)
    }

    fun fetchBytes(url: String, referer: String? = null, requestCookies: String? = null): ByteArray {
        val response = client.newCall(buildMediaRequest(url, referer, requestCookies)).execute()
        if (!response.isSuccessful) throw Exception("Failed to fetch bytes (HTTP ${response.code})")
        return response.body?.bytes() ?: throw Exception("Empty response body bytes")
    }

    private fun parseShortcodeMedia(media: JSONObject): List<MediaResult> {
        val username = media.optJSONObject("owner")?.optString("username").takeIf { !it.isNullOrEmpty() }
        val edges = media.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges")
        if (edges != null && edges.length() > 0) {
            val items = mutableListOf<MediaResult>()
            for (i in 0 until edges.length()) {
                val node = edges.getJSONObject(i).optJSONObject("node") ?: continue
                val poster = node.optString("display_url").takeIf { it.isNotEmpty() }
                val dimensions = node.optJSONObject("dimensions")
                val w = dimensions?.optInt("width") ?: 0
                val h = dimensions?.optInt("height") ?: 0
                if (node.optBoolean("is_video", false)) {
                    val url = node.optString("video_url").takeIf { it.isNotEmpty() } ?: continue
                    items += MediaResult(url, isVideo = true, thumbnailUrl = poster, width = w, height = h, username = username)
                } else {
                    val url = poster ?: continue
                    items += MediaResult(url, isVideo = false, thumbnailUrl = url, width = w, height = h, username = username)
                }
            }
            if (items.isNotEmpty()) return items
        }

        val poster = media.optString("display_url").takeIf { it.isNotEmpty() }
        val dimensions = media.optJSONObject("dimensions")
        val w = dimensions?.optInt("width") ?: 0
        val h = dimensions?.optInt("height") ?: 0
        return if (media.optBoolean("is_video", false)) {
            val url = media.optString("video_url").takeIf { it.isNotEmpty() }
                ?: throw Exception("Media node is marked as video, but video_url is empty")
            listOf(MediaResult(url, isVideo = true, thumbnailUrl = poster, width = w, height = h, username = username))
        } else {
            val url = poster ?: throw Exception("Display URL is missing in photo media node")
            listOf(MediaResult(url, isVideo = false, thumbnailUrl = url, width = w, height = h, username = username))
        }
    }

    private fun extractJsonBlock(html: String, key: String): String? {
        var index = html.indexOf(key)
        if (index == -1) return null
        index = html.indexOf("{", index + key.length)
        if (index == -1) return null

        var braceCount = 0
        var inString = false
        var isEscaped = false
        val result = StringBuilder()

        for (i in index until html.length) {
            val c = html[i]
            result.append(c)
            if (isEscaped) {
                isEscaped = false
                continue
            }
            if (c == '\\') {
                isEscaped = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '{') {
                    braceCount++
                } else if (c == '}') {
                    braceCount--
                    if (braceCount == 0) {
                        return result.toString()
                    }
                }
            }
        }
        return null
    }

    private fun unescapeJson(escaped: String): String {
        return escaped
            .replace("\\\"", "\"")
            .replace("\\\\\\/", "/")
            .replace("\\/", "/")
            .replace("\\\\", "\\")
    }

    fun extractShortcode(url: String): String? {
        val m = SHORTCODE_REGEX.matcher(url)
        return if (m.find()) m.group(1) else null
    }

    private fun extractStory(url: String): StoryRequest? {
        val m = STORY_REGEX.matcher(url)
        return if (m.find()) StoryRequest(m.group(1) ?: "", m.group(2) ?: "") else null
    }
}
