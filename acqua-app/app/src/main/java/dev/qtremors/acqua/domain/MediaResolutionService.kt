package dev.qtremors.acqua.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class MediaResolutionService(
    private val sourceResolver: MediaResolver,
    private val ytDlpResolver: MediaResolver? = null,
    private val inspectMedia: (ResolvedMedia) -> ResolvedMedia?
) {
    suspend fun resolve(
        input: String,
        browserSessionsEnabled: Boolean,
        forceBrowserResolution: Boolean = false,
        engine: DownloadEngine = DownloadEngine.AUTO,
        browserResolver: suspend (url: String, explicitSessionAuthorization: Boolean) -> List<ResolvedMedia>
    ): List<ResolvedMedia> {
        val url = WebLink.normalize(input)
            ?: throw MediaResolutionException(
                MediaResolutionFailure.INVALID_LINK,
                "Enter a valid HTTP or HTTPS link."
            )

        if (engine == DownloadEngine.YT_DLP ||
            (engine == DownloadEngine.AUTO && WebLink.isYouTubeUrl(url))
        ) {
            return resolveWithYtDlp(url, browserSessionsEnabled || forceBrowserResolution)
        }
        if (engine == DownloadEngine.ACQUA && WebLink.isYouTubeUrl(url)) {
            throw MediaResolutionException(
                MediaResolutionFailure.ENGINE_REQUIRED,
                "YouTube links require the yt-dlp engine."
            )
        }
        if (forceBrowserResolution) {
            return validateAndSelect(url, browserResolver(url, true))
        }
        if (!WebLink.isInstagramMediaUrl(url)) {
            if (engine == DownloadEngine.AUTO) {
                runCatching { resolveWithYtDlp(url, browserSessionsEnabled) }
                    .getOrNull()
                    ?.takeIf(List<ResolvedMedia>::isNotEmpty)
                    ?.let { return it }
            }
            if (!browserSessionsEnabled) {
                throw MediaResolutionException(
                    MediaResolutionFailure.SESSION_REQUIRED,
                    "Open this page in Acqua Browser or enable browser session extraction."
                )
            }
            return validateAndSelect(url, browserResolver(url, false))
        }

        val sourceFailure = try {
            val candidates = withContext(Dispatchers.IO) {
                sourceResolver.resolve(url).map { it.copy(referer = it.referer ?: url) }
            }
            val selected = validateAndSelect(url, candidates)
            return if (engine == DownloadEngine.AUTO) {
                preferHigherInstagramVariant(url, selected, browserSessionsEnabled)
            } else {
                selected
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            error
        }

        if (engine == DownloadEngine.AUTO) {
            ytDlpResolver?.let {
                runCatching { resolveWithYtDlp(url, browserSessionsEnabled) }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { return it }
            }
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

    private suspend fun preferHigherInstagramVariant(
        sourceUrl: String,
        nativeItems: List<ResolvedMedia>,
        browserSessionsEnabled: Boolean
    ): List<ResolvedMedia> {
        val resolver = ytDlpResolver ?: return nativeItems
        val isSingleVideo = Regex("/(?:reel|tv)/", RegexOption.IGNORE_CASE).containsMatchIn(sourceUrl) &&
            nativeItems.size == 1 && nativeItems.single().isVideo
        if (!isSingleVideo) return nativeItems

        val native = nativeItems.single()
        val nativeQuality = minOf(native.width, native.height)
        if (nativeQuality >= INSTAGRAM_HIGH_QUALITY_EDGE) return nativeItems

        val alternative = runCatching {
            resolveWithYtDlp(sourceUrl, browserSessionsEnabled).singleOrNull()
        }.getOrNull() ?: return nativeItems
        val nativeArea = native.width.toLong() * native.height.toLong()
        val alternativeArea = alternative.width.toLong() * alternative.height.toLong()
        return if (alternativeArea > nativeArea) listOf(alternative) else nativeItems
    }

    private suspend fun resolveWithYtDlp(
        url: String,
        allowBrowserSession: Boolean
    ): List<ResolvedMedia> {
        val resolver = ytDlpResolver ?: throw MediaResolutionException(
            MediaResolutionFailure.RUNTIME_UNAVAILABLE,
            "The yt-dlp runtime is unavailable."
        )
        return withContext(Dispatchers.IO) {
            if (resolver is SessionAwareMediaResolver) {
                resolver.resolve(url, allowBrowserSession)
            } else {
                resolver.resolve(url)
            }
        }
    }

    private suspend fun validateAndSelect(
        sourceUrl: String,
        candidates: List<ResolvedMedia>
    ): List<ResolvedMedia> {
        val uniqueCandidates = candidates.distinctBy(ResolvedMedia::url).take(MAX_CANDIDATES)
        val validated = uniqueCandidates.chunked(MAX_CONCURRENT_INSPECTIONS).flatMap { batch ->
            coroutineScope {
                batch.map { item -> async(Dispatchers.IO) {
                    if (item.backend == MediaBackend.YT_DLP) item else inspectMedia(item)
                } }.awaitAll().filterNotNull()
            }
        }
        if (validated.isEmpty()) {
            throw MediaResolutionException(
                MediaResolutionFailure.UNSUPPORTED_MEDIA,
                "The resolved links did not contain complete downloadable media files."
            )
        }
        return selectBest(sourceUrl, validated)
    }

    private fun selectBest(sourceUrl: String, items: List<ResolvedMedia>): List<ResolvedMedia> {
        val singleVideo = WebLink.isInstagramMediaUrl(sourceUrl) &&
            Regex("/(?:reel|tv)/", RegexOption.IGNORE_CASE).containsMatchIn(sourceUrl)
        if (!singleVideo) return items.distinctBy(ResolvedMedia::url)
        return items.filter(ResolvedMedia::isVideo).maxWithOrNull(
            compareBy<ResolvedMedia> { it.width.toLong() * it.height.toLong() }
                .thenBy { it.fileSize ?: 0L }
        )?.let(::listOf) ?: items.distinctBy(ResolvedMedia::url)
    }

    private companion object {
        const val INSTAGRAM_HIGH_QUALITY_EDGE = 1080
        const val MAX_CANDIDATES = 24
        const val MAX_CONCURRENT_INSPECTIONS = 4
    }
}
