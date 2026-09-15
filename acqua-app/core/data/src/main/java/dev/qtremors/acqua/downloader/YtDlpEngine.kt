package dev.qtremors.acqua.downloader

import android.content.Context
import android.webkit.CookieManager
import com.yausername.youtubedl_android.YoutubeDLRequest
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaFormatOption
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.WebLink
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

fun interface YtDlpCancellation {
    fun cancelAll()
}

class YtDlpEngine(context: Context) : YtDlpCancellation {
    private val appContext = context.applicationContext
    private val activeProcesses = ConcurrentHashMap.newKeySet<String>()

    fun inspect(url: String, allowBrowserSession: Boolean = false): ResolvedMedia {
        val processId = "inspect-${UUID.randomUUID()}"
        val cookieFile = createCookieFile(url, allowBrowserSession)
        val request = YoutubeDLRequest(url).apply {
            addOption("--dump-single-json")
            addOption("--skip-download")
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--socket-timeout", 15)
            cookieFile?.let { addOption("--cookies", it.absolutePath) }
        }
        return try {
            activeProcesses += processId
            val response = YtDlpRuntime.execute(appContext, request, processId)
            if (response.exitCode != 0) error(readableError(response.err))
            parseInfo(response.out, url, allowBrowserSession)
        } finally {
            activeProcesses -= processId
            cookieFile?.delete()
        }
    }

    fun download(
        media: ResolvedMedia,
        options: YtDlpDownloadOptions,
        onProgress: (YtDlpProgress) -> Unit,
        taskKey: String? = null
    ): File {
        val processId = "download-${taskKey ?: UUID.randomUUID()}"
        val taskDirectory = YtDlpTemporaryFiles.prepareTaskDirectory(appContext, processId)
        val cookieFile = createCookieFile(media.url, media.explicitBrowserSessionAuthorized)
        val request = YoutubeDLRequest(media.url).apply {
            addOption("--no-playlist")
            addOption("--newline")
            addOption("--no-mtime")
            addOption("--trim-filenames", 180)
            addOption("--socket-timeout", 20)
            addOption("-P", taskDirectory.absolutePath)
            addOption("-o", "%(title).180B [%(id)s].%(ext)s")
            cookieFile?.let { addOption("--cookies", it.absolutePath) }

            if (options.contentType == DownloadContentType.AUDIO) {
                addOption("-f", YtDlpFormatSelector.AUDIO)
                addOption("-x")
                when (options.audioFormat) {
                    AudioOutputFormat.ORIGINAL -> addOption("--audio-format", "best")
                    AudioOutputFormat.M4A -> addOption("--audio-format", "m4a")
                    AudioOutputFormat.MP3 -> addOption("--audio-format", "mp3")
                }
            } else {
                addOption(
                    "-f",
                    YtDlpFormatSelector.video(
                        options.maximumVideoHeight,
                        portrait = media.height > media.width && media.width > 0
                    )
                )
            }

            if (options.embedMetadata) {
                addOption("--parse-metadata", "%(release_year,upload_date)s:%(meta_date)s")
                if (options.contentType == DownloadContentType.AUDIO) {
                    addOption("--parse-metadata", "%(album,playlist,title)s:%(meta_album)s")
                    addOption("--parse-metadata", "%(track_number,playlist_index)d:%(meta_track)s")
                }
                addOption("--embed-metadata")
                addOption("--no-embed-info-json")
                if (options.contentType == DownloadContentType.VIDEO) addOption("--embed-chapters")
            }
            if (options.embedThumbnail && options.contentType == DownloadContentType.AUDIO) {
                addOption("--embed-thumbnail")
                addOption("--convert-thumbnails", "jpg")
            }
        }

        return try {
            activeProcesses += processId
            val response = YtDlpRuntime.execute(appContext, request, processId) { percent, eta, line ->
                onProgress(YtDlpProgress.fromCallback(percent, eta, line))
            }
            if (response.exitCode != 0) error(readableError(response.err))
            taskDirectory.walkTopDown()
                .filter(File::isFile)
                .filterNot { it.extension.lowercase() in SIDECAR_EXTENSIONS }
                .maxByOrNull(File::length)
                ?.takeIf { it.length() > 0L }
                ?: error("yt-dlp completed without producing a media file.")
        } catch (error: Throwable) {
            taskDirectory.deleteRecursively()
            throw error
        } finally {
            activeProcesses -= processId
            cookieFile?.delete()
        }
    }

    fun cleanup(file: File) {
        val taskDirectory = file.parentFile
        if (taskDirectory != null && taskDirectory.parentFile?.name == "yt-dlp") {
            taskDirectory.deleteRecursively()
        } else {
            file.delete()
        }
    }

    override fun cancelAll() {
        activeProcesses.toList().forEach(YtDlpRuntime::cancel)
    }

