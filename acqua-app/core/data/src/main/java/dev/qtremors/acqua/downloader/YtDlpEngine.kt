package dev.qtremors.acqua.downloader

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLResponse
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaFormatOption
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.MediaResolutionException
import dev.qtremors.acqua.domain.MediaResolutionFailure
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.data.settings.AppSettingsRepository
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

interface DownloadProcessingEngine : YtDlpCancellation {
    fun download(media: ResolvedMedia, options: YtDlpDownloadOptions, onProgress: (YtDlpProgress) -> Unit,
        taskKey: String? = null): DownloadOutputSet
    fun cleanup(outputs: DownloadOutputSet)
}

class YtDlpEngine(context: Context) : DownloadProcessingEngine {
    private val appContext = context.applicationContext
    private val settings = AppSettingsRepository(appContext)
    private val activeProcesses = ConcurrentHashMap.newKeySet<String>()
    private val timelineProcesses = ConcurrentHashMap.newKeySet<Process>()
    private val cancellationGeneration = java.util.concurrent.atomic.AtomicLong()

    fun inspect(url: String, allowBrowserSession: Boolean = false): ResolvedMedia =
        inspectItems(url, allowBrowserSession).first()

    fun inspectItems(url: String, allowBrowserSession: Boolean = false, sessionSnapshot: String? = null): List<ResolvedMedia> {
        val generation = cancellationGeneration.get()
        val document = inspectDocument(url, allowBrowserSession, sessionSnapshot = sessionSnapshot)
        return parseInspection(document.first, url, document.second) { nested ->
            if (generation != cancellationGeneration.get()) throw java.util.concurrent.CancellationException()
            inspectDocument(nested, allowBrowserSession, sessionSnapshot = sessionSnapshot).first
        }.map { if (sessionSnapshot == null) it else it.copy(authorizedSession = sessionSnapshot,
            requestUserAgent = dev.qtremors.acqua.data.session.CookieProfile.parse(sessionSnapshot).userAgent) }
    }

    private fun inspectDocument(url: String, allowBrowserSession: Boolean, entryIndex: Int? = null, detailed: Boolean = false,
        network: dev.qtremors.acqua.domain.TransferNetworkOptions? = null, sessionSnapshot: String? = null): Pair<String, Boolean> {
        val processId = "inspect-${UUID.randomUUID()}"
        val session = dev.qtremors.acqua.data.session.BrowserSessionTransfer(appContext).create(url, allowBrowserSession, sessionSnapshot)
        val cookieFile = session?.file
        val request = YoutubeDLRequest(url).apply {
            addOption("--dump-single-json")
            addOption("--skip-download")
            addOption(if (detailed && entryIndex == null) "--no-playlist" else "--yes-playlist")
            if (entryIndex != null) addOption("--playlist-items", entryIndex.toString())
            addOption(if (detailed) "--no-flat-playlist" else "--flat-playlist")
            addOption("--no-warnings")
            addOption("--socket-timeout", 15)
            (network ?: settings.downloadDefaults().network).ytDlpArguments(inspection = true).forEach { (name, value) ->
                if (value == null) addOption(name) else addOption(name, value)
            }
            cookieFile?.let { addOption("--cookies", it.absolutePath) }
            session?.let { addOption("--user-agent", it.userAgent) }
        }
        return try {
            activeProcesses += processId
            val response = executeInspection {
                YtDlpRuntime.execute(appContext, request, processId)
            }
            response.out to (cookieFile != null)
        } finally {
            activeProcesses -= processId
            cookieFile?.delete()
        }
    }

    internal fun executeInspection(execute: () -> YoutubeDLResponse): YoutubeDLResponse {
        val response = try {
            execute()
        } catch (error: YoutubeDLException) {
            throw inspectionFailure(error.message.orEmpty(), error)
        }
        if (response.exitCode != 0) throw inspectionFailure(response.err)
        return response
    }

    private fun inspectionFailure(stderr: String, cause: Throwable? = null): MediaResolutionException {
        val message = readableError(stderr)
        val failure = if (listOf("log in", "login", "sign in", "cookies", "authentication").any {
            message.contains(it, ignoreCase = true)
        }) MediaResolutionFailure.SESSION_REQUIRED else MediaResolutionFailure.UNKNOWN
        return MediaResolutionException(failure, message, cause)
    }

