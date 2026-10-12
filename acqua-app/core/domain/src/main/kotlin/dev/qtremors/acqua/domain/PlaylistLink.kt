package dev.qtremors.acqua.domain

import java.net.URI

/** Identifies the two download scopes available on a YouTube video in a playlist. */
object PlaylistLink {
    fun isPlaylist(input: String): Boolean = uri(input)?.let { query(it)["list"].orEmpty().isNotBlank() } == true

    fun videoUrl(input: String): String? {
        val uri = uri(input) ?: return null
        val hasVideo = query(uri)["v"].orEmpty().isNotBlank() ||
            (uri.host.equals("youtu.be", true) && uri.path.trim('/').isNotBlank()) ||
            listOf("/shorts/", "/live/", "/embed/").any { uri.path.startsWith(it) }
        if (!hasVideo) return null
        val query = uri.rawQuery.orEmpty().split('&').filter {
            it.isNotBlank() && it.substringBefore('=') !in setOf("list", "index", "start_radio")
        }.joinToString("&")
        return "${uri.scheme}://${uri.rawAuthority}${uri.rawPath}" +
            (if (query.isEmpty()) "" else "?$query") + (uri.rawFragment?.let { "#$it" }.orEmpty())
    }

    private fun uri(input: String): URI? = WebLink.normalize(input)?.takeIf(WebLink::isYouTubeUrl)?.let(::URI)
    private fun query(uri: URI): Map<String, String> = uri.rawQuery.orEmpty().split('&')
        .associate { it.substringBefore('=') to it.substringAfter('=', "") }
}
