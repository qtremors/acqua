package dev.qtremors.acqua.domain

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object BrowserDestination {
    fun fromInput(input: String): String? {
        val value = input.trim()
        if (value.isEmpty()) return null
        val containsExplicitUrl = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE).containsMatchIn(value)
        val looksLikeHost = !value.any(Char::isWhitespace) && (
            value.contains('.') || value.startsWith("localhost", true) || IPV4.matches(value)
        )
        if (containsExplicitUrl || looksLikeHost) WebLink.normalize(value)?.let { return it }
        return "$SEARCH_URL${URLEncoder.encode(value, StandardCharsets.UTF_8.name())}"
    }

    private val IPV4 = Regex("(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d+)?(?:/.*)?")
    private const val SEARCH_URL = "https://duckduckgo.com/?q="
}
