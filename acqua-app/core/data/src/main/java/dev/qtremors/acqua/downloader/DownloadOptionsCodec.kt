package dev.qtremors.acqua.downloader

import dev.qtremors.acqua.data.settings.DownloadSettings
import org.json.JSONObject

object DownloadOptionsCodec {
    fun encode(options: YtDlpDownloadOptions): String = JSONObject()
        .put("video", options.videoFormatId).put("audio", options.audioFormatId)
        .put("videoHasAudio", options.videoFormatIncludesAudio)
        .put("additionalAudio", org.json.JSONArray(options.additionalAudioFormatIds.sorted())).put("silentVideo", options.silentVideo)
        .put("acceptedStreams", DownloadStreams.encode(options.acceptedStreams))
        .put("commandTemplateId", options.commandTemplateId).put("commandTemplateSnapshot", options.commandTemplateSnapshot)
        .put("formatSelector", options.formatSelectorOverride).put("templateAudioMultistreams", options.templateAudioMultistreams)
        .put("container", options.videoContainer.name).put("bitrate", options.audioBitrateKbps)
        .put("captions", options.captionMode.name).put("captionFormat", options.captionFormat.name)
        .put("languages", options.captionLanguages).put("automatic", options.automaticCaptions)
        .put("clipStart", options.clipStartSeconds).put("clipEnd", options.clipEndSeconds)
        .put("splitChapters", options.splitChapters).put("skipArchived", options.skipArchived)
        .put("clipRanges", org.json.JSONArray(options.clipRanges.map { JSONObject().put("start", it.startSeconds).put("end", it.endSeconds).put("name", it.name) }))
        .put("captionTiming", JSONObject().put("offset", options.captionTiming.offsetSeconds).put("scale", options.captionTiming.scale))
        .put("cookieProfileId", options.cookieProfileId)
        .put("cookieProfileFingerprint", options.cookieProfileFingerprint)
        .put("contentType", options.contentType.name).put("height", options.maximumVideoHeight)
        .put("audioFormat", options.audioFormat.name).put("metadata", options.embedMetadata)
        .put("thumbnail", options.embedThumbnail).put("crop", options.cropArtwork)
        .put("downloadThumbnail", options.downloadThumbnail)
        .put("recordHistory", options.recordHistory)
        .put("verboseDiagnostics", options.verboseDiagnostics)
        .put("removeSegments", org.json.JSONArray(options.removeSegments.sortedBy { it.name }.map { it.name }))
        .put("formatPolicy", JSONObject().put("mode", options.formatPolicy.mode.name).put("videoCodec", options.formatPolicy.videoCodec.name)
            .put("audioCodec", options.formatPolicy.audioCodec.name).put("sort", options.formatPolicy.customSort)
            .put("decoders", org.json.JSONArray(options.formatPolicy.deviceCodecs.sortedBy { it.name }.map { it.name })))
        .put("network", JSONObject().put("rateKib", options.network.rateLimitKib).put("proxy", options.network.proxy)
            .put("ipv4", options.network.forceIpv4).put("fragments", options.network.concurrentFragments))
        .put("originalMetadata", options.preserveOriginalMetadata)
        .put("filename", options.filenamePatternOverride).put("audioFilename", options.audioFilenamePatternOverride).toString()

