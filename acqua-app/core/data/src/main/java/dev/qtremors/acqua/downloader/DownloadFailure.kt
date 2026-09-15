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
    UNKNOWN("download_error_unknown", false)

    ;

    companion object {
        fun fromCode(code: String?): DownloadFailure = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

fun Throwable.toDownloadFailure(): DownloadFailure = when (toYtDlpFailure()) {
    YtDlpFailure.RUNTIME -> DownloadFailure.RUNTIME
    YtDlpFailure.NETWORK -> DownloadFailure.NETWORK
    YtDlpFailure.STORAGE -> DownloadFailure.STORAGE
    YtDlpFailure.UPDATE_SERVICE -> DownloadFailure.UPDATE_SERVICE
    YtDlpFailure.UNKNOWN -> DownloadFailure.UNKNOWN
}

object SafeDiagnostics {
    private val url = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
    private val email = Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)
    private val accountHandle = Regex("(?<![A-Za-z0-9._])@[A-Za-z0-9._]{2,}")
    private val secret = Regex(
        "(?i)(cookie|token|authorization|session(?:id)?|password)\\s*(?:[:=]|\\s)\\s*[^\\s,;]+"
    )
    private val longIdentifier = Regex("(?<![A-Za-z0-9])[A-Za-z0-9_-]{24,}(?![A-Za-z0-9])")
    private val windowsPath = Regex("[A-Za-z]:\\\\(?:[^\\s\\\\]+\\\\)*[^\\s\\\\]+")
    private val unixPath = Regex("(?<!:)\\/(?:[^\\s/]+\\/)+[^\\s/]+")

    fun redact(error: Throwable): Throwable {
        val safeMessage = error.message.orEmpty()
            .replace(url, "[url]")
            .replace(secret) { "${it.groupValues[1]}=[redacted]" }
            .replace(email, "[email]")
            .replace(accountHandle, "@[account]")
            .replace(longIdentifier, "[identifier]")
            .replace(windowsPath, "[path]")
            .replace(unixPath, "[path]")
            .ifBlank { "No diagnostic message" }
        return RuntimeException(
            "${error.javaClass.simpleName}: $safeMessage".take(MAX_DIAGNOSTIC_LENGTH)
        )
    }

    private const val MAX_DIAGNOSTIC_LENGTH = 512
}