    private fun parseInfo(
        json: String,
        originalUrl: String,
        allowBrowserSession: Boolean
    ): ResolvedMedia {
        val root = JSONObject(json)
        val formatsJson = root.optJSONArray("formats")
        val formats = buildList {
            if (formatsJson != null) {
                for (index in 0 until formatsJson.length()) {
                    val item = formatsJson.optJSONObject(index) ?: continue
                    val id = item.optString("format_id").takeIf(String::isNotBlank) ?: continue
                    val extension = item.optString("ext").takeIf(String::isNotBlank) ?: continue
                    val videoCodec = item.optString("vcodec").takeIf(String::isNotBlank)
                    val audioCodec = item.optString("acodec").takeIf(String::isNotBlank)
                    if (videoCodec == "none" && audioCodec == "none") continue
                    add(
                        MediaFormatOption(
                            id = id,
                            extension = extension,
                            videoCodec = videoCodec,
                            audioCodec = audioCodec,
                            width = item.optInt("width").coerceAtLeast(0),
                            height = item.optInt("height").coerceAtLeast(0),
                            fps = item.optInt("fps").coerceAtLeast(0),
                            audioBitrateKbps = item.optInt("abr").coerceAtLeast(0),
                            totalBitrateKbps = item.optInt("tbr").coerceAtLeast(0),
                            fileSize = item.optLong("filesize").takeIf { it > 0L }
                                ?: item.optLong("filesize_approx").takeIf { it > 0L }
                        )
                    )
                }
            }
        }
        val bestVideo = formats.filter(MediaFormatOption::hasVideo)
            .maxWithOrNull(compareBy<MediaFormatOption> { it.height }.thenBy { it.totalBitrateKbps })
        val bestAudio = formats.filter(MediaFormatOption::hasAudio)
            .maxWithOrNull(compareBy<MediaFormatOption> { it.audioBitrateKbps }.thenBy { it.fileSize ?: 0L })
        val title = root.optString("track").takeIf(String::isNotBlank)
            ?: root.optString("title").takeIf(String::isNotBlank)
        val artist = root.optString("artist").takeIf(String::isNotBlank)
            ?: root.optString("creator").takeIf(String::isNotBlank)
            ?: root.optString("uploader").takeIf(String::isNotBlank)
            ?: root.optString("channel").takeIf(String::isNotBlank)
        val album = root.optString("album").takeIf(String::isNotBlank)
        val thumbnail = root.optString("thumbnail").takeIf { it.startsWith("http") }
        val size = root.optLong("filesize").takeIf { it > 0L }
            ?: root.optLong("filesize_approx").takeIf { it > 0L }
            ?: bestVideo?.fileSize
            ?: bestAudio?.fileSize
        val isAudioOnly = (formats.isNotEmpty() && formats.none(MediaFormatOption::hasVideo)) ||
            root.optString("vcodec") == "none" ||
            root.optString("_type") == "audio" ||
            WebLink.isYouTubeMusicUrl(originalUrl)

        return ResolvedMedia(
            url = originalUrl,
            kind = if (isAudioOnly) MediaKind.AUDIO else MediaKind.VIDEO,
            thumbnailUrl = thumbnail,
            width = root.optInt("width").takeIf { it > 0 } ?: bestVideo?.width ?: 0,
            height = root.optInt("height").takeIf { it > 0 } ?: bestVideo?.height ?: 0,
            fileSize = size,
            username = artist,
            backend = MediaBackend.YT_DLP,
            title = title,
            artist = artist,
            album = album,
            durationSeconds = root.optInt("duration").coerceAtLeast(0),
            formats = formats,
            explicitBrowserSessionAuthorized = allowBrowserSession,
            sourceTimestampMillis = sourceTimestampMillis(root)
        )
    }

    private fun sourceTimestampMillis(root: JSONObject): Long? {
        sequenceOf(root.optLong("release_timestamp"), root.optLong("timestamp"))
            .firstOrNull { it > 0L }
            ?.let { return if (it > 10_000_000_000L) it else it * 1_000L }
        val uploadDate = root.optString("upload_date").takeIf { it.matches(Regex("\\d{8}")) }
            ?: return null
        return runCatching {
            SimpleDateFormat("yyyyMMdd", Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(uploadDate)?.time
        }.getOrNull()
    }

    private fun createCookieFile(url: String, allowed: Boolean): File? {
        if (!allowed) return null
        val normalized = WebLink.normalize(url) ?: return null
        val host = WebLink.host(normalized) ?: return null
        val cookieOrigin = when {
            WebLink.isYouTubeUrl(normalized) -> "https://www.youtube.com"
            WebLink.isInstagramHost(normalized) -> "https://www.instagram.com"
            else -> normalized
        }
        val header = runCatching { CookieManager.getInstance().getCookie(cookieOrigin) }
            .getOrNull()?.takeIf(String::isNotBlank) ?: return null
        val cookieDomain = when {
            host == "youtu.be" || host.endsWith("youtube.com") -> ".youtube.com"
            host.endsWith("instagram.com") -> ".instagram.com"
            else -> ".${URI(normalized).host}"
        }
        val rows = header.split(';').mapNotNull { raw ->
            val pair = raw.trim().split('=', limit = 2)
            if (pair.size != 2 || pair[0].isBlank()) null
            else "$cookieDomain\tTRUE\t/\tTRUE\t0\t${pair[0]}\t${pair[1]}"
        }
        if (rows.isEmpty()) return null
        return YtDlpTemporaryFiles.createCookieFile(appContext, rows)
    }

    private fun readableError(stderr: String): String = stderr.lineSequence()
        .map(String::trim)
        .lastOrNull { it.startsWith("ERROR:", ignoreCase = true) }
        ?.removePrefix("ERROR:")
        ?.trim()
        ?: stderr.lineSequence().map(String::trim).lastOrNull(String::isNotBlank)
        ?: YT_DLP_FALLBACK_DIAGNOSTIC

    private companion object {
        const val YT_DLP_FALLBACK_DIAGNOSTIC = "yt-dlp produced no diagnostic output"
        val SIDECAR_EXTENSIONS = setOf(
            "part", "ytdl", "json", "jpg", "jpeg", "png", "webp", "vtt", "srt", "ass", "lrc"
        )
    }
}
