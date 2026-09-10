package dev.qtremors.acqua.data.network

import android.graphics.BitmapFactory
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.InputStream
import java.io.OutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

class MediaDownloader(
    private val client: OkHttpClient = defaultClient()
) {
    private data class ScopedRequestCookies(val host: String, val value: String)

    private val userAgent = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/142.0.0.0 Mobile Safari/537.36"
    private val signatureByteCount = 65_536

    private fun buildRequest(
        url: String,
        referer: String? = null,
        requestCookies: String? = null
    ) = Request.Builder()
        .url(url)
        .header("User-Agent", userAgent)
        .apply {
            referer?.takeIf { it.startsWith("http") }?.let { header("Referer", it) }
            requestCookies?.takeIf { it.isNotBlank() }?.let { cookies ->
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

        return client.newCall(buildRequest(item.url, item.referer, requestCookies)).execute().use { response ->
            if (response.code == 206) {
                error("The media server returned only part of the file (HTTP 206).")
            }
            if (!response.isSuccessful) error("Media download request failed (HTTP ${response.code}).")

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
                val read = stream.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                bytesWritten += read
                onProgress(bytesWritten, expectedLength)
            }
            if (expectedLength >= 0L && bytesWritten != expectedLength) {
                error("The media download ended before the complete file was received.")
            }
            bytesWritten
        }
    }

    fun validateAndResolveMetadata(item: ResolvedMedia): ResolvedMedia? {
        if (hasEmbeddedByteRange(item.url)) return null
        return runCatching {
            val request = buildRequest(item.url, item.referer, item.requestCookies).newBuilder()
                .header("Range", "bytes=0-65535")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code != 206 && !response.isSuccessful) return@use null
                val totalSize = response.header("Content-Range")
                    ?.substringAfterLast('/')
                    ?.trim()
                    ?.toLongOrNull()
                    ?: response.header("Content-Length")?.toLongOrNull()
                    ?: 0L
                val bytes = response.body?.byteStream()?.readPrefix(signatureByteCount)
                    ?: return@use null
                val format = MediaContentDetector.detect(
                    response.header("Content-Type"),
                    bytes,
                    item.isVideo
                ) ?: return@use null

                if (format.kind == MediaKind.VIDEO) {
                    return@use item.copy(
                        kind = MediaKind.VIDEO,
                        thumbnailUrl = item.thumbnailUrl?.takeIf { it != item.url },
                        fileSize = totalSize.takeIf { it > 0L } ?: item.fileSize,
                        mimeType = format.mimeType,
                        fileExtension = format.fileExtension
                    )
                }

                if (format.kind == MediaKind.AUDIO) {
                    return@use item.copy(
                        kind = MediaKind.AUDIO,
                        thumbnailUrl = item.thumbnailUrl?.takeIf { it != item.url },
                        fileSize = totalSize.takeIf { it > 0L } ?: item.fileSize,
                        mimeType = format.mimeType,
                        fileExtension = format.fileExtension
                    )
                }

                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                item.copy(
                    kind = MediaKind.IMAGE,
                    thumbnailUrl = item.url,
                    width = options.outWidth.takeIf { it > 0 } ?: item.width,
                    height = options.outHeight.takeIf { it > 0 } ?: item.height,
                    fileSize = totalSize.takeIf { it > 0L } ?: item.fileSize,
                    mimeType = format.mimeType,
                    fileExtension = format.fileExtension
                )
            }
        }.getOrNull()
    }

    fun fetchBytes(item: ResolvedMedia): ByteArray =
        client.newCall(buildRequest(item.url, item.referer, item.requestCookies)).execute().use { response ->
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
            client.newCall(buildRequest(item.url, item.referer, item.requestCookies)).execute().use { response ->
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
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
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
