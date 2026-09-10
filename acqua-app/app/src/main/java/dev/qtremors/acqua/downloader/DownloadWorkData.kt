package dev.qtremors.acqua.downloader

import androidx.work.Data
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia

/**
 * Stable serialization boundary between the UI process and [YtDlpDownloadWorker].
 *
 * WorkManager can recreate a worker long after the screen that scheduled it has
 * disappeared. Keeping all request encoding and defensive decoding here prevents
 * the coordinator and worker from silently drifting apart when media fields or
 * download options evolve.
 */
internal object DownloadWorkData {
    const val KEY_MEDIA_URL = "media_url"
    const val KEY_BACKEND = "backend"
    const val KEY_SOURCE_URL = "source_url"
    const val KEY_CONTENT_TYPE = "content_type"
    const val KEY_AUDIO_FORMAT = "audio_format"
    const val KEY_MAXIMUM_VIDEO_QUALITY = "maximum_video_quality"
    const val KEY_EMBED_METADATA = "embed_metadata"
    const val KEY_EMBED_THUMBNAIL = "embed_thumbnail"
    const val KEY_BROWSER_SESSION_AUTHORIZED = "browser_session_authorized"
    const val KEY_IS_VIDEO = "is_video"
    const val KEY_MEDIA_KIND = "media_kind"
    const val KEY_ITEM_INDEX = "item_index"
    const val KEY_MEDIA_WIDTH = "media_width"
    const val KEY_MEDIA_HEIGHT = "media_height"
    const val KEY_SOURCE_TIMESTAMP_MILLIS = "source_timestamp_millis"
    const val KEY_OUTPUT_WIDTH = "output_width"
    const val KEY_OUTPUT_HEIGHT = "output_height"
    const val KEY_THUMBNAIL_URL = "thumbnail_url"
    const val KEY_USERNAME = "username"
    const val KEY_TITLE = "title"
    const val KEY_ARTIST = "artist"
    const val KEY_ALBUM = "album"
    const val KEY_REFERER = "referer"
    const val KEY_MIME_TYPE = "mime_type"
    const val KEY_FILE_EXTENSION = "file_extension"
    const val KEY_FILE_SIZE = "file_size"
    const val KEY_PROGRESS = "progress"
    const val KEY_ETA_SECONDS = "eta_seconds"
    const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
    const val KEY_TOTAL_BYTES = "total_bytes"
    const val KEY_ERROR = "error"

    fun processedRequest(
        media: ResolvedMedia,
        outputMedia: ResolvedMedia,
        options: YtDlpDownloadOptions,
        sourceUrl: String
    ): Data = commonRequest(media, sourceUrl)
        .putString(KEY_CONTENT_TYPE, options.contentType.name)
        .putString(KEY_AUDIO_FORMAT, options.audioFormat.name)
        .putInt(KEY_MAXIMUM_VIDEO_QUALITY, options.maximumVideoHeight)
        .putBoolean(KEY_EMBED_METADATA, options.embedMetadata)
        .putBoolean(KEY_EMBED_THUMBNAIL, options.embedThumbnail)
        .putInt(KEY_OUTPUT_WIDTH, outputMedia.width)
        .putInt(KEY_OUTPUT_HEIGHT, outputMedia.height)
        .build()

    fun directRequest(
        media: ResolvedMedia,
        itemIndex: Int,
        sourceUrl: String
    ): Data = commonRequest(media, sourceUrl)
        .putInt(KEY_ITEM_INDEX, itemIndex.coerceAtLeast(0))
        .apply {
            media.fileSize?.takeIf { it > 0L }?.let { putLong(KEY_FILE_SIZE, it) }
            media.referer?.takeIf(String::isNotBlank)?.let { putString(KEY_REFERER, it) }
            media.mimeType?.takeIf(String::isNotBlank)?.let { putString(KEY_MIME_TYPE, it) }
            media.fileExtension?.takeIf(String::isNotBlank)?.let {
                putString(KEY_FILE_EXTENSION, it)
            }
        }
        .build()

