package dev.qtremors.acqua.downloader

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.CookieManager
import dev.qtremors.acqua.data.network.HttpMediaClient
import dev.qtremors.acqua.data.network.MediaContentDetector
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.WebLink
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class DownloadThumbnail(
    context: Context,
    private val http: HttpMediaClient,
    private val embeddedArtwork: (Uri) -> ByteArray? = { uri -> readArtwork(context, uri) }
) {
    private val appContext = context.applicationContext
    private val requests = DownloadRequestStore(appContext)

    suspend fun save(
        request: DownloadWorkRequest,
        result: DownloadExecutionResult,
        taskKey: String,
        browserSessions: Boolean,
        publish: suspend (File) -> Uri
    ): DownloadExecutionResult = withContext(Dispatchers.IO) {
        if (!request.options.downloadThumbnail || result.mediaKind == MediaKind.IMAGE) return@withContext result
        val publication = request.publicationId ?: taskKey
        val existing = requests.published(publication, "thumbnail")
        if (existing != null) return@withContext result.copy(artifactUris = result.artifactUris + existing)
        val directory = YtDlpTemporaryFiles.prepareTaskDirectory(appContext, "thumbnail-$taskKey")
        try {
            val source = File(directory, "source.image")
            val url = request.media.thumbnailUrl?.let(WebLink::normalize)
            if (url != null) {
                val cookies = thumbnailCookies(request.media, url, browserSessions)
                val image = ResolvedMedia(url, MediaKind.IMAGE, referer = request.media.referer, requestCookies = cookies, requestUserAgent = request.media.requestUserAgent)
                source.outputStream().use { http.downloadToStreamCancellable(image, it, cookies) }
            } else if (result.mediaKind == MediaKind.AUDIO) {
                val bytes = embeddedArtwork(result.uri) ?: return@withContext result
                source.writeBytes(bytes)
            } else return@withContext result
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val format = source.inputStream().use { input ->
                val prefix = ByteArray(64)
                val count = input.read(prefix).coerceAtLeast(0)
                MediaContentDetector.detect(null, prefix.copyOf(count), expectedVideo = false)
            }?.takeIf { it.kind == MediaKind.IMAGE } ?: error("The thumbnail is not a supported image.")
            val image = File(directory, "thumbnail.${format.fileExtension}")
            check(source.renameTo(image)) { "Could not prepare the thumbnail image." }
            val uri = publish(image)
            requests.recordPublished(publication, "thumbnail", uri)
            result.copy(artifactUris = result.artifactUris + uri)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun thumbnailCookies(media: ResolvedMedia, url: String, browserSessions: Boolean): String? {
        if (media.authorizedSession != null) return null
        if (!browserSessions || !media.explicitBrowserSessionAuthorized) return null
        return if (WebLink.host(url) == WebLink.host(media.url)) media.requestCookies
            ?: CookieManager.getInstance().getCookie(url) else CookieManager.getInstance().getCookie(url)
    }
}

private fun readArtwork(context: Context, uri: Uri): ByteArray? = runCatching {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        retriever.embeddedPicture
    } finally {
        retriever.release()
    }
}.getOrNull()
