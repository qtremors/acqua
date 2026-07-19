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
        forceBrowserResolution: Boolean = false,
        browserResolver: suspend (url: String, explicitSessionAuthorization: Boolean) -> List<ResolvedMedia>
    ): List<ResolvedMedia> {
        val url = WebLink.normalize(input)
            ?: throw IllegalArgumentException("Enter a valid HTTP or HTTPS link.")

        if (forceBrowserResolution) {
            return validateAndSelect(url, browserResolver(url, true))
        }
        if (!WebLink.isInstagramMediaUrl(url)) {
            if (!browserSessionsEnabled) error("Enable browser session extraction to resolve this page.")
            return validateAndSelect(url, browserResolver(url, false))
        }

        val sourceFailure = try {
            val candidates = withContext(Dispatchers.IO) {
                sourceResolver.resolve(url).map { it.copy(referer = it.referer ?: url) }
            }
            return validateAndSelect(url, candidates)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            error
        }

        if (!browserSessionsEnabled) throw sourceFailure
        return try {
            validateAndSelect(url, browserResolver(url, false))
        } catch (error: CancellationException) {
            throw error
        } catch (browserFailure: Exception) {
            browserFailure.addSuppressed(sourceFailure)
            throw browserFailure
        }
    }

    private suspend fun validateAndSelect(
        sourceUrl: String,
        candidates: List<ResolvedMedia>
    ): List<ResolvedMedia> {
        val validated = coroutineScope {
            candidates.map { item -> async(Dispatchers.IO) { inspectMedia(item) } }
                .awaitAll().filterNotNull()
        }
        if (validated.isEmpty()) error("The resolved links did not contain complete downloadable media files.")
        return selectBest(sourceUrl, validated)
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
