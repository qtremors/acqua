package dev.qtremors.acqua.domain

import java.net.URI

object WebLink {
    private val webUrlPattern = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE)
    private val trailingPunctuation = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"')

    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val candidate = if (trimmed.contains(Regex("\\s"))) {
            webUrlPattern.find(trimmed)?.value ?: return null
        } else {
            trimmed
        }.trimEnd(*trailingPunctuation)

        val withScheme = if (candidate.startsWith("http://", true) || candidate.startsWith("https://", true)) {
            candidate
        } else {
            "https://$candidate"
        }

        return runCatching {
            val uri = URI(withScheme)
            require(uri.scheme.equals("https", true) || uri.scheme.equals("http", true))
            require(!uri.host.isNullOrBlank())
            require(uri.userInfo == null)
            uri.toASCIIString()
        }.getOrNull()
    }

    fun extractFirst(input: String): String? = normalize(input)

    fun host(url: String): String? = normalize(url)?.let { normalized ->
        runCatching { URI(normalized).host?.lowercase() }.getOrNull()
    }

    fun origin(url: String): String? = normalize(url)?.let { normalized ->
        runCatching {
            val uri = URI(normalized)
            val port = if (uri.port == -1) "" else ":${uri.port}"
            "${uri.scheme.lowercase()}://${uri.host.lowercase()}$port"
        }.getOrNull()
    }

    fun isInstagramHost(url: String): Boolean {
        val host = host(url) ?: return false
        return host == "instagram.com" || host.endsWith(".instagram.com") ||
            host == "instagr.am" || host.endsWith(".instagr.am")
    }

    fun isInstagramMediaUrl(url: String): Boolean {
        val normalized = normalize(url) ?: return false
        if (!isInstagramHost(normalized)) return false
        val path = runCatching { URI(normalized).path.orEmpty() }.getOrDefault("")
        return listOf("/p/", "/reel/", "/tv/", "/stories/").any { path.startsWith(it, true) }
    }

    fun isYouTubeUrl(url: String): Boolean {
        val host = host(url) ?: return false
        return host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
    }

    fun isYouTubeMusicUrl(url: String): Boolean =
        host(url) == "music.youtube.com"

    fun preferredEngine(input: String, fallback: DownloadEngine): DownloadEngine {
        val url = normalize(input) ?: return fallback
        if (!isInstagramHost(url)) return fallback
        return when (URI(url).path.orEmpty().split('/').getOrNull(1)?.lowercase()) {
            "p" -> DownloadEngine.ACQUA
            "reel", "reels", "tv" -> DownloadEngine.YT_DLP
            else -> fallback
        }
    }

    // Carousel navigation changes the query without changing the Instagram post.
    fun mediaPageIdentity(input: String): String? {
        val url = normalize(input) ?: return null
        return if (isInstagramMediaUrl(url)) {
            "${host(url)}${URI(url).path.trimEnd('/')}"
        } else url
    }
}
