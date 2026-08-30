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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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

    @OptIn(ExperimentalMaterial3Api::class)
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
                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text(stringResource(R.string.download)) },
                            navigationIcon = {
                                IconButton(onClick = ::finish) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        stringResource(R.string.return_to_browser)
                                    )
                                }
                            }
                        )
                    }
                ) { padding ->
                    DownloaderScreen(
                        viewModel = downloader,
                        mediaDownloader = dependencies.mediaDownloader,
                        useBrowserSessions = true,
                        sessionsInitialized = true,
                        browserRequestRevision = EXPLICIT_BROWSER_REQUEST_REVISION,
                        resolveInBrowser = ::resolveInBrowser,
                        requestDownloadAccess = ::runWithDownloadPermissions,
                        onOpenBrowser = { finish() },
                        showLinkEditor = false,
                        modifier = Modifier.padding(padding)
                    )
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
