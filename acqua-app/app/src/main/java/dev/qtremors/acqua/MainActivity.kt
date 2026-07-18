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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.qtremors.acqua.auth.BrowserActivity
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.feature.DashboardScreen
import dev.qtremors.acqua.feature.ViewModelFactory
import dev.qtremors.acqua.feature.browser.BrowserViewModel
import dev.qtremors.acqua.feature.downloader.DownloaderViewModel
import dev.qtremors.acqua.feature.history.HistoryViewModel
import dev.qtremors.acqua.feature.settings.SettingsViewModel
import dev.qtremors.acqua.resolver.instagram.ExpiredSessionException
import dev.qtremors.acqua.resolver.web.RenderedPageResolverActivity
import dev.qtremors.acqua.ui.theme.AcquaTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val dependencies by lazy { MainDependencies(applicationContext) }
    private var pendingResolution: CompletableDeferred<List<ResolvedMedia>>? = null
    private var pendingStorageAction: (() -> Unit)? = null
    private var browserDownloadUrl by mutableStateOf<String?>(null)
    private var browserRevision by mutableIntStateOf(0)
    private var sessionsEnabled = false

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingStorageAction
        pendingStorageAction = null
        if (granted) action?.invoke()
    }

    private val browserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.takeIf { it.getBooleanExtra(BrowserActivity.EXTRA_DOWNLOAD_CURRENT_PAGE, false) }
                ?.getStringExtra(BrowserActivity.EXTRA_CURRENT_URL)
                ?.let(WebLink::normalize)
                ?.let { browserDownloadUrl = it }
            browserRevision++
        }
    }

    private val resolverLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pending = pendingResolution ?: return@registerForActivityResult
        pendingResolution = null
        refreshInstagramSession()
        when (result.resultCode) {
            Activity.RESULT_OK -> completeMediaResult(pending, result.data)
            RenderedPageResolverActivity.RESULT_SESSION_EXPIRED -> pending.completeExceptionally(
                ExpiredSessionException(
                    result.data?.getStringExtra(RenderedPageResolverActivity.EXTRA_ERROR)
                        ?: getString(R.string.session_expired)
                )
            )
            else -> pending.completeExceptionally(
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
                    browserDownloadUrl,
                    browserRevision,
                    onBrowserDownloadConsumed = { browserDownloadUrl = null },
                    resolveInBrowser = ::resolveInBrowser,
                    requestStorageAccess = ::runWithStoragePermission,
                    openBrowser = ::openBrowser,
                    onSessionStateChanged = { sessionsEnabled = it }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingUrl(intent).takeIf(String::isNotBlank)?.let { browserDownloadUrl = it }
    }

    override fun onDestroy() {
        pendingResolution?.completeExceptionally(CancellationException("Activity destroyed"))
        pendingResolution = null
        super.onDestroy()
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

    private suspend fun resolveInBrowser(url: String): List<ResolvedMedia> = withContext(Dispatchers.Main.immediate) {
        pendingResolution?.completeExceptionally(CancellationException("A newer request replaced this one."))
        val deferred = CompletableDeferred<List<ResolvedMedia>>()
        pendingResolution = deferred
        resolverLauncher.launch(
            Intent(this@MainActivity, RenderedPageResolverActivity::class.java)
                .putExtra(RenderedPageResolverActivity.EXTRA_URL, url)
        )
        try {
            deferred.await()
        } finally {
            if (pendingResolution === deferred) pendingResolution = null
        }
    }

    private fun completeMediaResult(pending: CompletableDeferred<List<ResolvedMedia>>, data: Intent?) {
        runCatching {
            RenderedPageResolverActivity.parseMediaResults(data).map { item ->
                item.copy(
                    requestCookies = if (sessionsEnabled) {
                        CookieManager.getInstance().getCookie(item.url)?.takeIf(String::isNotBlank)
                    } else null
                )
            }
        }.onSuccess { media ->
            if (media.isEmpty()) pending.completeExceptionally(Exception(getString(R.string.browser_no_media)))
            else pending.complete(media)
        }.onFailure {
            pending.completeExceptionally(Exception(getString(R.string.browser_invalid_media), it))
        }
    }

    private fun refreshInstagramSession() {
        if (!sessionsEnabled) return dependencies.instagramResolver.clearSessionCookies()
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
