package dev.qtremors.acqua

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.qtremors.acqua.auth.BrowserActivity
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.feature.DashboardScreen
import dev.qtremors.acqua.feature.ViewModelFactory
import dev.qtremors.acqua.feature.browser.BrowserViewModel
import dev.qtremors.acqua.feature.downloader.BrowserResolutionCoordinator
import dev.qtremors.acqua.feature.downloader.DownloaderViewModel
import dev.qtremors.acqua.feature.downloader.PendingBrowserResolution
import dev.qtremors.acqua.feature.history.HistoryViewModel
import dev.qtremors.acqua.feature.settings.SettingsViewModel
import dev.qtremors.acqua.resolver.instagram.ExpiredSessionException
import dev.qtremors.acqua.resolver.web.RenderedPageResolverActivity
import dev.qtremors.acqua.ui.theme.AcquaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val dependencies by lazy { MainDependencies(applicationContext) }
    private val browserResolutionCoordinator by viewModels<BrowserResolutionCoordinator>()
    private var pendingStorageAction: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingStorageAction
        pendingStorageAction = null
        if (granted) action?.invoke()
    }

    private val browserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            browserResolutionCoordinator.recordBrowserReturn()
        }
    }

    private val resolverLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pending = browserResolutionCoordinator.pending ?: return@registerForActivityResult
        browserResolutionCoordinator.clear(pending)
        refreshInstagramSession()
        when (result.resultCode) {
            Activity.RESULT_OK -> completeMediaResult(pending, result.data)
            RenderedPageResolverActivity.RESULT_SESSION_EXPIRED -> pending.deferred.completeExceptionally(
                ExpiredSessionException(
                    result.data?.getStringExtra(RenderedPageResolverActivity.EXTRA_ERROR)
                        ?: getString(R.string.session_expired)
                )
            )
            else -> pending.deferred.completeExceptionally(
                Exception(
                    result.data?.getStringExtra(RenderedPageResolverActivity.EXTRA_ERROR)
                        ?: getString(R.string.browser_resolution_failed)
                )
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialUrl = incomingUrl(intent)
        setContent {
            AcquaTheme {
                val downloader: DownloaderViewModel = viewModel(
                    factory = remember { ViewModelFactory {
                        DownloaderViewModel(dependencies.history, dependencies.resolution, dependencies.mediaStorage)
                    } }
                )
                val browser: BrowserViewModel = viewModel(
                    factory = remember { ViewModelFactory {
                        BrowserViewModel(
                            dependencies.settings,
                            dependencies.savedWebsites,
                            dependencies.browserData,
                            dependencies.instagramSessions,
                            dependencies.instagramResolver
                        )
                    } }
                )
                val history: HistoryViewModel = viewModel(
                    factory = remember { ViewModelFactory { HistoryViewModel(dependencies.history) } }
                )
                val settings: SettingsViewModel = viewModel(
                    factory = remember { ViewModelFactory { SettingsViewModel(dependencies.settings) } }
                )
                DashboardScreen(
                    downloader,
                    browser,
                    history,
                    settings,
                    dependencies.mediaDownloader,
                    dependencies.fileActions,
                    initialUrl,
                    browserResolutionCoordinator.urlHandoff,
                    browserResolutionCoordinator.browserRevision,
                    browserResolutionCoordinator.downloadRequestRevision,
                    onUrlHandoffConsumed = browserResolutionCoordinator::consumeUrlHandoff,
                    resolveInBrowser = ::resolveInBrowser,
                    requestStorageAccess = ::runWithStoragePermission,
                    openBrowser = ::openBrowser
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingUrl(intent).takeIf(String::isNotBlank)?.let {
            browserResolutionCoordinator.acceptExternalUrl(it)
        }
    }

    private fun incomingUrl(intent: Intent): String =
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            WebLink.extractFirst(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()).orEmpty()
        } else ""

    private fun openBrowser(initialUrl: String?, addWebsite: Boolean, name: String?) {
        browserLauncher.launch(Intent(this, BrowserActivity::class.java).apply {
            putExtra(BrowserActivity.EXTRA_ADD_LOGIN, addWebsite)
            name?.takeIf(String::isNotBlank)?.let { putExtra(BrowserActivity.EXTRA_LOGIN_NAME, it) }
            WebLink.normalize(initialUrl.orEmpty())?.let { putExtra(BrowserActivity.EXTRA_INITIAL_URL, it) }
        })
    }

    private suspend fun resolveInBrowser(
        url: String,
        explicitSessionAuthorization: Boolean
    ): List<ResolvedMedia> = withContext(Dispatchers.Main.immediate) {
        val pending = browserResolutionCoordinator.begin(explicitSessionAuthorization)
        resolverLauncher.launch(
            Intent(this@MainActivity, RenderedPageResolverActivity::class.java)
                .putExtra(RenderedPageResolverActivity.EXTRA_URL, url)
        )
        try {
            pending.deferred.await()
        } finally {
            browserResolutionCoordinator.clear(pending)
        }
    }

    private fun completeMediaResult(pending: PendingBrowserResolution, data: Intent?) {
        runCatching {
            RenderedPageResolverActivity.parseMediaResults(data).map { item ->
                item.copy(
                    requestCookies = CookieManager.getInstance().getCookie(item.url)
                        ?.takeIf(String::isNotBlank),
                    explicitBrowserSessionAuthorized = pending.explicitSessionAuthorization
                )
            }
        }.onSuccess { media ->
            if (media.isEmpty()) {
                pending.deferred.completeExceptionally(Exception(getString(R.string.browser_no_media)))
            } else {
                pending.deferred.complete(media)
            }
        }.onFailure {
            pending.deferred.completeExceptionally(Exception(getString(R.string.browser_invalid_media), it))
        }
    }

    private fun refreshInstagramSession() {
        if (!dependencies.settings.useBrowserSessions()) {
            return dependencies.instagramResolver.clearSessionCookies()
        }
        dependencies.instagramSessions.load()?.let {
            dependencies.instagramResolver.setSessionCookies(it.cookies, it.userAgent)
        } ?: dependencies.instagramResolver.clearSessionCookies()
    }

    private fun runWithStoragePermission(action: () -> Unit) {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingStorageAction = action
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else action()
    }
}
