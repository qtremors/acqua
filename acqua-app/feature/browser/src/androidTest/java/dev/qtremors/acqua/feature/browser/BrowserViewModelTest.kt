package dev.qtremors.acqua.feature.browser

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.qtremors.acqua.data.session.BrowserDataManager
import dev.qtremors.acqua.data.session.InstagramSessionStore
import dev.qtremors.acqua.data.session.SavedWebsiteRepository
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.resolver.instagram.InstagramResolver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserViewModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun createViewModel(): Pair<BrowserViewModel, AppSettingsRepository> {
        val settings = AppSettingsRepository(context)
        settings.setUseBrowserSessions(false)
        val websites = SavedWebsiteRepository(context)
        val sessions = InstagramSessionStore(context)
        lateinit var viewModel: BrowserViewModel
        instrumentation.runOnMainSync {
            viewModel = BrowserViewModel(
                settings, websites, BrowserDataManager(context, websites, sessions, settings),
                sessions, InstagramResolver(), SavedStateHandle()
            )
        }
        return viewModel to settings
    }

    @Test
    fun clearingAllDataDisablesSessionReuseAndClosesTheLivePage() {
        val (viewModel, settings) = createViewModel()
        instrumentation.runOnMainSync { viewModel.setUseSessions(true) }
        runBlocking {
            withTimeout(5_000) { viewModel.state.first { it.useSessions } }
        }
        instrumentation.runOnMainSync {
            viewModel.loadUrl("https://example.com")
            viewModel.clearAll()
            assertFalse(viewModel.state.value.useSessions)
            assertFalse(settings.useBrowserSessions())
            assertNull(viewModel.state.value.currentUrl)
            assertTrue(viewModel.state.value.websites.isEmpty())

            // A queued callback from the discarded WebView must not reopen it.
            viewModel.updatePageState("https://example.com", "Old page", true, true, true)
            viewModel.updateProgress(50, true)
            assertNull(viewModel.state.value.currentUrl)
            assertFalse(viewModel.state.value.isLoading)
        }
    }

    @Test
    fun returningHomeIgnoresLatePageCallbacks() {
        val (viewModel, _) = createViewModel()
        instrumentation.runOnMainSync {
            viewModel.loadUrl("https://example.com")
            viewModel.goHome()
            viewModel.updatePageState("https://example.com", "Old page", true, false, true)
            viewModel.updateProgress(100, false)
            assertNull(viewModel.state.value.currentUrl)
            assertFalse(viewModel.state.value.canGoBack)
        }
    }

    @Test
    fun historyIsRetainedOnlyForTheCurrentPage() {
        val (viewModel, _) = createViewModel()
        instrumentation.runOnMainSync {
            val url = "https://example.com"
            val history = Bundle().apply { putString("history", "saved") }
            viewModel.loadUrl(url)
            viewModel.saveWebViewState(url, history)
            assertSame(history, viewModel.savedWebViewState)

            viewModel.goHome()
            viewModel.saveWebViewState(url, history)
            assertNull(viewModel.savedWebViewState)

            viewModel.loadUrl(url)
            viewModel.saveWebViewState(url, history)
            viewModel.loadUrl("https://example.org")
            viewModel.saveWebViewState(url, history)
            assertNull(viewModel.savedWebViewState)

            viewModel.loadUrl(url)
            viewModel.saveWebViewState(url, history)
            viewModel.clearAll()
            viewModel.saveWebViewState(url, history)
            assertNull(viewModel.savedWebViewState)
        }
    }

    @Test
    fun addingAndRemovingWebsitesUpdatesUiState() {
        val (viewModel, _) = createViewModel()
        instrumentation.runOnMainSync {
            val url = "https://example.com/page"
            viewModel.addWebsite("Example", url)
            assertEquals(1, viewModel.state.value.websites.size)
            assertEquals("example.com", viewModel.state.value.websites.first().host)

            viewModel.removeWebsite(url)
            assertTrue(viewModel.state.value.websites.isEmpty())
        }
    }
}
