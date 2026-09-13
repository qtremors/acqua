package dev.qtremors.acqua.resolver.web

import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import org.json.JSONArray
import org.json.JSONObject

internal enum class LivePageExtractionFailure {
    SESSION_EXPIRED,
    CONTENT_UNAVAILABLE,
    NO_MEDIA
}

internal sealed interface LivePageExtractionOutcome {
    data object Continue : LivePageExtractionOutcome
    data class Complete(val media: List<ResolvedMedia>) : LivePageExtractionOutcome
    data class Failed(val reason: LivePageExtractionFailure) : LivePageExtractionOutcome
}

internal class LivePageMediaCollector(
    private val maximumAttempts: Int = 30,
    private val requiredStablePasses: Int = 4
) {
    private var attempts = 0
    private var stablePasses = 0
    private var lastItemCount = 0
    private var sawCarouselAdvance = false
    private var expectsVideo = false
    private var videoPoster: String? = null
    private var videoWidth = 0
    private var videoHeight = 0
    private val media = linkedMapOf<String, ResolvedMedia>()
    private val capturedNetworkVideos = linkedSetOf<String>()

    fun reset() {
        attempts = 0
        stablePasses = 0
        lastItemCount = 0
        sawCarouselAdvance = false
        expectsVideo = false
        videoPoster = null
        videoWidth = 0
        videoHeight = 0
        media.clear()
        capturedNetworkVideos.clear()
    }

    fun captureNetworkVideo(url: String) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) return
        capturedNetworkVideos.remove(url)
        capturedNetworkVideos.add(url)
        while (capturedNetworkVideos.size > MAX_CAPTURED_VIDEO_URLS) {
            capturedNetworkVideos.remove(capturedNetworkVideos.first())
        }
    }

    fun consume(payload: JSONObject?, sourceUrl: String): LivePageExtractionOutcome {
        attempts++
        if (payload == null) return continueOrFail()
        if (payload.optBoolean("loginPage")) {
            return LivePageExtractionOutcome.Failed(LivePageExtractionFailure.SESSION_EXPIRED)
        }

        val username = payload.optString("username").takeIf(String::isNotBlank)
        val timestamp = payload.optLong("sourceTimestampMillis").takeIf { it > 0L }
        expectsVideo = expectsVideo || payload.optBoolean("expectsVideo") ||
            payload.optInt("videoElementCount") > 0
        videoPoster = payload.optString("videoPoster").takeIf { it.startsWith("http") } ?: videoPoster
        videoWidth = payload.optInt("videoWidth").takeIf { it > 0 } ?: videoWidth
        videoHeight = payload.optInt("videoHeight").takeIf { it > 0 } ?: videoHeight

        collectPageItems(payload.optJSONArray("items") ?: JSONArray(), username, timestamp, sourceUrl)
        collectNetworkVideos(payload, username, timestamp, sourceUrl)

        val clickedNext = payload.optBoolean("clickedNext")
        sawCarouselAdvance = sawCarouselAdvance || clickedNext
        stablePasses = if (media.size == lastItemCount && !clickedNext) stablePasses + 1 else 0
        lastItemCount = media.size
        val hasExpectedMedia = media.isNotEmpty() && (!expectsVideo || media.values.any(ResolvedMedia::isVideo))

        return when {
            hasExpectedMedia && !clickedNext && stablePasses >= requiredStablePasses ->
                LivePageExtractionOutcome.Complete(normalizedMedia())
            payload.optBoolean("errorPage") && media.isEmpty() ->
                LivePageExtractionOutcome.Failed(LivePageExtractionFailure.CONTENT_UNAVAILABLE)
            attempts >= maximumAttempts && hasExpectedMedia ->
                LivePageExtractionOutcome.Complete(normalizedMedia())
            attempts >= maximumAttempts ->
                LivePageExtractionOutcome.Failed(LivePageExtractionFailure.NO_MEDIA)
            else -> LivePageExtractionOutcome.Continue
        }
    }

    private fun collectPageItems(
        items: JSONArray,
        username: String?,
        timestamp: Long?,
        sourceUrl: String
    ) {
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val url = item.optString("url")
            if (!url.startsWith("http")) continue
            media[url] = ResolvedMedia(
                url = url,
                kind = if (item.optBoolean("isVideo")) MediaKind.VIDEO else MediaKind.IMAGE,
                thumbnailUrl = item.optString("thumbnail").takeIf { it.startsWith("http") },
                width = item.optInt("width").coerceAtLeast(0),
                height = item.optInt("height").coerceAtLeast(0),
                username = username,
                referer = sourceUrl,
                sourceTimestampMillis = timestamp
            )
        }
    }

    private fun collectNetworkVideos(
        payload: JSONObject,
        username: String?,
        timestamp: Long?,
        sourceUrl: String
    ) {
        if (!expectsVideo || media.values.any(ResolvedMedia::isVideo)) return
        val urls = payload.optJSONArray("networkVideoUrls") ?: JSONArray()
        for (index in 0 until urls.length()) {
            val url = urls.optString(index)
            captureNetworkVideo(url)
        }
        capturedNetworkVideos.forEach { url ->
            media[url] = ResolvedMedia(
                url = url,
                kind = MediaKind.VIDEO,
                thumbnailUrl = videoPoster,
                width = videoWidth,
                height = videoHeight,
                username = username,
                referer = sourceUrl,
                sourceTimestampMillis = timestamp
            )
        }
    }

    private fun continueOrFail(): LivePageExtractionOutcome =
        if (attempts >= maximumAttempts) {
            LivePageExtractionOutcome.Failed(LivePageExtractionFailure.NO_MEDIA)
        } else {
            LivePageExtractionOutcome.Continue
        }

    private fun normalizedMedia(): List<ResolvedMedia> {
        val items = media.values.toList()
        val videos = items.filter(ResolvedMedia::isVideo)
        if (videos.isNotEmpty() && !sawCarouselAdvance) {
            val poster = videos.firstNotNullOfOrNull(ResolvedMedia::thumbnailUrl)
                ?: items.firstOrNull { !it.isVideo }?.url
                ?: videoPoster
            return videos.map { video ->
                if (video.thumbnailUrl != null || poster == null) video else video.copy(thumbnailUrl = poster)
            }
        }
        val posterUrls = videos.mapNotNull(ResolvedMedia::thumbnailUrl).toSet()
        return items.filterNot { !it.isVideo && it.url in posterUrls }
    }

    private companion object {
        const val MAX_CAPTURED_VIDEO_URLS = 12
    }
}
