package dev.qtremors.acqua.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class MediaResolutionService(
    private val sourceResolver: MediaResolver,
    private val inspectMedia: (ResolvedMedia) -> ResolvedMedia?
) {
    suspend fun resolve(
        input: String,
        browserSessionsEnabled: Boolean,
        browserResolver: suspend (String) -> List<ResolvedMedia>
    ): List<ResolvedMedia> {
        val url = WebLink.normalize(input)
            ?: throw IllegalArgumentException("Enter a valid HTTP or HTTPS link.")
        val candidates = if (WebLink.isInstagramMediaUrl(url)) {
            resolveKnownSource(url, browserSessionsEnabled, browserResolver)
        } else {
            if (!browserSessionsEnabled) error("Enable browser session extraction to resolve this page.")
            browserResolver(url)
        }
        val validated = coroutineScope {
            candidates.map { item -> async(Dispatchers.IO) { inspectMedia(item) } }
                .awaitAll().filterNotNull()
        }
        if (validated.isEmpty()) error("The resolved links did not contain complete downloadable media files.")
        return selectBest(url, validated)
    }

    private suspend fun resolveKnownSource(
        url: String,
        browserSessionsEnabled: Boolean,
        browserResolver: suspend (String) -> List<ResolvedMedia>
    ): List<ResolvedMedia> {
        val networkFailure = try {
            return withContext(Dispatchers.IO) {
                sourceResolver.resolve(url).map { it.copy(referer = it.referer ?: url) }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            error
        }
        if (!browserSessionsEnabled) throw networkFailure
        return try {
            browserResolver(url)
        } catch (error: CancellationException) {
            throw error
        } catch (browserFailure: Exception) {
            throw Exception(
                "Network extraction failed: ${networkFailure.message}\n" +
                    "Browser extraction failed: ${browserFailure.message}"
            )
        }
    }

    private fun selectBest(sourceUrl: String, items: List<ResolvedMedia>): List<ResolvedMedia> {
        val singleVideo = WebLink.isInstagramMediaUrl(sourceUrl) &&
            Regex("/(?:reel|tv)/", RegexOption.IGNORE_CASE).containsMatchIn(sourceUrl)
        if (!singleVideo) return items.distinctBy(ResolvedMedia::url)
        return items.filter(ResolvedMedia::isVideo).maxWithOrNull(
            compareBy<ResolvedMedia> { it.fileSize ?: 0L }
                .thenBy { it.width.toLong() * it.height.toLong() }
        )?.let(::listOf) ?: items.distinctBy(ResolvedMedia::url)
    }
}
