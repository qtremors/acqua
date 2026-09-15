package dev.qtremors.acqua.feature.browser

import android.graphics.Bitmap
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.qtremors.acqua.data.session.BrowserDataManager
import dev.qtremors.acqua.data.session.InstagramSessionStore
import dev.qtremors.acqua.data.session.SavedWebsite
import dev.qtremors.acqua.data.session.SavedWebsiteRepository
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.resolver.instagram.InstagramResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BrowserUiState(
    val useSessions: Boolean = false,
    val websites: List<SavedWebsite> = emptyList(),
    val initialized: Boolean = false,
    val currentUrl: String? = null,
    val pageTitle: String? = null,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isDesktopSite: Boolean = false,
    val isSecure: Boolean = true,
    val navigationAction: BrowserNavigationAction? = null,
    val savedSession: dev.qtremors.acqua.data.session.SavedInstagramSession? = null
)

enum class BrowserNavigationAction { RELOAD, STOP, BACK, FORWARD }

class BrowserViewModel(
    private val settings: AppSettingsRepository,
    private val savedWebsites: SavedWebsiteRepository,
    private val browserData: BrowserDataManager,
    private val instagramSessions: InstagramSessionStore,
    private val instagramResolver: InstagramResolver,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        BrowserUiState(
            currentUrl = savedStateHandle[KEY_CURRENT_URL],
            isDesktopSite = savedStateHandle[KEY_DESKTOP_SITE] ?: false
        )
    )
    val state = mutableState.asStateFlow()
    private var sessionJob: Job? = null
    internal var savedWebViewState: Bundle? = null
        private set

    internal fun saveWebViewState(pageUrl: String?, webViewState: Bundle) {
        // A disposed page must not revive history after Home, Clear All, or a new URL.
        if (pageUrl != null && pageUrl == mutableState.value.currentUrl) {
            savedWebViewState = webViewState
        }
    }

    init {
        refresh()
    }

    fun refresh() {
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            val useSessions = settings.useBrowserSessions()
            val session = if (useSessions) {
                withContext(Dispatchers.IO) { instagramSessions.load() }
            } else {
                null
            }
            if (useSessions && session != null) {
                instagramResolver.setSessionCookies(session.cookies, session.userAgent)
            } else {
                instagramResolver.clearSessionCookies()
            }
            mutableState.value = mutableState.value.copy(
                useSessions = useSessions,
                websites = savedWebsites.load(),
                savedSession = session,
                initialized = true
            )
        }
    }

    fun loadUrl(url: String) {
        savedStateHandle[KEY_CURRENT_URL] = url
        savedWebViewState = null
        mutableState.value = mutableState.value.copy(
            currentUrl = url,
            isLoading = true,
            progress = 15
        )
    }

    fun goHome() {
        savedStateHandle[KEY_CURRENT_URL] = null
        savedWebViewState = null
        mutableState.value = mutableState.value.copy(
            currentUrl = null,
            pageTitle = null,
            isLoading = false,
            progress = 0,
            canGoBack = false,
            canGoForward = false,
            navigationAction = null
        )
    }

    fun updatePageState(
        url: String?,
        title: String?,
        canGoBack: Boolean,
        canGoForward: Boolean,
        isSecure: Boolean
    ) {
        if (mutableState.value.currentUrl == null) return
        if (url != null) savedStateHandle[KEY_CURRENT_URL] = url
        mutableState.value = mutableState.value.copy(
            currentUrl = url ?: mutableState.value.currentUrl,
            pageTitle = title ?: mutableState.value.pageTitle,
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            isSecure = isSecure
        )
    }

    fun updateProgress(progress: Int, isLoading: Boolean) {
        if (mutableState.value.currentUrl == null) return
        mutableState.value = mutableState.value.copy(
            progress = progress,
            isLoading = isLoading
        )
    }

    fun toggleDesktopSite() {
        savedStateHandle[KEY_DESKTOP_SITE] = !mutableState.value.isDesktopSite
        mutableState.value = mutableState.value.copy(
            isDesktopSite = !mutableState.value.isDesktopSite
        )
    }

    fun reload() {
        mutableState.value = mutableState.value.copy(navigationAction = BrowserNavigationAction.RELOAD)
    }

    fun stop() {
        mutableState.value = mutableState.value.copy(navigationAction = BrowserNavigationAction.STOP)
    }

    fun goBack() {
        mutableState.value = mutableState.value.copy(navigationAction = BrowserNavigationAction.BACK)
    }

    fun goForward() {
        mutableState.value = mutableState.value.copy(navigationAction = BrowserNavigationAction.FORWARD)
    }

    fun consumeNavigationAction() {
        mutableState.value = mutableState.value.copy(navigationAction = null)
    }

    fun addWebsite(name: String, url: String, icon: Bitmap? = null) {
        savedWebsites.save(name, url, icon)
        mutableState.value = mutableState.value.copy(websites = savedWebsites.load())
    }

    fun recordVisit(url: String) = viewModelScope.launch(Dispatchers.IO) {
        savedWebsites.record(url)
    }

    fun updateWebsiteIcon(url: String, icon: Bitmap) = viewModelScope.launch(Dispatchers.IO) {
        savedWebsites.updateIcon(url, icon)
    }

    fun saveSession(session: dev.qtremors.acqua.data.session.SavedInstagramSession) =
        viewModelScope.launch(Dispatchers.IO) {
            instagramSessions.save(session)
            mutableState.value = mutableState.value.copy(savedSession = session)
        }

    fun removeWebsite(url: String) {
        val host = WebLink.host(url)?.removePrefix("www.") ?: return
        val existing = mutableState.value.websites.firstOrNull {
            it.host.equals(host, ignoreCase = true) ||
                it.origin.equals(WebLink.origin(url), ignoreCase = true)
        }
        val originToRemove = existing?.origin ?: WebLink.origin(url) ?: return
        savedWebsites.remove(listOf(originToRemove))
        mutableState.value = mutableState.value.copy(websites = savedWebsites.load())
    }

    fun setUseSessions(enabled: Boolean) {
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            settings.setUseBrowserSessions(enabled)
            val session = if (enabled) {
                withContext(Dispatchers.IO) { instagramSessions.load() }
            } else {
                null
            }
            if (enabled && session != null) {
                instagramResolver.setSessionCookies(session.cookies, session.userAgent)
            } else {
                instagramResolver.clearSessionCookies()
            }
            mutableState.value = mutableState.value.copy(useSessions = enabled)
        }
    }

    fun clearSelected(origins: Collection<String>) {
        browserData.clearWebsites(origins) { refresh() }
    }

    fun updateWebsite(originalOrigin: String, name: String, url: String) {
        savedWebsites.update(originalOrigin, name, url)
        mutableState.value = mutableState.value.copy(websites = savedWebsites.load())
    }

    fun clearAll() {
        sessionJob?.cancel()
        savedWebViewState = null
        // Stop the live page before clearing cookies so it cannot save them again.
        mutableState.value = BrowserUiState(initialized = true)
        browserData.clearAll {
            instagramResolver.clearSessionCookies()
        }
    }

    private companion object {
        const val KEY_CURRENT_URL = "browser.current_url"
        const val KEY_DESKTOP_SITE = "browser.desktop_site"
    }
}
