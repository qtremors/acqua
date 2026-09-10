package dev.qtremors.acqua.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.qtremors.acqua.data.session.BrowserDataManager
import dev.qtremors.acqua.data.session.InstagramSessionStore
import dev.qtremors.acqua.data.session.SavedWebsite
import dev.qtremors.acqua.data.session.SavedWebsiteRepository
import dev.qtremors.acqua.data.settings.AppSettingsRepository
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
    val navigationAction: BrowserNavigationAction? = null
)

enum class BrowserNavigationAction { RELOAD, STOP, BACK, FORWARD }

class BrowserViewModel(
    private val settings: AppSettingsRepository,
    private val savedWebsites: SavedWebsiteRepository,
    private val browserData: BrowserDataManager,
    private val instagramSessions: InstagramSessionStore,
    private val instagramResolver: InstagramResolver
) : ViewModel() {
    private val mutableState = MutableStateFlow(BrowserUiState())
    val state = mutableState.asStateFlow()
    private var sessionJob: Job? = null

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
                initialized = true
            )
        }
    }

    fun loadUrl(url: String) {
        mutableState.value = mutableState.value.copy(
            currentUrl = url,
            isLoading = true,
            progress = 15
        )
    }

    fun goHome() {
        mutableState.value = mutableState.value.copy(
            currentUrl = null,
            pageTitle = null,
            isLoading = false,
            progress = 0,
            canGoBack = false,
            canGoForward = false
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

    fun addWebsite(name: String, url: String) {
        savedWebsites.save(name, url, null)
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
        // Stop the live page before clearing cookies so it cannot save them again.
        mutableState.value = BrowserUiState(initialized = true)
        browserData.clearAll {
            instagramResolver.clearSessionCookies()
        }
    }
}