    fun decode(value: String?, base: YtDlpDownloadOptions): YtDlpDownloadOptions {
        if (value == null) return base
        val json = JSONObject(value)
        return base.copy(videoFormatId = json.optional("video"), audioFormatId = json.optional("audio"),
            videoFormatIncludesAudio = json.optBoolean("videoHasAudio", true),
            silentVideo = json.optBoolean("silentVideo"),
            commandTemplateId = json.optional("commandTemplateId")?.also { require(java.util.UUID.fromString(it).toString() == it) },
            commandTemplateSnapshot = json.optional("commandTemplateSnapshot")?.also { CommandTemplateParser.resolve(YtDlpDownloadOptions(), it) },
            formatSelectorOverride = json.optional("formatSelector")?.let(CommandTemplateParser::validateFormat),
            templateAudioMultistreams = json.optBoolean("templateAudioMultistreams"),
            additionalAudioFormatIds = if (json.has("additionalAudio")) json.getJSONArray("additionalAudio").let { ids ->
                require(ids.length() <= 8)
                (0 until ids.length()).map { ids.getString(it).also { id -> require(id.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}"))) } }.toSet()
            } else emptySet(),
            acceptedStreams = if (json.has("acceptedStreams")) DownloadStreams.decode(json.getJSONArray("acceptedStreams")) else emptyList(),
            videoContainer = VideoContainer.valueOf(json.optString("container", "AUTO")),
            audioBitrateKbps = json.optInt("bitrate").also { require(it in 0..512) },
            captionMode = CaptionMode.valueOf(json.optString("captions", "NONE")),
            captionFormat = CaptionFormat.valueOf(json.optString("captionFormat", "ORIGINAL")),
            captionLanguages = json.optString("languages", "en"), automaticCaptions = json.optBoolean("automatic"),
            clipStartSeconds = if (json.has("clipStart")) json.getDouble("clipStart") else null,
            clipEndSeconds = if (json.has("clipEnd")) json.getDouble("clipEnd") else null,
            splitChapters = json.optBoolean("splitChapters"), skipArchived = json.optBoolean("skipArchived"),
            clipRanges = if (json.has("clipRanges")) json.getJSONArray("clipRanges").let { ranges ->
                require(ranges.length() <= MediaTimeline.MAX_CLIPS)
                (0 until ranges.length()).map { index -> ranges.getJSONObject(index).let { ClipRange(it.getDouble("start"), it.getDouble("end"), it.getString("name")) } }
                    .also { MediaTimeline.validateRanges(it) }
            } else emptyList(),
            captionTiming = if (json.has("captionTiming")) json.getJSONObject("captionTiming").let { CaptionTiming(it.getDouble("offset"), it.getDouble("scale")).validated() } else CaptionTiming(),
            cookieProfileId = json.optional("cookieProfileId")?.also { require(java.util.UUID.fromString(it).toString() == it) },
            cookieProfileFingerprint = json.optional("cookieProfileFingerprint")?.also { require(it.matches(Regex("[a-f0-9]{64}")) && json.optional("cookieProfileId") != null) },
            contentType = DownloadContentType.valueOf(json.optString("contentType", base.contentType.name)),
            maximumVideoHeight = json.optInt("height", base.maximumVideoHeight),
            audioFormat = AudioOutputFormat.valueOf(json.optString("audioFormat", base.audioFormat.name)),
            embedMetadata = json.optBoolean("metadata", base.embedMetadata),
            embedThumbnail = json.optBoolean("thumbnail", base.embedThumbnail),
            downloadThumbnail = json.optBoolean("downloadThumbnail", false),
            recordHistory = json.optBoolean("recordHistory", base.recordHistory),
            verboseDiagnostics = json.optBoolean("verboseDiagnostics", base.verboseDiagnostics),
            formatPolicy = if (json.has("formatPolicy")) json.getJSONObject("formatPolicy").let { policy ->
                val decoders = policy.getJSONArray("decoders")
                require(decoders.length() <= VideoCodecPreference.entries.size)
                DownloadFormatPolicy(FormatPolicyMode.valueOf(policy.getString("mode")), VideoCodecPreference.valueOf(policy.getString("videoCodec")),
                    AudioCodecPreference.valueOf(policy.getString("audioCodec")), policy.getString("sort"),
                    (0 until decoders.length()).map { VideoCodecPreference.valueOf(decoders.getString(it)) }.toSet()).validated()
            } else base.formatPolicy,
            removeSegments = if (json.has("removeSegments")) json.getJSONArray("removeSegments").let { categories ->
                require(categories.length() <= SegmentCategory.entries.size)
                (0 until categories.length()).map { SegmentCategory.valueOf(categories.getString(it)) }.toSet()
            } else base.removeSegments,
            network = if (json.has("network")) json.getJSONObject("network").let { network -> dev.qtremors.acqua.domain.TransferNetworkOptions(
                network.getInt("rateKib"), network.getString("proxy"), network.getBoolean("ipv4"), network.getInt("fragments")
            ).validated() } else base.network,
            cropArtwork = json.optBoolean("crop", base.cropArtwork),
            preserveOriginalMetadata = json.optBoolean("originalMetadata", base.preserveOriginalMetadata),
            filenamePatternOverride = json.optional("filename"), audioFilenamePatternOverride = json.optional("audioFilename"))
    }

    internal fun encodeStorage(settings: DownloadSettings): String = JSONObject()
        .put("folder", settings.baseFolder).put("categorize", settings.categorizeMedia)
        .put("filename", settings.filenamePattern).put("audioFilename", settings.audioFilenamePattern)
        .put("destinations", settings.destinations.json()).toString()

    internal fun decodeStorage(value: String?): DownloadSettings? = value?.let {
        val json = JSONObject(it)
        DownloadSettings(json.getString("folder"), json.getBoolean("categorize"),
            json.getString("filename"), json.getString("audioFilename"),
            dev.qtremors.acqua.data.settings.DownloadDestinations.parse(if (json.has("destinations")) json.getJSONObject("destinations") else null))
    }

    private fun JSONObject.optional(key: String) = optString(key).takeIf { it.isNotBlank() && it != "null" }
}
