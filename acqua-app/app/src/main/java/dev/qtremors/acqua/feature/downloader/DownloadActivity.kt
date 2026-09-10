package dev.qtremors.acqua.feature.downloader

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import dev.qtremors.acqua.ui.components.AcquaFabAction
import dev.qtremors.acqua.ui.components.AcquaFloatingToolbar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.qtremors.acqua.MainDependencies
import dev.qtremors.acqua.R
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.feature.ViewModelFactory
import dev.qtremors.acqua.resolver.instagram.ExpiredSessionException
import dev.qtremors.acqua.resolver.web.RenderedPageResolverActivity
import dev.qtremors.acqua.ui.theme.AcquaTheme
import dev.qtremors.acqua.ui.security.SecureWindowEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DownloadActivity : ComponentActivity() {
    private val dependencies by lazy { MainDependencies(applicationContext) }
    private val resolutionCoordinator by viewModels<BrowserResolutionCoordinator>()
    private var pendingStorageAction: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingStorageAction
        pendingStorageAction = null
        if (granted) action?.invoke()
    }
    private var pendingNotificationAction: (() -> Unit)? = null
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            pendingNotificationAction?.invoke()
            pendingNotificationAction = null
        }

    private val resolverLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pending = resolutionCoordinator.pending ?: return@registerForActivityResult
        resolutionCoordinator.clear(pending)
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
        val sourceUrl = intent.getStringExtra(EXTRA_URL)?.let(WebLink::normalize)
        if (sourceUrl == null) {
            finish()
            return
        }
        dependencies.instagramSessions.load()?.let { session ->
            dependencies.instagramResolver.setSessionCookies(session.cookies, session.userAgent)
        }
        val browserMedia = RenderedPageResolverActivity.parseMediaJson(
            intent.getStringExtra(EXTRA_MEDIA_JSON).orEmpty()
        ).map { item ->
            item.copy(
                requestCookies = CookieManager.getInstance().getCookie(item.url)?.takeIf(String::isNotBlank),
                explicitBrowserSessionAuthorized = true
            )
        }
        val screenProtectionEnabled = dependencies.settings.screenProtectionEnabled()

        setContent {
            val themeState by dependencies.themePreferences.themeState.collectAsStateWithLifecycle(
                initialValue = dev.qtremors.acqua.ui.theme.ThemeState()
            )
            AcquaTheme(themeState = themeState) {
                SecureWindowEffect(enabled = screenProtectionEnabled)
                val downloader: DownloaderViewModel = viewModel(
                    factory = remember {
                        ViewModelFactory {
                            DownloaderViewModel(
                                dependencies.history,
                                dependencies.resolution,
                                dependencies.mediaStorage,
                                dependencies.settings,
                                dependencies.ytDlpEngine,
                                dependencies.ytDlpDownloads
                            )
                        }
                    }
                )
                LaunchedEffect(sourceUrl, browserMedia) {
                    if (browserMedia.isEmpty()) downloader.updateUrl(sourceUrl)
                    else downloader.seedResolvedMedia(sourceUrl, browserMedia)
                }
                val downloaderState by downloader.state.collectAsStateWithLifecycle()
                val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                val screenPadding = remember(statusBarTop, navBottom, imeBottom) {
                    val baseBottom = if (imeBottom > 0.dp) 0.dp else navBottom + 80.dp
                    PaddingValues(
                        top = statusBarTop,
                        bottom = baseBottom,
                        start = 0.dp,
                        end = 0.dp
                    )
                }

                val downloadFabAction = remember(downloaderState) {
                    val hasMedia = !downloaderState.media.isNullOrEmpty()
                    val canDownload = hasMedia && !downloaderState.isSaving && !downloaderState.saved && downloaderState.savingItemIndex == null
                    if (canDownload) {
                        AcquaFabAction(
                            icon = Icons.Filled.Download,
                            contentDescriptionRes = R.string.download,
                            onClick = {
                                runWithDownloadPermissions(true) {
                                    downloader.downloadAll(browserSessionsEnabled = true, browserResolver = ::resolveInBrowser)
                                }
                            }
                        )
                    } else null
                }

                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {}
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        DownloaderScreen(
                            viewModel = downloader,
                            mediaDownloader = dependencies.mediaDownloader,
                            useBrowserSessions = true,
                            sessionsInitialized = true,
                            browserRequestRevision = EXPLICIT_BROWSER_REQUEST_REVISION,
                            resolveInBrowser = ::resolveInBrowser,
                            requestDownloadAccess = ::runWithDownloadPermissions,
                            onOpenBrowser = { finish() },
                            contentPadding = screenPadding,
                            modifier = Modifier.fillMaxSize(),
                            showLinkEditor = false
                        )

                        AcquaFloatingToolbar(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .zIndex(1f),
                            title = stringResource(R.string.download),
                            onBackClick = ::finish,
                            fabAction = downloadFabAction
                        )
                    }
                }
            }
        }
    }

    private suspend fun resolveInBrowser(
        url: String,
        explicitSessionAuthorization: Boolean
    ): List<ResolvedMedia> = withContext(Dispatchers.Main.immediate) {
        val pending = resolutionCoordinator.begin(explicitSessionAuthorization)
        resolverLauncher.launch(
            Intent(this@DownloadActivity, RenderedPageResolverActivity::class.java)
                .putExtra(RenderedPageResolverActivity.EXTRA_URL, url)
        )
        try {
            pending.deferred.await()
        } finally {
            resolutionCoordinator.clear(pending)
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

    private fun runWithDownloadPermissions(needsNotification: Boolean, action: () -> Unit) {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingStorageAction = { runWithDownloadPermissions(needsNotification, action) }
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else if (needsNotification) {
            runWithNotificationPermission(action)
        } else {
            action()
        }
    }

    private fun runWithNotificationPermission(action: () -> Unit) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingNotificationAction = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else action()
    }

    companion object {
        const val EXTRA_URL = "download_url"
        const val EXTRA_MEDIA_JSON = "download_media_json"
        private const val EXPLICIT_BROWSER_REQUEST_REVISION = 1
    }
}
