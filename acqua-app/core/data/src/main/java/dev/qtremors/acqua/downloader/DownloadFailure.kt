package dev.qtremors.acqua.downloader

enum class DownloadFailure(
    val code: String,
    val retryable: Boolean
) {
    INVALID_REQUEST("download_error_invalid_request", false),
    RUNTIME("download_error_runtime", false),
    NETWORK("download_error_network", true),
    STORAGE("download_error_storage", false),
    UPDATE_SERVICE("download_error_update_service", true),
    PLATFORM_LIMIT("download_error_platform_limit", true),
    NO_CHAPTERS("download_error_no_chapters", false),
    NO_SEGMENTS("download_error_no_segments", false),
    SEGMENT_SERVICE("download_error_segment_service", true),
    SOURCE_STREAMS_CHANGED("download_error_source_streams_changed", false),
    COMMAND_TEMPLATE("download_error_command_template", false),
    MEDIA_TIMELINE("download_error_media_timeline", false),
    COOKIE_PROFILE("download_error_cookie_profile", false),
    UNKNOWN("download_error_unknown", false)

    ;

    companion object {
        fun fromCode(code: String?): DownloadFailure = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

fun Throwable.toDownloadFailure(): DownloadFailure {
    if (this is dev.qtremors.acqua.platform.storage.DownloadFolderException) return DownloadFailure.STORAGE
    if (this is dev.qtremors.acqua.data.session.CookieProfileException) return DownloadFailure.COOKIE_PROFILE
    if (this is MediaTimelineException) return DownloadFailure.MEDIA_TIMELINE
    if (this is InvalidCommandTemplateException || this is MissingCommandTemplateException) return DownloadFailure.COMMAND_TEMPLATE
    if (this is SourceStreamsChangedException) return DownloadFailure.SOURCE_STREAMS_CHANGED
    if (this is NoRemovableSegmentsException) return DownloadFailure.NO_SEGMENTS
    if (this is SegmentServiceException) return DownloadFailure.SEGMENT_SERVICE
    val http = generateSequence(this) { it.cause }.take(16)
        .filterIsInstance<dev.qtremors.acqua.data.network.MediaHttpException>().firstOrNull()
    if (http != null) return if (http.statusCode == 429 || http.statusCode in 500..599) DownloadFailure.NETWORK else DownloadFailure.UNKNOWN
    return if (this is NoDownloadableChaptersException) DownloadFailure.NO_CHAPTERS else when (toYtDlpFailure()) {
        YtDlpFailure.RUNTIME -> DownloadFailure.RUNTIME
        YtDlpFailure.NETWORK -> DownloadFailure.NETWORK
        YtDlpFailure.STORAGE -> DownloadFailure.STORAGE
        YtDlpFailure.UPDATE_SERVICE -> DownloadFailure.UPDATE_SERVICE
        YtDlpFailure.UNKNOWN -> DownloadFailure.UNKNOWN
    }
}

object SafeDiagnostics {
    private val url = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
    private val email = Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)
    private val accountHandle = Regex("(?<![A-Za-z0-9._])@[A-Za-z0-9._]{2,}")
    private val secret = Regex(
        "(?i)(cookies?|token|authorization|session(?:id)?|password|secret|api[_-]?key)\\s*(?:[:=]|\\s)\\s*[^\\s,;]+"
    )
    private val longIdentifier = Regex("(?<![A-Za-z0-9])[A-Za-z0-9_-]{24,}(?![A-Za-z0-9])")
    private val windowsPath = Regex("[A-Za-z]:\\\\(?:[^\\s\\\\]+\\\\)*[^\\s\\\\]+")
    private val unixPath = Regex("(?<!:)\\/(?:[^\\s/]+\\/)+[^\\r\\n]*")
    private val authorization = Regex("(?i)\\b(?:proxy-)?authorization[\"']?\\s*[:=]\\s*[\"']?(?:(?:bearer|basic)\\s+)?[^\\s,;\"']+")
    private val cookies = Regex("(?i)\\b(?:set-cookie|cookies?)[\"']?\\s*[:=]\\s*[^\\r\\n]*")
    private val quotedSecret = Regex("(?i)([\"']?(?:[a-z_-]*token|password|secret|api[_-]?key)[\"']?)\\s*[:=]\\s*(?:\"[^\"\\r\\n]*(?:\"|$)|'[^'\\r\\n]*(?:'|$))")
    private val spacedWindowsPath = Regex("[A-Za-z]:\\\\[^\\r\\n]*")

    fun redact(error: Throwable): Throwable {
        val safeMessage = text(error.message.orEmpty())
        return RuntimeException("${error.javaClass.simpleName}: $safeMessage".take(MAX_DIAGNOSTIC_LENGTH))
    }

    fun text(value: String, limit: Int = MAX_DIAGNOSTIC_LENGTH): String = value.take(16_384)
            .replace(url, "[url]")
            .replace(authorization, "Authorization=[redacted]")
            .replace(cookies, "Cookie=[redacted]")
            .replace(quotedSecret) { "${it.groupValues[1]}=[redacted]" }
            .replace(secret) { "${it.groupValues[1]}=[redacted]" }
            .replace(email, "[email]")
            .replace(accountHandle, "@[account]")
            .replace(longIdentifier, "[identifier]")
            .replace(spacedWindowsPath, "[path]")
            .replace(windowsPath, "[path]")
            .replace(unixPath, "[path]")
            .ifBlank { "No diagnostic message" }
            .take(limit.coerceIn(1, 8192))

    private const val MAX_DIAGNOSTIC_LENGTH = 512
}
