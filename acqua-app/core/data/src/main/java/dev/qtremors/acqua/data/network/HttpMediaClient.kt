package dev.qtremors.acqua.data.network

import android.graphics.BitmapFactory
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.MediaResolutionException
import dev.qtremors.acqua.domain.MediaResolutionFailure
import dev.qtremors.acqua.domain.ResolvedMedia
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class MediaHttpException(val statusCode: Int) : IOException("Media download request failed (HTTP $statusCode).")

class HttpMediaClient(
    private val client: OkHttpClient = defaultClient(),
    private val session: dev.qtremors.acqua.data.session.CookieProfile? = null,
    private val networkOptions: () -> TransferNetworkOptions = { TransferNetworkOptions() }
) {
    private data class ScopedRequestCookies(val host: String, val value: String)

    private val transferClient = client.newBuilder().callTimeout(0, TimeUnit.MILLISECONDS).build()
    private val publicProbeClient = client.newBuilder().cookieJar(CookieJar.NO_COOKIES).build()

    fun withNetworkOptions(options: TransferNetworkOptions): HttpMediaClient {
        options.validated()
        return HttpMediaClient(client, session) { options }
    }

    fun withSession(snapshot: String?): HttpMediaClient = HttpMediaClient(client, snapshot?.let(dev.qtremors.acqua.data.session.CookieProfile::parse), networkOptions)

    private fun configured(base: OkHttpClient = client): OkHttpClient {
        val configured = networkOptions().configure(base)
        val profile = session ?: return configured
        return configured.newBuilder().cookieJar(object : CookieJar {
            override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) = Unit
            override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> = profile.matching(url)
        }).build()
    }

    private val userAgent = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/142.0.0.0 Mobile Safari/537.36"
    private val signatureByteCount = 65_536

    private fun buildRequest(
        url: String,
        referer: String? = null,
        requestCookies: String? = null,
        requestUserAgent: String? = null
    ) = Request.Builder()
        .url(url)
        .header("User-Agent", session?.userAgent ?: requestUserAgent?.takeIf { it.isNotBlank() && it.length <= 1024 && it.none(Char::isISOControl) } ?: userAgent)
        .apply {
            referer?.takeIf { it.startsWith("http") }?.let { header("Referer", it) }
            requestCookies?.takeIf { session == null && it.isNotBlank() }?.let { cookies ->
                url.toHttpUrlOrNull()?.host?.let { host ->
                    tag(ScopedRequestCookies::class.java, ScopedRequestCookies(host, cookies))
                }
            }
        }
        .get()
        .build()

    fun downloadToStream(
        item: ResolvedMedia,
        output: OutputStream,
        requestCookies: String? = item.requestCookies,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Long {
        if (hasEmbeddedByteRange(item.url)) {
            error("A partial browser media segment cannot be saved as a complete file.")
        }

        return configured(transferClient).newCall(buildRequest(item.url, item.referer, requestCookies, item.requestUserAgent)).execute().use { response ->
            copyResponse(item, response, output, onProgress) { false }
        }
    }

    suspend fun downloadToStreamCancellable(
        item: ResolvedMedia,
        output: OutputStream,
        requestCookies: String? = item.requestCookies,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Long {
        if (hasEmbeddedByteRange(item.url)) {
            error("A partial browser media segment cannot be saved as a complete file.")
        }
        return suspendCancellableCoroutine { continuation ->
            val call = configured(transferClient).newCall(buildRequest(item.url, item.referer, requestCookies, item.requestUserAgent))
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val bytes = response.use {
                            copyResponse(item, it, output, onProgress) { !continuation.isActive }
                        }
                        if (continuation.isActive) continuation.resume(bytes)
                    } catch (cancelled: CancellationException) {
                        if (continuation.isActive) continuation.cancel(cancelled)
                    } catch (error: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            })
        }
    }

    private fun copyResponse(
        item: ResolvedMedia,
        response: Response,
        output: OutputStream,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit,
        isCancelled: () -> Boolean
    ): Long {
            if (isCancelled()) throw CancellationException("Media download cancelled")
            if (response.code == 206) {
                error("The media server returned only part of the file (HTTP 206).")
            }
            if (!response.isSuccessful) throw MediaHttpException(response.code)

            val body = response.body ?: error("The media server returned an empty response body.")
            val expectedLength = body.contentLength()
            val stream = body.byteStream()
            val prefix = stream.readPrefix(signatureByteCount)
            val format = MediaContentDetector.detect(response.header("Content-Type"), prefix, item.isVideo)
                ?: error("The media server returned an unsupported or incomplete file.")

            if (item.isVideo && format.kind != MediaKind.VIDEO) {
                error("The video URL returned non-video media instead of a complete video.")
            } else if (item.isAudio && format.kind != MediaKind.AUDIO) {
                error("The audio URL returned non-audio media instead of an audio file.")
            } else if (!item.isVideo && !item.isAudio && format.kind != MediaKind.IMAGE) {
                error("The image URL returned non-image media instead of an image.")
            }

            output.write(prefix)
            var bytesWritten = prefix.size.toLong()
            onProgress(bytesWritten, expectedLength)
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                if (isCancelled()) throw CancellationException("Media download cancelled")
                val read = stream.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                bytesWritten += read
                onProgress(bytesWritten, expectedLength)
            }
            if (expectedLength >= 0L && bytesWritten != expectedLength) {
                error("The media download ended before the complete file was received.")
            }
            return bytesWritten
    }

    fun resolvePublicMedia(url: String): ResolvedMedia? = if (session == null) probeMedia(url) else HttpMediaClient(client, networkOptions = networkOptions).probeMedia(url)

    fun resolveSessionMedia(url: String, snapshot: String): ResolvedMedia? = withSession(snapshot).probeMedia(url)?.copy(
        authorizedSession = snapshot, requestUserAgent = dev.qtremors.acqua.data.session.CookieProfile.parse(snapshot).userAgent,
        explicitBrowserSessionAuthorized = true)

    private fun probeMedia(url: String): ResolvedMedia? {
        if (hasEmbeddedByteRange(url)) throw MediaResolutionException(
            MediaResolutionFailure.UNSUPPORTED_MEDIA, "A partial media segment cannot be saved as a complete file."
        )
        val request = buildRequest(url).newBuilder().header("Range", "bytes=0-65535").build()
        try {
            return configured(publicProbeClient).newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) throw MediaResolutionException(
                    MediaResolutionFailure.SESSION_REQUIRED,
                    "This link requires authentication or does not permit public access. Try browser extraction with sessions enabled."
                )
                if (response.code >= 500) throw MediaResolutionException(
                    MediaResolutionFailure.NETWORK, "The media server could not complete the request (HTTP ${response.code})."
                )
                if (!response.isSuccessful) return@use null
                val body = response.body ?: return@use null
                val bytes = body.byteStream().readPrefix(signatureByteCount)
                val size = publicProbeSize(response, bytes.size) ?: return@use null
                val contentType = response.header("Content-Type").orEmpty()
                if (contentType.startsWith("text/", true) || contentType.contains("json", true)) return@use null
                if (hasEmbeddedByteRange(response.request.url.toString())) return@use null
                val format = MediaContentDetector.detect(contentType, bytes, false)
                    ?: MediaContentDetector.detect(contentType, bytes, true)
                    ?: return@use null
                val item = ResolvedMedia(url, format.kind)
                metadata(item, format, bytes, size.takeIf { it > 0 })
            }
        } catch (error: IOException) {
            throw MediaResolutionException(MediaResolutionFailure.NETWORK, "Could not inspect this media link. Check your connection and try again.", error)
        }
    }

    private fun publicProbeSize(response: Response, bytesRead: Int): Long? {
        val contentLength = response.body?.contentLength() ?: -1L
        if (response.code == 206) {
            val range = Regex("bytes 0-(\\d+)/(\\d+)").matchEntire(response.header("Content-Range").orEmpty())
                ?: return null
            val end = range.groupValues[1].toLongOrNull() ?: return null
            val total = range.groupValues[2].toLongOrNull()?.takeIf { it > 0 } ?: return null
            val expected = minOf(total, signatureByteCount.toLong())
            if (end != expected - 1 || bytesRead.toLong() != expected) return null
            if (contentLength >= 0 && contentLength != expected) return null
            return total
        }
        if (response.code != 200 || response.header("Content-Range") != null) return null
        if (contentLength >= 0 && bytesRead.toLong() != minOf(contentLength, signatureByteCount.toLong())) return null
        return contentLength
    }

    fun validateAndResolveMetadata(item: ResolvedMedia): ResolvedMedia? {
        if (item.authorizedSession != null && session == null) return withSession(item.authorizedSession).validateAndResolveMetadata(item)
        if (hasEmbeddedByteRange(item.url)) return null
        return runCatching {
            val request = buildRequest(item.url, item.referer, item.requestCookies, item.requestUserAgent).newBuilder()
                .header("Range", "bytes=0-65535")
                .build()

            configured().newCall(request).execute().use { response ->
                if (response.code != 206 && !response.isSuccessful) return@use null
                val totalSize = resolvedContentLength(
                    statusCode = response.code,
                    contentRange = response.header("Content-Range"),
                    contentLength = response.body?.contentLength() ?: -1L
                ) ?: 0L
                val bytes = response.body?.byteStream()?.readPrefix(signatureByteCount)
                    ?: return@use null
                val format = MediaContentDetector.detect(
                    response.header("Content-Type"),
                    bytes,
                    item.isVideo
                ) ?: return@use null

                metadata(item, format, bytes, totalSize.takeIf { it > 0L })
            }
        }.getOrNull()
    }

    private fun metadata(item: ResolvedMedia, format: DetectedMediaFormat, bytes: ByteArray, size: Long?): ResolvedMedia {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (format.kind == MediaKind.IMAGE) BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return item.copy(
            kind = format.kind,
            thumbnailUrl = if (format.kind == MediaKind.IMAGE) item.url else item.thumbnailUrl?.takeIf { it != item.url },
            width = options.outWidth.takeIf { it > 0 } ?: item.width,
            height = options.outHeight.takeIf { it > 0 } ?: item.height,
            fileSize = size ?: item.fileSize,
            mimeType = format.mimeType,
            fileExtension = format.fileExtension
        )
    }

    fun fetchBytes(item: ResolvedMedia): ByteArray =
        configured().newCall(buildRequest(item.url, item.referer, item.requestCookies, item.requestUserAgent)).execute().use { response ->
            if (!response.isSuccessful) error("Failed to fetch media preview (HTTP ${response.code}).")
            val body = response.body ?: error("The media preview response was empty.")
            if (body.contentLength() > MAX_PREVIEW_BYTES) {
                error("The media preview is too large to load safely.")
            }
            body.byteStream().readLimited(MAX_PREVIEW_BYTES)
        }

    fun fetchPreviewToFile(
        item: ResolvedMedia,
        target: File,
        maxBytes: Long = DEFAULT_CACHED_PREVIEW_BYTES
    ): File {
        require(maxBytes > 0L) { "The preview size limit must be positive." }
        if (target.isFile && target.length() in 1..maxBytes) return target
        if (target.exists()) check(target.delete()) { "Could not replace the invalid cached preview." }
        val directory = target.parentFile ?: error("The preview cache target has no parent directory.")
        check(directory.mkdirs() || directory.isDirectory) { "Could not prepare the preview cache." }
        val temporary = File(directory, "${target.name}.${UUID.randomUUID()}.tmp")
        try {
            configured().newCall(buildRequest(item.url, item.referer, item.requestCookies, item.requestUserAgent)).execute().use { response ->
                if (!response.isSuccessful) error("Failed to fetch media preview (HTTP ${response.code}).")
                val body = response.body ?: error("The media preview response was empty.")
                if (body.contentLength() > maxBytes) error("The media preview is too large to cache safely.")
                body.byteStream().use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            total += read
                            if (total > maxBytes) error("The media preview is too large to cache safely.")
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
            check(temporary.length() > 0L) { "The media preview response was empty." }
            if (!temporary.renameTo(target)) {
                if (target.isFile) temporary.delete()
                else error("Could not commit the cached media preview.")
            }
            return target
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    private fun hasEmbeddedByteRange(url: String): Boolean =
        url.contains("bytestart=", ignoreCase = true) ||
            url.contains("byteend=", ignoreCase = true)

    private fun InputStream.readPrefix(maxBytes: Int): ByteArray {
        val buffer = ByteArray(maxBytes)
        var total = 0
        while (total < maxBytes) {
            val read = read(buffer, total, maxBytes - total)
            if (read <= 0) break
            total += read
        }
        return buffer.copyOf(total)
    }

    private fun InputStream.readLimited(maxBytes: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read <= 0) break
            total += read
            if (total > maxBytes) error("The media preview is too large to load safely.")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private companion object {
        const val MAX_PREVIEW_BYTES = 16L * 1024L * 1024L
        const val DEFAULT_CACHED_PREVIEW_BYTES = 8L * 1024L * 1024L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.MINUTES)
            .followRedirects(true)
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val scoped = request.tag(ScopedRequestCookies::class.java)
                    ?: return@addNetworkInterceptor chain.proceed(request)
                val safeRequest = request.newBuilder().apply {
                    if (request.url.host.equals(scoped.host, ignoreCase = true)) {
                        header("Cookie", scoped.value)
                    } else {
                        removeHeader("Cookie")
                    }
                }.build()
                chain.proceed(safeRequest)
            }
            .build()
    }
}

internal fun resolvedContentLength(
    statusCode: Int,
    contentRange: String?,
    contentLength: Long
): Long? {
    val rangeTotalSize = contentRange
        ?.substringAfterLast('/')
        ?.trim()
        ?.toLongOrNull()
        ?.takeIf { it > 0L }
    return rangeTotalSize ?: contentLength.takeIf { statusCode != 206 && it > 0L }
}
