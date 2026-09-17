package dev.qtremors.acqua.domain

enum class MediaResolutionFailure {
    INVALID_LINK,
    SESSION_REQUIRED,
    ENGINE_REQUIRED,
    RUNTIME_UNAVAILABLE,
    UNSUPPORTED_MEDIA,
    NETWORK,
    SESSION_EXPIRED,
    UNKNOWN
}

class MediaResolutionException(
    val failure: MediaResolutionFailure,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
