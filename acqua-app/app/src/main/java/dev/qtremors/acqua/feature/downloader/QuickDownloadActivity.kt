package dev.qtremors.acqua.feature.downloader

import android.content.Intent
import dev.qtremors.acqua.domain.WebLink
import java.net.URI
import java.util.Locale

/** External entry point. Browser media and authorization extras are never accepted here. */
class QuickDownloadActivity : DownloadActivity() {
    override val externalQuickMode = true
}

internal object ExternalDownloadIntake {
    private val additionalTypes = setOf("text/html", "application/octet-stream", "application/vnd.apple.mpegurl", "application/x-mpegurl", "application/dash+xml")

    fun parse(intent: Intent): String? = runCatching {
        if (intent.action != Intent.ACTION_VIEW) return null
        val type = intent.type?.substringBefore(';')?.lowercase(Locale.ROOT)
        if (type != null && type !in additionalTypes && !type.startsWith("image/") && !type.startsWith("video/") && !type.startsWith("audio/")) return null
        canonical(intent.dataString ?: return null)
    }.getOrNull()

    fun canonical(value: String): String? {
        if (value.length !in 1..8192 || value.any { it.isWhitespace() || it.isISOControl() }) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https") || uri.rawUserInfo != null || uri.host.isNullOrBlank()) return null
        if (uri.port != -1 && uri.port !in 1..65535) return null
        return WebLink.normalize(value)
    }
}
