package dev.qtremors.acqua.feature.downloader

import android.content.Context
import dev.qtremors.acqua.data.session.CookieProfileStore
import dev.qtremors.acqua.data.settings.AppSettingsRepository

internal class CookieProfileChoices(context: Context, store: CookieProfileStore?) {
    private val profiles = store ?: CookieProfileStore(context)
    private val settings = AppSettingsRepository(context)
    val enabled: Boolean get() = settings.useBrowserSessions()
    fun entries() = profiles.all()
}
