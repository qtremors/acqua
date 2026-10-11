package dev.qtremors.acqua.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

interface MediaResolutionGateway {
    suspend fun resolve(
        input: String,
        browserSessionsEnabled: Boolean,
        forceBrowserResolution: Boolean = false,
        engine: DownloadEngine = DownloadEngine.ACQUA,
        allowBrowserFallback: Boolean = false,
        browserResolver: suspend (url: String, explicitSessionAuthorization: Boolean) -> List<ResolvedMedia>
    ): List<ResolvedMedia>
}

class MediaResolutionService(
    private val sourceResolver: MediaResolver,
    private val ytDlpResolver: MediaResolver? = null,
    private val inspectMedia: (ResolvedMedia) -> ResolvedMedia?
) : MediaResolutionGateway {
    override suspend fun resolve(
        input: String,
        browserSessionsEnabled: Boolean,
        forceBrowserResolution: Boolean,
        engine: DownloadEngine,
        allowBrowserFallback: Boolean,
        browserResolver: suspend (url: String, explicitSessionAuthorization: Boolean) -> List<ResolvedMedia>
    ): List<ResolvedMedia> {
        val url = WebLink.normalize(input)
            ?: throw MediaResolutionException(
                MediaResolutionFailure.INVALID_LINK,
                "Enter a valid HTTP or HTTPS link."
            )
        if (engine == DownloadEngine.YT_DLP) {
            return try {
                validateAndSelect(url, resolveWithYtDlp(url, false))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!browserSessionsEnabled) throw error
                try {
                    validateAndSelect(url, resolveWithYtDlp(url, true))
                } catch (error: CancellationException) {
                    throw error
                } catch (sessionFailure: Exception) {
                    sessionFailure.addSuppressed(error)
                    throw sessionFailure
                }
            }
        }
        if (WebLink.isYouTubeUrl(url)) {
            throw MediaResolutionException(
                MediaResolutionFailure.ENGINE_REQUIRED,
                "YouTube links require the yt-dlp engine."
            )
        }
        if (forceBrowserResolution) {
            requireBrowserSessions(browserSessionsEnabled)
            return validateAndSelect(url, browserResolver(url, true))
        }
        if (!WebLink.isInstagramMediaUrl(url)) {
            if (!allowBrowserFallback) {
                throw MediaResolutionException(
                    MediaResolutionFailure.SESSION_REQUIRED,
                    "This page needs browser extraction. Use the browser option to continue."
                )
            }
            requireBrowserSessions(browserSessionsEnabled)
            return validateAndSelect(url, browserResolver(url, true))
        }

        val sourceFailure = try {
            val candidates = withContext(Dispatchers.IO) {
                val candidates = if (sourceResolver is SessionAwareMediaResolver) {
                    sourceResolver.resolve(url, browserSessionsEnabled)
                } else {
                    sourceResolver.resolve(url)
                }
                candidates.map { it.copy(referer = it.referer ?: url) }
            }
            return validateAndSelect(url, candidates)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            error
        }

        if (!allowBrowserFallback) throw sourceFailure
        requireBrowserSessions(browserSessionsEnabled)
        return try {
            validateAndSelect(url, browserResolver(url, true))
        } catch (error: CancellationException) {
            throw error
        } catch (browserFailure: Exception) {
            browserFailure.addSuppressed(sourceFailure)
            throw browserFailure
        }
    }

    private fun requireBrowserSessions(enabled: Boolean) {
        if (!enabled) throw MediaResolutionException(
            MediaResolutionFailure.SESSION_REQUIRED,
            "Enable browser sessions to use cookies for this page."
        )
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
        val uniqueCandidates = deduplicate(candidates).take(MAX_CANDIDATES)
        val validated = uniqueCandidates.chunked(MAX_CONCURRENT_INSPECTIONS).flatMap { batch ->
            coroutineScope {
                batch.map { item ->
                    async(Dispatchers.IO) {
                        if (item.backend == MediaBackend.YT_DLP) item else inspectMedia(item)
                    }
                }.awaitAll().filterNotNull()
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
        if (!singleVideo) return deduplicate(items)
        return items.filter(ResolvedMedia::isVideo).maxWithOrNull(
            compareBy<ResolvedMedia> { it.width.toLong() * it.height.toLong() }
                .thenBy { it.fileSize ?: 0L }
        )?.let(::listOf) ?: deduplicate(items)
    }

    private fun deduplicate(items: List<ResolvedMedia>): List<ResolvedMedia> {
        val unique = linkedMapOf<String, ResolvedMedia>()
        items.forEach { candidate ->
            val identity = WebLink.mediaAssetIdentity(candidate.url)
            val existing = unique[identity]
            val candidateArea = candidate.width.toLong() * candidate.height.toLong()
            val existingArea = existing?.let { it.width.toLong() * it.height.toLong() } ?: -1L
            if (existing == null || candidateArea >= existingArea) unique[identity] = candidate
        }
        return unique.values.toList()
    }

    private companion object {
        const val MAX_CANDIDATES = 24
        const val MAX_CONCURRENT_INSPECTIONS = 4
    }
}
