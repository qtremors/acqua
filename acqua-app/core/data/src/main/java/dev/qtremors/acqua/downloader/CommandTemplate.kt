package dev.qtremors.acqua.downloader

import java.util.UUID

data class CommandTemplate(val id: String = UUID.randomUUID().toString(), val name: String, val command: String) {
    fun validated(): CommandTemplate {
        if (runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false).not() ||
            name.isBlank() || name.length > 80 || name.any { it.isISOControl() }) throw InvalidCommandTemplateException()
        CommandTemplateParser.validateOptions(CommandTemplateParser.resolve(YtDlpDownloadOptions(), command))
        return copy(name = name.trim(), command = command.trim())
    }

    fun applyTo(options: YtDlpDownloadOptions): YtDlpDownloadOptions = CommandTemplateParser.validateOptions(CommandTemplateParser.resolve(options, command))
        .copy(commandTemplateId = id, commandTemplateSnapshot = command)
}

class InvalidCommandTemplateException : IllegalArgumentException("Invalid or unsupported command template. Review its supported options.")
class MissingCommandTemplateException : IllegalArgumentException("The command template is missing or changed. Choose it again or clear its selection.")

object CommandTemplateParser {
    const val MAX_COMMAND_BYTES = 4096
    private fun invalid(): Nothing = throw InvalidCommandTemplateException()

    fun validateOptions(options: YtDlpDownloadOptions): YtDlpDownloadOptions = runCatching {
        DownloadAdvancedOptions.arguments(dev.qtremors.acqua.domain.ResolvedMedia("https://example.invalid/media", dev.qtremors.acqua.domain.MediaKind.VIDEO), options)
        options
    }.getOrElse { invalid() }

    fun resolve(base: YtDlpDownloadOptions, command: String): YtDlpDownloadOptions {
        val words = tokenize(command).toMutableList()
        if (words.firstOrNull() in listOf("yt-dlp", "yt-dlp.exe")) words.removeAt(0)
        if (words.isEmpty()) invalid()
        var options = base.copy(commandTemplateId = null, commandTemplateSnapshot = null, formatSelectorOverride = null,
            templateAudioMultistreams = false, acceptedStreams = emptyList())
        var position = 0
        fun value(): String = words.getOrNull(position++) ?: invalid()
        while (position < words.size) {
            when (val flag = value()) {
                "-f", "--format" -> options = options.copy(formatSelectorOverride = validateFormat(value()), videoFormatId = null, audioFormatId = null,
                    additionalAudioFormatIds = emptySet(), silentVideo = false, acceptedStreams = emptyList())
                "-S", "--format-sort" -> options = options.copy(formatPolicy = options.formatPolicy.copy(mode = FormatPolicyMode.CUSTOM, customSort = value()).validated())
                "-x", "--extract-audio" -> options = options.copy(contentType = DownloadContentType.AUDIO, silentVideo = false, additionalAudioFormatIds = emptySet(),
                    videoFormatId = null, videoFormatIncludesAudio = true, templateAudioMultistreams = false)
                "--audio-format" -> options = options.copy(audioFormat = when (value().lowercase()) {
                    "best", "original" -> AudioOutputFormat.ORIGINAL; "m4a" -> AudioOutputFormat.M4A; "mp3" -> AudioOutputFormat.MP3; else -> invalid()
                })
                "--audio-quality" -> options = options.copy(audioBitrateKbps = bitrate(value()))
                "--remux-video", "--merge-output-format" -> options = options.copy(videoContainer = when (value().lowercase()) {
                    "mp4" -> VideoContainer.MP4; "mkv" -> VideoContainer.MKV; "webm" -> VideoContainer.WEBM; else -> invalid()
                })
                "--audio-multistreams", "--no-audio-multistreams" -> options = options.copy(templateAudioMultistreams = flag == "--audio-multistreams")
                "--embed-metadata", "--no-embed-metadata" -> options = options.copy(embedMetadata = flag == "--embed-metadata")
                "--embed-thumbnail", "--no-embed-thumbnail" -> options = options.copy(embedThumbnail = flag == "--embed-thumbnail")
                "--write-thumbnail", "--no-write-thumbnail" -> options = options.copy(downloadThumbnail = flag == "--write-thumbnail")
                "--mtime", "--no-mtime" -> options = options.copy(preserveOriginalMetadata = flag == "--mtime")
                "--write-subs" -> options = options.copy(captionMode = if (options.captionMode in listOf(CaptionMode.EMBED, CaptionMode.BOTH)) CaptionMode.BOTH else CaptionMode.SEPARATE)
                "--embed-subs" -> options = options.copy(captionMode = if (options.captionMode in listOf(CaptionMode.SEPARATE, CaptionMode.BOTH)) CaptionMode.BOTH else CaptionMode.EMBED)
                "--no-write-subs" -> options = options.copy(captionMode = when (options.captionMode) { CaptionMode.BOTH -> CaptionMode.EMBED; CaptionMode.SEPARATE -> CaptionMode.NONE; else -> options.captionMode })
                "--no-embed-subs" -> options = options.copy(captionMode = when (options.captionMode) { CaptionMode.BOTH -> CaptionMode.SEPARATE; CaptionMode.EMBED -> CaptionMode.NONE; else -> options.captionMode })
                "--write-auto-subs", "--no-write-auto-subs" -> options = options.copy(automaticCaptions = flag == "--write-auto-subs",
                    captionMode = if (flag == "--write-auto-subs" && options.captionMode == CaptionMode.NONE) CaptionMode.SEPARATE else options.captionMode)
                "--sub-langs" -> options = options.copy(captionLanguages = value().also { if (!it.matches(Regex("(?:all|[A-Za-z0-9_-]{1,35})(?:,(?:all|[A-Za-z0-9_-]{1,35})){0,99}"))) invalid() })
                "--convert-subs" -> options = options.copy(captionFormat = runCatching { CaptionFormat.valueOf(value().uppercase()) }.getOrElse { invalid() })
                "--split-chapters", "--no-split-chapters" -> options = options.copy(splitChapters = flag == "--split-chapters",
                    clipRanges = if (flag == "--split-chapters") emptyList() else options.clipRanges,
                    clipStartSeconds = if (flag == "--split-chapters") null else options.clipStartSeconds,
                    clipEndSeconds = if (flag == "--split-chapters") null else options.clipEndSeconds)
                "--sponsorblock-remove" -> options = options.copy(removeSegments = value().split(',').map { name -> SegmentCategory.entries.firstOrNull { it.apiName == name } ?: invalid() }.toSet())
                "--no-sponsorblock" -> options = options.copy(removeSegments = emptySet())
                "--force-ipv4" -> options = options.copy(network = options.network.copy(forceIpv4 = true))
                "--concurrent-fragments" -> options = options.copy(network = options.network.copy(concurrentFragments = value().toIntOrNull() ?: invalid()).validated())
                "-r", "--limit-rate" -> options = options.copy(network = options.network.copy(rateLimitKib = rate(value())).validated())
                else -> invalid()
            }
        }
        if (options.contentType == DownloadContentType.AUDIO && options.templateAudioMultistreams) invalid()
        return options
    }

