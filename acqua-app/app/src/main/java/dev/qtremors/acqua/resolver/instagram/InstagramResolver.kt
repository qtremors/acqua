package dev.qtremors.acqua.resolver.instagram

import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.MediaResolver
import dev.qtremors.acqua.domain.ResolvedMedia

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.FormBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class AgeGateException(message: String) : Exception(message)
class ExpiredSessionException(message: String) : Exception(message)
class InstagramExtractionException(cause: Throwable? = null) :
    Exception("Instagram extraction failed.", cause)

class InstagramResolver internal constructor(
    private val origin: HttpUrl = DEFAULT_ORIGIN
) : MediaResolver {

    private val SHORTCODE_REGEX = Pattern.compile(
        "(?:instagram\\.com|instagr\\.am)/(?:reel|p|tv)/([A-Za-z0-9_-]+)"
    )
    private val STORY_REGEX = Pattern.compile(
        "(?:instagram\\.com|instagr\\.am)/stories/([A-Za-z0-9._]+)/([0-9]+)"
    )

    private data class StoryRequest(val username: String, val mediaId: String)

    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()
    private val cookieLock = Any()
    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(cookieLock) {
                cookieStore.getOrPut(url.host) { mutableListOf() }.apply {
                    removeAll { existing -> cookies.any { it.name == existing.name } }
                    addAll(cookies)
                }
            }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            synchronized(cookieLock) { cookieStore[url.host]?.toList().orEmpty() }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .cookieJar(cookieJar)
        .build()

    private val desktopUserAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"
    private val mobileUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
    @Volatile private var activeUserAgent: String? = null
    @Volatile private var hasActiveSession: Boolean = false

    private fun getUserAgent(fallback: String): String {
        return activeUserAgent ?: fallback
    }

    fun setSessionCookies(cookiesString: String, userAgent: String?) {
        activeUserAgent = userAgent?.takeIf { it.isNotBlank() }
        synchronized(cookieLock) { cookieStore.clear() }

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
            synchronized(cookieLock) {
                cookieStore["www.instagram.com"] = cookies.toMutableList()
                cookieStore["instagram.com"] = cookies.toMutableList()
            }
            hasActiveSession = true
        } else {
            hasActiveSession = false
        }
    }

    fun clearSessionCookies() {
        synchronized(cookieLock) { cookieStore.clear() }
        activeUserAgent = null
        hasActiveSession = false
    }

    override fun resolve(url: String): List<ResolvedMedia> {
        val postUrl = url
        extractStory(postUrl)?.let { story ->
            return tryStory(story)
        }

        val shortcode = extractShortcode(postUrl)
            ?: throw IllegalArgumentException("Invalid Instagram URL.")

        if (hasActiveSession) {
            return getAuthenticatedMediaItems(shortcode)
        }

        val embedException: Exception?
        try {
            return tryEmbedPage(shortcode)
        } catch (e: AgeGateException) {
            throw e
        } catch (e: ExpiredSessionException) {
            throw e
        } catch (e: Exception) {
            embedException = e
        }

        val graphqlException: Exception
        try {
            return tryGraphQL(shortcode)
        } catch (e: AgeGateException) {
            throw e
        } catch (e: ExpiredSessionException) {
            throw e
        } catch (e: Exception) {
            graphqlException = e
        }

        // If both failed and one of them detected a private error from profile or redirect
        if (embedException is UnsupportedOperationException) {
            throw embedException
        }

        throw InstagramExtractionException(graphqlException).apply {
            addSuppressed(embedException)
        }
    }

    private fun getAuthenticatedMediaItems(shortcode: String): List<ResolvedMedia> {
        val graphqlException = try {
            return tryGraphQL(shortcode)
        } catch (e: ExpiredSessionException) {
            throw e
        } catch (e: Exception) {
            e
        }

        val embedException = try {
            return tryEmbedPage(shortcode)
        } catch (e: ExpiredSessionException) {
            throw e
        } catch (e: Exception) {
            e
        }

        throw InstagramExtractionException(embedException).apply {
            addSuppressed(graphqlException)
        }
    }

    private fun tryStory(story: StoryRequest): List<ResolvedMedia> {
        val userId = fetchPublicUserId(story.username)
        val reelsJson = fetchStoryReels(userId, story.mediaId, story.username)
        val items = extractStoryMedia(reelsJson, userId, story.mediaId)
        if (items.isNotEmpty()) return items

        throw UnsupportedOperationException("Story media is unavailable.")
    }

    private fun fetchPublicUserId(username: String): String {
        val response = client.newCall(
            Request.Builder()
                .url(
                    origin.newBuilder()
                        .addPathSegments("api/v1/users/web_profile_info/")
                        .addQueryParameter("username", username)
                        .build()
                )
                .header("User-Agent", getUserAgent(desktopUserAgent))
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", origin.resolve("$username/").toString())
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
            throw Exception("Profile request failed (HTTP ${response.code})")
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
                .url(
                    origin.newBuilder()
                        .addPathSegments("api/v1/feed/reels_media/")
                        .addQueryParameter("reel_ids", userId)
                        .addQueryParameter("media_id", mediaId)
                        .build()
                )
                .header("User-Agent", getUserAgent(desktopUserAgent))
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", origin.resolve("stories/$username/$mediaId/").toString())
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
            throw Exception("Stories fetch failed (HTTP ${response.code})")
        }
        return body
    }

    private fun extractStoryMedia(reelsJson: String, userId: String, mediaId: String): List<ResolvedMedia> {
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

    private fun extractSingleStoryItem(item: JSONObject): ResolvedMedia? {
        val posterCandidate = highestResolutionCandidate(
            item.optJSONObject("image_versions2")?.optJSONArray("candidates")
        )
        val poster = posterCandidate?.optString("url")?.takeIf { it.isNotBlank() }
        val posterWidth = posterCandidate?.optInt("width") ?: 0
        val posterHeight = posterCandidate?.optInt("height") ?: 0

        val username = item.optJSONObject("user")?.optString("username").takeIf { !it.isNullOrEmpty() }
        val sourceTimestampMillis = sourceTimestampMillis(item)

        highestResolutionCandidate(item.optJSONArray("video_versions"))?.let { video ->
            val videoUrl = video.optString("url").takeIf { it.isNotBlank() }
            if (videoUrl != null) {
                return ResolvedMedia(
                    url = videoUrl,
                    kind = MediaKind.VIDEO,
                    thumbnailUrl = poster,
                    width = video.optInt("width").takeIf { it > 0 } ?: posterWidth,
                    height = video.optInt("height").takeIf { it > 0 } ?: posterHeight,
                    username = username,
                    sourceTimestampMillis = sourceTimestampMillis
                )
            }
        }

        poster?.let {
            return ResolvedMedia(
                url = it,
                kind = MediaKind.IMAGE,
                thumbnailUrl = it,
                width = posterWidth,
                height = posterHeight,
                username = username,
                sourceTimestampMillis = sourceTimestampMillis
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

    private fun tryEmbedPage(shortcode: String): List<ResolvedMedia> {
        val response = client.newCall(
            Request.Builder()
                .url(origin.resolve("p/$shortcode/embed/captioned/").toString())
                .header("User-Agent", getUserAgent(mobileUserAgent))
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Referer", origin.toString())
                .get().build()
        ).execute()

        val html = response.body?.string()
            ?: throw Exception("Empty body in embed page response (HTTP ${response.code})")

        if (response.code == 401 || html.contains("accounts/login") || html.contains("/accounts/login/") || html.contains("login_required")) {
            if (hasActiveSession) throw ExpiredSessionException("Session expired during embed parsing.")
            throw AgeGateException("Embed page requests redirection to Instagram login.")
        }

        if (!response.isSuccessful) {
            throw Exception("Embed page request failed (HTTP ${response.code})")
        }

        if (html.contains("PolarisErrorRoute") && html.contains("httpErrorPage")) {
            throw UnsupportedOperationException(
                "Instagram returned an unavailable-content page instead of media."
            )
        }

        val structuredItems = runCatching {
            InstagramEmbeddedJson.findShortcodeMedia(html)?.let(::parseShortcodeMedia)
                ?: extractJsonBlock(html, "\\\"shortcode_media\\\":")
                    ?.let(::unescapeJson)
                    ?.let(::JSONObject)
                    ?.let(::parseShortcodeMedia)
                ?: extractJsonBlock(html, "\"shortcode_media\":")
                    ?.let(::JSONObject)
                    ?.let(::parseShortcodeMedia)
        }.getOrNull()
        if (!structuredItems.isNullOrEmpty()) return structuredItems

        fun String.unescape() = replace("\\\\\\/", "/").replace("\\u0026", "&")

        val embedUsername = Regex("""\\"owner_username\\":\\"([^\\"]+)\\"""").find(html)?.groupValues?.get(1)?.unescape()
            ?: Regex("""class=\\"EmbedHeaderUser\\" href=\\"https://www.instagram.com/([^/]+)/\\"""").find(html)?.groupValues?.get(1)?.unescape()

        Regex("""\\"video_url\\":\\"(https:(?:(?!\\").)*)""").find(html)?.let {
            val poster = Regex("""\\"display_url\\":\\"(https:(?:(?!\\").)*)""")
                .find(html)?.groupValues?.get(1)?.unescape()
            return listOf(ResolvedMedia(it.groupValues[1].unescape(), MediaKind.VIDEO, thumbnailUrl = poster, username = embedUsername))
        }

        val images = Regex("""\\"display_url\\":\\"(https:(?:(?!\\").)*)""")
            .findAll(html)
            .map { ResolvedMedia(it.groupValues[1].unescape(), MediaKind.IMAGE, username = embedUsername) }
            .distinctBy { it.url }
            .toList()
        if (images.isNotEmpty()) return images

        Regex("""<meta property="og:image" content="([^"]+)"""").find(html)?.let {
            return listOf(ResolvedMedia(it.groupValues[1].unescape(), MediaKind.IMAGE, username = embedUsername))
        }

        throw Exception("Failed to find any media URLs in embed markup (len=${html.length})")
    }

    private fun tryGraphQL(shortcode: String): List<ResolvedMedia> {
        client.newCall(
            Request.Builder()
                .url(origin)
                .header("User-Agent", getUserAgent(desktopUserAgent))
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .get().build()
        ).execute().close()

        val csrfToken = synchronized(cookieLock) {
            cookieStore["www.instagram.com"]
                ?.firstOrNull { it.name == "csrftoken" }
                ?.value
                .orEmpty()
        }

        val body = FormBody.Builder()
            .add("variables", """{"shortcode":"$shortcode"}""")
            .add("doc_id", "8845758582119845")
            .build()

        val resp = client.newCall(
            Request.Builder()
                .url(origin.resolve("graphql/query").toString())
                .post(body)
                .header("User-Agent", getUserAgent(desktopUserAgent))
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", origin.toString())
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
            throw Exception("GraphQL query failed (HTTP ${resp.code})")
        }

        val json = try {
            JSONObject(respBody)
        } catch (parseError: Exception) {
            throw Exception("Failed to parse GraphQL JSON", parseError)
        }

        if (json.isNull("data")) {
            throw Exception("GraphQL response reported an error")
        }

        val media = json.getJSONObject("data")
            .optJSONObject("xdt_shortcode_media")
            ?: throw Exception("Missing shortcode media node in GraphQL payload")

        return parseShortcodeMedia(media)
    }


    private fun parseShortcodeMedia(media: JSONObject): List<ResolvedMedia> {
        val username = media.optJSONObject("owner")?.optString("username").takeIf { !it.isNullOrEmpty() }
        val postTimestampMillis = sourceTimestampMillis(media)
        val edges = media.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges")
        if (edges != null && edges.length() > 0) {
            val items = mutableListOf<ResolvedMedia>()
            for (i in 0 until edges.length()) {
                val node = edges.getJSONObject(i).optJSONObject("node") ?: continue
                val poster = node.optString("display_url").takeIf { it.isNotEmpty() }
                val dimensions = node.optJSONObject("dimensions")
                val w = dimensions?.optInt("width") ?: 0
                val h = dimensions?.optInt("height") ?: 0
                val itemTimestampMillis = sourceTimestampMillis(node) ?: postTimestampMillis
                if (node.optBoolean("is_video", false)) {
                    val url = node.optString("video_url").takeIf { it.isNotEmpty() } ?: continue
                    items += ResolvedMedia(
                        url, MediaKind.VIDEO, thumbnailUrl = poster, width = w, height = h,
                        username = username, sourceTimestampMillis = itemTimestampMillis
                    )
                } else {
                    val url = poster ?: continue
                    items += ResolvedMedia(
                        url, MediaKind.IMAGE, thumbnailUrl = url, width = w, height = h,
                        username = username, sourceTimestampMillis = itemTimestampMillis
                    )
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
            listOf(
                ResolvedMedia(
                    url, MediaKind.VIDEO, thumbnailUrl = poster, width = w, height = h,
                    username = username, sourceTimestampMillis = postTimestampMillis
                )
            )
        } else {
            val url = poster ?: throw Exception("Display URL is missing in photo media node")
            listOf(
                ResolvedMedia(
                    url, MediaKind.IMAGE, thumbnailUrl = url, width = w, height = h,
                    username = username, sourceTimestampMillis = postTimestampMillis
                )
            )
        }
    }

    private companion object {
        val DEFAULT_ORIGIN = "https://www.instagram.com/".toHttpUrl()
    }

    private fun sourceTimestampMillis(item: JSONObject): Long? = sequenceOf(
        item.optLong("taken_at_timestamp"),
        item.optLong("taken_at"),
        item.optLong("published_time")
    ).firstOrNull { it > 0L }?.let { seconds ->
        if (seconds > 10_000_000_000L) seconds else seconds * 1_000L
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