    override fun download(
        media: ResolvedMedia,
        options: YtDlpDownloadOptions,
        onProgress: (YtDlpProgress) -> Unit,
        taskKey: String?
    ): DownloadOutputSet {
        options.cookieProfileId?.let { id ->
            if (media.authorizedSession?.let(dev.qtremors.acqua.data.session.CookieProfile::parse)?.id != id)
                throw dev.qtremors.acqua.data.session.CookieProfileException()
        }
        val generation = cancellationGeneration.get()
        val accepted = if (options.acceptedStreams.isEmpty()) DownloadStreams.accepted(media, options) else options
        return DownloadStreams.recover(media, accepted, inspect = {
            if (generation != cancellationGeneration.get()) throw java.util.concurrent.CancellationException()
            val source = DownloadSourceSelection.from(media)
            val document = inspectDocument(source.url, media.explicitBrowserSessionAuthorized, source.collectionIndex, detailed = true,
                network = options.network, sessionSnapshot = media.authorizedSession)
            (parseInspection(document.first, source.url, document.second).singleOrNull() ?: throw SourceStreamsChangedException())
                .copy(authorizedSession = media.authorizedSession, requestUserAgent = media.requestUserAgent)
        }, download = { fresh, choices ->
            if (generation != cancellationGeneration.get()) throw java.util.concurrent.CancellationException()
            downloadOnce(fresh, choices, onProgress, taskKey)
        })
    }

