package dev.qtremors.acqua.resolver.ytdlp

import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.SessionAwareMediaResolver
import dev.qtremors.acqua.downloader.YtDlpEngine

class YtDlpResolver(private val engine: YtDlpEngine) : SessionAwareMediaResolver {
    override fun resolve(url: String, allowBrowserSession: Boolean): List<ResolvedMedia> =
        listOf(engine.inspect(url, allowBrowserSession))
}
