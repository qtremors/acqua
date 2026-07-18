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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BrowserUiState(
    val useSessions: Boolean = false,
    val websites: List<SavedWebsite> = emptyList(),
    val initialized: Boolean = false
)

class BrowserViewModel(
    private val settings: AppSettingsRepository,
    private val savedWebsites: SavedWebsiteRepository,
    private val browserData: BrowserDataManager,
    private val instagramSessions: InstagramSessionStore,
    private val instagramResolver: InstagramResolver
) : ViewModel() {
    private val mutableState = MutableStateFlow(BrowserUiState())
    val state = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        val useSessions = settings.useBrowserSessions()
        val session = withContext(Dispatchers.IO) { instagramSessions.load() }
        if (useSessions && session != null) {
            instagramResolver.setSessionCookies(session.cookies, session.userAgent)
        } else {
            instagramResolver.clearSessionCookies()
        }
        mutableState.value = BrowserUiState(useSessions, savedWebsites.load(), initialized = true)
    }

    fun setUseSessions(enabled: Boolean) = viewModelScope.launch {
        settings.setUseBrowserSessions(enabled)
        val session = if (enabled) withContext(Dispatchers.IO) { instagramSessions.load() } else null
        if (enabled && session != null) {
            instagramResolver.setSessionCookies(session.cookies, session.userAgent)
        } else {
            instagramResolver.clearSessionCookies()
        }
        mutableState.value = mutableState.value.copy(useSessions = enabled)
    }

    fun clearSelected(origins: Collection<String>) {
        browserData.clearWebsites(origins) { refresh() }
    }

    fun clearAll() {
        browserData.clearAll {
            instagramResolver.clearSessionCookies()
            mutableState.value = BrowserUiState(initialized = true)
        }
    }
}