    private fun downloadOnce(originalMedia: ResolvedMedia, originalOptions: YtDlpDownloadOptions, onProgress: (YtDlpProgress) -> Unit,
        taskKey: String?): DownloadOutputSet {
        val generation = cancellationGeneration.get()
        val media = timelineSource(originalMedia, originalOptions)
        if (generation != cancellationGeneration.get()) throw java.util.concurrent.CancellationException()
        DownloadAdvancedOptions.arguments(media, originalOptions)
        if (MediaTimeline.active(originalOptions)) MediaTimeline.plan(media, originalOptions)
        val options = MediaTimeline.sourceOptions(originalOptions)
        val advancedArguments = DownloadAdvancedOptions.arguments(media, options)
        val networkArguments = options.network.ytDlpArguments()
        val processId = "download-${taskKey ?: UUID.randomUUID()}"
        val taskDirectory = YtDlpTemporaryFiles.prepareTaskDirectory(appContext, processId)
        val source = DownloadSourceSelection.from(media)
        val downloadUrl = source.url
        val session = dev.qtremors.acqua.data.session.BrowserSessionTransfer(appContext).create(downloadUrl, media.explicitBrowserSessionAuthorized, media.authorizedSession)
        val cookieFile = session?.file
        val manifest = File(taskDirectory, "outputs.jsonl")
        val request = YoutubeDLRequest(downloadUrl).apply {
            if (source.collectionIndex != null) {
                addOption("--yes-playlist")
                addOption("--playlist-items", source.collectionIndex.toString())
            } else addOption("--no-playlist")
            addOption("--print", "after_move:ACQUA_OUTPUT:[%(id)j,%(filepath)j]")
            if (options.splitChapters) {
                DownloadChapterManifest.requestOptions().forEach { (name, value) -> addOption(name, value) }
            }
            addOption("--no-quiet")
            addOption("--progress")
            addOption("--concat-playlist", "never")
            addOption("--newline")
            addOption(if (options.preserveOriginalMetadata) "--mtime" else "--no-mtime")
            addOption("--trim-filenames", 180)
            addOption("--socket-timeout", 20)
            networkArguments.forEach { (name, value) -> if (value == null) addOption(name) else addOption(name, value) }
            addOption("-P", taskDirectory.absolutePath)
            addOption("-o", "%(title).180B [%(id)s].%(ext)s")
            cookieFile?.let { addOption("--cookies", it.absolutePath) }
            session?.let { addOption("--user-agent", it.userAgent) }

            if (options.contentType == DownloadContentType.AUDIO) {
                addOption("-x")
                addOption("--audio-quality", if (options.audioBitrateKbps > 0 && options.audioFormat != AudioOutputFormat.ORIGINAL) "${options.audioBitrateKbps}K" else "0")
                when (options.audioFormat) {
                    AudioOutputFormat.ORIGINAL -> addOption("--audio-format", "best")
                    AudioOutputFormat.M4A -> addOption("--audio-format", "m4a")
                    AudioOutputFormat.MP3 -> addOption("--audio-format", "mp3")
                }
            }

            advancedArguments.forEach { (name, value) ->
                if (value == null) addOption(name) else addOption(name, value)
            }

            YtDlpPostProcessing.options(options).forEach { (name, value) ->
                if (value == null) addOption(name) else addOption(name, value)
            }
            if (options.embedThumbnail &&
                options.cropArtwork && options.contentType == DownloadContentType.AUDIO) {
                val ffmpeg = File(appContext.applicationInfo.nativeLibraryDir, "libffmpeg.so")
                addOption("--exec", YtDlpPostProcessing.cropCommand(ffmpeg, File(taskDirectory, "cover-cropped.png")))
            }
        }

        return try {
            activeProcesses += processId
            val response = YtDlpRuntime.execute(appContext, request, processId) { percent, eta, line ->
                onProgress(YtDlpProgress.fromCallback(percent, eta, line))
            }
            if (response.exitCode != 0) {
                if (options.removeSegments.isNotEmpty() && response.err.contains("SponsorBlock", true)) throw SegmentServiceException()
                error(readableError(response.err))
            }
            DownloadSegments.verify(response.out, response.err, media, options.removeSegments)
            CommandTemplateOutput.verify(response.out, options)
            manifest.writeText(DownloadChapterManifest.rows(response.out, options.splitChapters).joinToString("\n"))
            val outputs = DownloadOutputSet.read(taskDirectory, manifest, options.captionMode)
            MediaTimelineProcessor({ arguments -> runTimelineCommand(arguments, generation) }, {
                if (generation != cancellationGeneration.get()) throw java.util.concurrent.CancellationException()
            }).process(outputs, media, originalOptions)
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

    override fun cleanup(outputs: DownloadOutputSet) { outputs.directory.deleteRecursively() }

    override fun cancelAll() {
        cancellationGeneration.incrementAndGet()
        activeProcesses.toList().forEach(YtDlpRuntime::cancel)
        timelineProcesses.toList().forEach(Process::destroy)
    }

    private fun runTimelineCommand(arguments: List<String>, generation: Long) {
        if (generation != cancellationGeneration.get()) throw java.util.concurrent.CancellationException()
        val native = appContext.applicationInfo.nativeLibraryDir
        val builder = ProcessBuilder(listOf(File(native, "libffmpeg.so").absolutePath) + arguments).redirectErrorStream(true)
        builder.environment()["LD_LIBRARY_PATH"] = listOf(native,
            File(appContext.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib").absolutePath,
            File(appContext.noBackupFilesDir, "youtubedl-android/packages/python/usr/lib").absolutePath).joinToString(":")
        val process = builder.start()
        timelineProcesses += process
        try {
            if (generation != cancellationGeneration.get()) throw java.util.concurrent.CancellationException()
            process.inputStream.use { stream -> val buffer = ByteArray(4096); while (stream.read(buffer) >= 0) Unit }
            if (generation != cancellationGeneration.get()) throw java.util.concurrent.CancellationException()
            if (process.waitFor() != 0) throw MediaTimelineException()
        } finally { timelineProcesses -= process; process.destroy() }
    }

    private fun timelineSource(media: ResolvedMedia, options: YtDlpDownloadOptions): ResolvedMedia {
        if (!options.splitChapters || media.chapters.isNotEmpty()) return media
        val source = DownloadSourceSelection.from(media)
        val document = inspectDocument(source.url, media.explicitBrowserSessionAuthorized, source.collectionIndex, detailed = true,
            network = options.network, sessionSnapshot = media.authorizedSession)
        val detailed = parseInspection(document.first, source.url, document.second).singleOrNull() ?: throw NoDownloadableChaptersException()
        if (media.sourceIdentity != null && detailed.sourceIdentity != media.sourceIdentity) throw SourceStreamsChangedException()
        return media.copy(chapters = detailed.chapters, preciseDurationSeconds = detailed.preciseDurationSeconds,
            durationSeconds = detailed.durationSeconds)
    }

    internal fun parseInspection(json: String, url: String, authorized: Boolean, loadNested: ((String) -> String)? = null): List<ResolvedMedia> =
        CollectionInspection({ entry, parent, allowed ->
            val entryUrl = sequenceOf(entry.optString("webpage_url"),
                entry.optString("url").takeIf { entry.optString("_type") in setOf("url", "url_transparent") }.orEmpty())
                .mapNotNull(WebLink::normalize).firstOrNull() ?: youtubeEntryUrl(entry, parent) ?: parent
            parseInfo(entry.toString(), entryUrl, allowed)
        }, loadNested).parse(json, url, authorized)

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
                            language = item.optString("language").takeIf(String::isNotBlank),
                            audioChannels = item.optInt("audio_channels").coerceAtLeast(0),
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
        val thumbnail = inspectionThumbnail(root, originalUrl)
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
            preciseDurationSeconds = root.optDouble("duration").takeIf { it.isFinite() && it >= 0 },
            formats = formats,
            explicitBrowserSessionAuthorized = allowBrowserSession,
            sourceTimestampMillis = sourceTimestampMillis(root),
            sourceIdentity = root.optString("id").takeIf(String::isNotBlank)?.let {
                val extractor = sequenceOf("extractor_key", "ie_key", "extractor").map(root::optString)
                    .firstOrNull(String::isNotBlank) ?: WebLink.host(originalUrl)
                if (extractor.equals("generic", true)) "$extractor:${WebLink.mediaAssetIdentity(originalUrl)}:$it" else "$extractor:$it"
            },
            chapters = buildList {
                val chapters = root.optJSONArray("chapters") ?: return@buildList
                for (index in 0 until chapters.length()) {
                    val chapter = chapters.optJSONObject(index) ?: continue
                    val start = chapter.optDouble("start_time", Double.NaN)
                    val end = chapter.optDouble("end_time", Double.NaN)
                    if (start.isFinite() && end.isFinite() && start >= 0 && end > start) {
                        add(dev.qtremors.acqua.domain.MediaChapter(chapter.optString("title"), start, end))
                    }
                }
            },
            captions = buildList {
                listOf("subtitles" to false, "automatic_captions" to true).forEach { (key, automatic) ->
                    val tracks = root.optJSONObject(key) ?: return@forEach
                    tracks.keys().forEach { language ->
                        val row = tracks.optJSONArray(language)?.optJSONObject(0)
                        val name = row?.optString("name")
                        val translated = row?.optString("url")?.let { url ->
                            runCatching { !android.net.Uri.parse(url).getQueryParameter("tlang").isNullOrBlank() }.getOrDefault(false)
                        } ?: false
                        add(dev.qtremors.acqua.domain.CaptionTrack(language, name, automatic, translated))
                    }
                }
            }
        )
    }

    private fun youtubeEntryUrl(entry: JSONObject, collectionUrl: String): String? {
        val youtube = WebLink.isYouTubeUrl(collectionUrl) || entry.optString("ie_key").equals("Youtube", true)
        val id = entry.optString("id").takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
        return if (youtube && id != null) "https://www.youtube.com/watch?v=$id" else null
    }

    private fun inspectionThumbnail(root: JSONObject, url: String): String? {
        root.optString("thumbnail").takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?.let(WebLink::normalize)?.let { return it }
        val thumbnails = root.optJSONArray("thumbnails")
        val fromArray = (0 until (thumbnails?.length() ?: 0)).mapNotNull { thumbnails?.optJSONObject(it) }
            .filter { it.optString("url").startsWith("https://") || it.optString("url").startsWith("http://") }
            .maxByOrNull { it.optInt("width") * it.optInt("height") }?.optString("url")
        if (fromArray != null) return fromArray
        return youtubeEntryUrl(root, url)?.let { "https://i.ytimg.com/vi/${root.optString("id")}/hqdefault.jpg" }
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

    private fun readableError(stderr: String): String = stderr.lineSequence()
        .map(String::trim)
        .lastOrNull { it.startsWith("ERROR:", ignoreCase = true) }
        ?.removePrefix("ERROR:")
        ?.trim()
        ?: stderr.lineSequence().map(String::trim).lastOrNull(String::isNotBlank)
        ?: YT_DLP_FALLBACK_DIAGNOSTIC

    private companion object {
        const val YT_DLP_FALLBACK_DIAGNOSTIC = "yt-dlp produced no diagnostic output"
    }
}
