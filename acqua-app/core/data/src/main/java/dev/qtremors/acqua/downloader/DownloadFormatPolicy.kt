package dev.qtremors.acqua.downloader

import android.media.MediaCodecList
import dev.qtremors.acqua.domain.ResolvedMedia

enum class FormatPolicyMode { AUTO, COMPATIBLE, DEVICE, LOWEST, CUSTOM }
enum class VideoCodecPreference(val prefix: String, val mime: String) {
    AUTO("", ""), H264("avc|h264", "video/avc"), HEVC("hev|hvc|h265", "video/hevc"),
    AV1("av01|av1", "video/av01"), VP9("vp0?9", "video/x-vnd.on2.vp9")
}
enum class AudioCodecPreference(val prefix: String) { AUTO(""), AAC("mp4a|aac"), OPUS("opus"), MP3("mp3"), FLAC("flac") }

data class DownloadFormatPolicy(
    val mode: FormatPolicyMode = FormatPolicyMode.AUTO,
    val videoCodec: VideoCodecPreference = VideoCodecPreference.AUTO,
    val audioCodec: AudioCodecPreference = AudioCodecPreference.AUTO,
    val customSort: String = "res,fps,br",
    val deviceCodecs: Set<VideoCodecPreference> = emptySet()
) {
    fun validated(): DownloadFormatPolicy {
        require(customSort.length <= 160) { "Format sorting is too long." }
        val fields = customSort.split(',').map(String::trim)
        require(fields.size in 1..10 && fields.distinct().size == fields.size && fields.all { SORT_FIELD.matches(it) }) { "Use comma-separated sort fields, optionally prefixed with +." }
        require(VideoCodecPreference.AUTO !in deviceCodecs) { "Invalid decoder capability." }
        return copy(customSort = fields.joinToString(","), deviceCodecs = deviceCodecs.toSet())
    }

    fun accepted(capabilities: () -> Set<VideoCodecPreference> = ::androidVideoDecoders): DownloadFormatPolicy {
        val policy = validated()
        return if (mode == FormatPolicyMode.DEVICE) policy.copy(deviceCodecs = capabilities().ifEmpty { setOf(VideoCodecPreference.H264) })
        else policy.copy(deviceCodecs = emptySet())
    }

    fun sortingArguments(): List<Pair<String, String?>> = when (mode) {
        FormatPolicyMode.LOWEST -> listOf("--format-sort" to "+res,+br,+fps", "--format-sort-force" to null)
        FormatPolicyMode.CUSTOM -> listOf("--format-sort" to validated().customSort, "--format-sort-force" to null)
        else -> emptyList()
    }

    internal fun selector(media: ResolvedMedia, options: YtDlpDownloadOptions): String {
        validated()
        val portrait = media.height > media.width && media.width > 0
        val cap = if (options.maximumVideoHeight > 0) "[${if (portrait) "width" else "height"}<=?${options.maximumVideoHeight}]" else ""
        val videoCodecs = when {
            videoCodec != VideoCodecPreference.AUTO -> setOf(videoCodec)
            mode == FormatPolicyMode.COMPATIBLE -> setOf(VideoCodecPreference.H264)
            mode == FormatPolicyMode.DEVICE -> deviceCodecs.ifEmpty { setOf(VideoCodecPreference.H264) }
            else -> emptySet()
        }
        val audio = if (audioCodec == AudioCodecPreference.AUTO && mode == FormatPolicyMode.COMPATIBLE) AudioCodecPreference.AAC else audioCodec
        val vf = videoCodecs.takeIf { it.isNotEmpty() }?.sortedBy { it.name }?.joinToString("|") { it.prefix }
            ?.let { "[vcodec~='^(?:$it)']" }.orEmpty()
        val af = audio.prefix.takeIf { it.isNotEmpty() }?.let { "[acodec~='^(?:$it)']" }.orEmpty()
        val exactAudio = options.audioFormatId
        if (options.contentType == DownloadContentType.AUDIO) return exactAudio ?: "ba$af/b$af"
        val exactVideo = options.videoFormatId
        val videoOnly = "bv$cap$vf"
        if (options.silentVideo) return exactVideo ?: videoOnly
        if (options.additionalAudioFormatIds.isNotEmpty()) {
            val selected = (listOfNotNull(exactAudio.takeUnless { exactVideo != null && options.videoFormatIncludesAudio }) +
                options.additionalAudioFormatIds.sorted()).distinct()
            return (listOf(exactVideo ?: videoOnly) + selected).joinToString("+")
        }
        val audioStream = exactAudio ?: BEST_AUDIO + af
        if (exactVideo != null) return if (options.videoFormatIncludesAudio) exactVideo else "$exactVideo+$audioStream"
        if (exactAudio != null) return "bestvideo$cap$vf+$exactAudio"
        return "bv*$cap$vf+ba$af/b$cap$vf$af"
    }

    companion object {
        private const val BEST_AUDIO = "bestaudio"
        private val SORT_FIELD = Regex("\\+?(res|height|width|fps|br|tbr|vbr|abr|size|filesize|fs_approx|vcodec|acodec|codec|channels|ext|vext|aext|hdr)")
    }
}

internal fun androidVideoDecoders(): Set<VideoCodecPreference> = runCatching {
    val types = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filterNot { it.isEncoder }
        .flatMap { it.supportedTypes.toList() }.toSet()
    VideoCodecPreference.entries.filter { it != VideoCodecPreference.AUTO && it.mime in types }.toSet()
}.getOrDefault(emptySet())
