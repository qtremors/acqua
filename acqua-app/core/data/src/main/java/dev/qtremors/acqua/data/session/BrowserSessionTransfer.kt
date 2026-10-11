package dev.qtremors.acqua.data.session

import android.content.Context
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.downloader.YtDlpTemporaryFiles
import java.io.File

data class BrowserTransferSession(val file: File, val userAgent: String)

class BrowserSessionTransfer(private val context: Context, private val profiles: CookieProfileStore = CookieProfileStore(context),
    private val capture: (String, String) -> CookieProfile = { name, url -> BrowserCookieCapture(context).capture(name, url) }) {
    fun create(url: String, allowed: Boolean, snapshot: String? = null): BrowserTransferSession? {
        val enabled = AppSettingsRepository(context).useBrowserSessions()
        if (allowed && snapshot != null && !enabled) throw CookieProfileException()
        if (!allowed || !enabled) return null
        val captureUrl = when {
            WebLink.isYouTubeUrl(url) -> "https://www.youtube.com/"
            WebLink.isInstagramHost(url) -> "https://www.instagram.com/"
            else -> WebLink.normalize(url) ?: return null
        }
        val profile = if (snapshot != null) CookieProfile.parse(snapshot).also { profiles.requireAuthorized(it, captureUrl) }
            else runCatching { capture("Browser", captureUrl) }.getOrNull() ?: return null
        val rows = profile.netscape()
        if (rows.isEmpty()) { if (snapshot != null) throw CookieProfileException() else return null }
        return BrowserTransferSession(YtDlpTemporaryFiles.createCookieFile(context, rows), profile.userAgent)
    }
}