    fun validateFormat(value: String): String {
        if (value.isBlank() || value.length > 512 || value.trimStart().startsWith('-') || value.contains("://") || value.contains("~=") ||
            value.any { it.code !in 32..126 || it in "`;@$\\" }) invalid()
        if (Regex("(?i)\\b(cookies?|token|authorization|password|secret|api[_-]?key)\\s*[:=]").containsMatchIn(value)) invalid()
        val stack = ArrayDeque<Char>()
        var quote: Char? = null
        for (character in value) {
            if (quote != null) { if (character == quote) quote = null; continue }
            if (character == '\'' || character == '"') { if ('[' !in stack) invalid(); quote = character; continue }
            when (character) {
                '[', '(' -> stack.addLast(character)
                ']' -> if (stack.removeLastOrNull() != '[') invalid()
                ')' -> if (stack.removeLastOrNull() != '(') invalid()
                ',' -> if ('[' !in stack) invalid()
            }
        }
        if (quote != null || stack.isNotEmpty() || Regex("(?:^|[+/()])\\s*all(?=\\[|$|[+/()])", RegexOption.IGNORE_CASE).containsMatchIn(value)) invalid()
        return value
    }

    fun tokenize(command: String): List<String> {
        if (command.toByteArray(Charsets.UTF_8).size > MAX_COMMAND_BYTES || command.any { it.isISOControl() && it !in "\r\n\t" }) invalid()
        val result = mutableListOf<String>()
        val word = StringBuilder()
        var quote: Char? = null
        var escaped = false
        var started = false
        for (character in command) {
            if (escaped) { word.append(character); escaped = false; started = true }
            else if (character == '\\' && quote != '\'') { escaped = true; started = true }
            else if (quote != null) { if (character == quote) quote = null else word.append(character); started = true }
            else if (character == '\'' || character == '"') { quote = character; started = true }
            else if (character.isWhitespace()) { if (started) { result += word.toString(); word.clear(); started = false } }
            else { word.append(character); started = true }
        }
        if (quote != null || escaped) invalid()
        if (started) result += word.toString()
        if (result.size > 128 || result.any { it.length > 512 }) invalid()
        return result.flatMap { token -> if (token.startsWith("--") && '=' in token) listOf(token.substringBefore('='), token.substringAfter('=')) else listOf(token) }
    }

    private fun rate(value: String): Int {
        val match = Regex("([0-9]{1,7})([KkMm]?)").matchEntire(value) ?: invalid()
        val number = match.groupValues[1].toInt()
        return when (match.groupValues[2].uppercase()) { "M" -> (number.toLong() * 1024).takeIf { it <= Int.MAX_VALUE }?.toInt() ?: invalid(); "K" -> number; else -> if (number % 1024 == 0) number / 1024 else invalid() }
    }

    private fun bitrate(value: String): Int {
        if (value == "0") return 0
        if (!value.matches(Regex("[0-9]{2,3}[Kk]"))) invalid()
        return value.dropLast(1).toInt().takeIf { it in 32..512 } ?: invalid()
    }
}
