package dev.qtremors.acqua.domain

fun interface MediaResolver {
    fun resolve(url: String): List<ResolvedMedia>
}

interface SessionAwareMediaResolver : MediaResolver {
    fun resolve(url: String, allowBrowserSession: Boolean): List<ResolvedMedia>

    override fun resolve(url: String): List<ResolvedMedia> = resolve(url, false)
}