    fun decode(data: Data): DownloadWorkRequest? {
        val mediaUrl = data.getString(KEY_MEDIA_URL)?.takeIf(String::isNotBlank) ?: return null
        val sourceUrl = data.getString(KEY_SOURCE_URL)?.takeIf(String::isNotBlank) ?: return null
        val backend = enumValueOrDefault(
            data.getString(KEY_BACKEND),
            MediaBackend.YT_DLP
        )
        val contentType = enumValueOrDefault(
            data.getString(KEY_CONTENT_TYPE),
            DownloadContentType.VIDEO
        )
        val audioFormat = enumValueOrDefault(
            data.getString(KEY_AUDIO_FORMAT),
            AudioOutputFormat.ORIGINAL
        )
        val kind = data.getString(KEY_MEDIA_KIND)?.let { name ->
            runCatching { MediaKind.valueOf(name) }.getOrNull()
        } ?: if (data.getBoolean(KEY_IS_VIDEO, true)) MediaKind.VIDEO else MediaKind.IMAGE
        val media = ResolvedMedia(
            url = mediaUrl,
            kind = kind,
            thumbnailUrl = data.nonBlankString(KEY_THUMBNAIL_URL),
            width = data.nonNegativeInt(KEY_MEDIA_WIDTH),
            height = data.nonNegativeInt(KEY_MEDIA_HEIGHT),
            username = data.nonBlankString(KEY_USERNAME),
            explicitBrowserSessionAuthorized = data.getBoolean(KEY_BROWSER_SESSION_AUTHORIZED, false),
            referer = data.nonBlankString(KEY_REFERER),
            mimeType = data.nonBlankString(KEY_MIME_TYPE),
            fileExtension = data.nonBlankString(KEY_FILE_EXTENSION),
            fileSize = data.positiveLong(KEY_FILE_SIZE),
            backend = backend,
            title = data.nonBlankString(KEY_TITLE),
            artist = data.nonBlankString(KEY_ARTIST),
            album = data.nonBlankString(KEY_ALBUM),
            sourceTimestampMillis = data.positiveLong(KEY_SOURCE_TIMESTAMP_MILLIS)
        )
        val outputWidth = data.getInt(KEY_OUTPUT_WIDTH, media.width).coerceAtLeast(0)
        val outputHeight = data.getInt(KEY_OUTPUT_HEIGHT, media.height).coerceAtLeast(0)
        return DownloadWorkRequest(
            media = media,
            outputMedia = media.copy(width = outputWidth, height = outputHeight),
            sourceUrl = sourceUrl,
            itemIndex = data.getInt(KEY_ITEM_INDEX, 0).coerceAtLeast(0),
            options = YtDlpDownloadOptions(
                contentType = contentType,
                maximumVideoHeight = data.getInt(KEY_MAXIMUM_VIDEO_QUALITY, 0).coerceAtLeast(0),
                audioFormat = audioFormat,
                embedMetadata = data.getBoolean(KEY_EMBED_METADATA, true),
                embedThumbnail = data.getBoolean(KEY_EMBED_THUMBNAIL, true)
            )
        )
    }

    fun progress(
        progress: Float,
        etaSeconds: Long = 0L,
        downloadedBytes: Long = 0L,
        totalBytes: Long = 0L
    ): Data = Data.Builder()
        .putFloat(KEY_PROGRESS, progress.coerceIn(0f, 100f))
        .putLong(KEY_ETA_SECONDS, etaSeconds.coerceAtLeast(0L))
        .putLong(KEY_DOWNLOADED_BYTES, downloadedBytes.coerceAtLeast(0L))
        .putLong(KEY_TOTAL_BYTES, totalBytes.coerceAtLeast(0L))
        .build()

    fun error(message: String?): Data = Data.Builder()
        .putString(KEY_ERROR, message?.takeIf(String::isNotBlank) ?: DEFAULT_ERROR)
        .build()

    private fun commonRequest(media: ResolvedMedia, sourceUrl: String): Data.Builder =
        Data.Builder()
            .putString(KEY_MEDIA_URL, media.url)
            .putString(KEY_BACKEND, media.backend.name)
            .putString(KEY_SOURCE_URL, sourceUrl)
            .putString(KEY_MEDIA_KIND, media.kind.name)
            .putBoolean(KEY_IS_VIDEO, media.isVideo)
            .putBoolean(KEY_BROWSER_SESSION_AUTHORIZED, media.explicitBrowserSessionAuthorized)
            .putInt(KEY_MEDIA_WIDTH, media.width.coerceAtLeast(0))
            .putInt(KEY_MEDIA_HEIGHT, media.height.coerceAtLeast(0))
            .apply {
                media.sourceTimestampMillis?.takeIf { it > 0L }?.let {
                    putLong(KEY_SOURCE_TIMESTAMP_MILLIS, it)
                }
                media.thumbnailUrl?.takeIf(String::isNotBlank)?.let {
                    putString(KEY_THUMBNAIL_URL, it)
                }
                media.username?.takeIf(String::isNotBlank)?.let { putString(KEY_USERNAME, it) }
                media.title?.takeIf(String::isNotBlank)?.let { putString(KEY_TITLE, it) }
                media.artist?.takeIf(String::isNotBlank)?.let { putString(KEY_ARTIST, it) }
                media.album?.takeIf(String::isNotBlank)?.let { putString(KEY_ALBUM, it) }
            }

    private fun Data.nonBlankString(key: String): String? =
        getString(key)?.takeIf(String::isNotBlank)

    private fun Data.nonNegativeInt(key: String): Int = getInt(key, 0).coerceAtLeast(0)

    private fun Data.positiveLong(key: String): Long? = getLong(key, 0L).takeIf { it > 0L }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
        value?.let { candidate -> enumValues<T>().firstOrNull { it.name == candidate } } ?: default

    private const val DEFAULT_ERROR = "The background download failed."
}

internal data class DownloadWorkRequest(
    val media: ResolvedMedia,
    val outputMedia: ResolvedMedia,
    val sourceUrl: String,
    val itemIndex: Int,
    val options: YtDlpDownloadOptions
)
