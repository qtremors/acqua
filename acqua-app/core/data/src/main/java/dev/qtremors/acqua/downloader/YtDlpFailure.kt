package dev.qtremors.acqua.downloader

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

enum class YtDlpFailure {
    RUNTIME,
    NETWORK,
    STORAGE,
    UPDATE_SERVICE,
    UNKNOWN
}

fun Throwable.toYtDlpFailure(): YtDlpFailure {
    val causes = causeChain()
    val details = causes.joinToString(" ") { error ->
        "${error.javaClass.name} ${error.message.orEmpty()}"
    }.lowercase()

    return when {
        causes.any { it is LinkageError || it is ReflectiveOperationException } ||
            RUNTIME_MARKERS.any(details::contains) -> YtDlpFailure.RUNTIME

        causes.any {
            it is UnknownHostException || it is SocketTimeoutException ||
                it is ConnectException || it is SSLException
        } -> YtDlpFailure.NETWORK

        UPDATE_SERVICE_MARKERS.any(details::contains) -> YtDlpFailure.UPDATE_SERVICE
        causes.any { it is SecurityException } || STORAGE_MARKERS.any(details::contains) ->
            YtDlpFailure.STORAGE

        causes.any { it is IOException } -> YtDlpFailure.NETWORK
        else -> YtDlpFailure.UNKNOWN
    }
}

fun Throwable.hasNetworkCause(): Boolean = toYtDlpFailure() == YtDlpFailure.NETWORK

private fun Throwable.causeChain(): List<Throwable> {
    val causes = mutableListOf<Throwable>()
    var current: Throwable? = this
    while (current != null && causes.size < MAX_CAUSES && causes.none { it === current }) {
        causes += current
        current = current.cause
    }
    return causes
}

private val RUNTIME_MARKERS = listOf(
    "failed to initialize",
    "not a concrete class",
    "exceptionininitializererror"
)
private val UPDATE_SERVICE_MARKERS = listOf(
    "unable to get download url",
    "unable to parse update",
    "jsonprocessingexception"
)
private val STORAGE_MARKERS = listOf(
    "no space left",
    "permission denied",
    "read-only file system"
)
private const val MAX_CAUSES = 16
