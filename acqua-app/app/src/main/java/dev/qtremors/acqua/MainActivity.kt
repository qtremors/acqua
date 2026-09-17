package dev.qtremors.acqua

import dev.qtremors.acqua.core.data.R

import android.annotation.SuppressLint
import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.os.StrictMode
import android.os.Trace
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import dev.qtremors.acqua.auth.BrowserActivity
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.feature.DashboardScreen
import dev.qtremors.acqua.feature.DashboardActions
import dev.qtremors.acqua.feature.DashboardRouteState
import dev.qtremors.acqua.feature.DashboardServices
import dev.qtremors.acqua.feature.DashboardStateHolders
import dev.qtremors.acqua.feature.ViewModelFactory
import dev.qtremors.acqua.feature.browser.BrowserViewModel
import dev.qtremors.acqua.feature.browser.BrowserDownloadRequest
import dev.qtremors.acqua.feature.downloader.DownloadActivity
import dev.qtremors.acqua.feature.downloader.BrowserResolutionCoordinator
import dev.qtremors.acqua.feature.downloader.DownloaderViewModel
import dev.qtremors.acqua.feature.downloader.PendingBrowserResolution
import dev.qtremors.acqua.feature.downloader.history.HistoryViewModel
import dev.qtremors.acqua.feature.settings.SettingsViewModel
import dev.qtremors.acqua.feature.updater.FeedsViewModel
import dev.qtremors.acqua.resolver.instagram.ExpiredSessionException
import dev.qtremors.acqua.resolver.web.RenderedPageResolverActivity
import dev.qtremors.acqua.platform.permissions.DownloadPermissionDialogs
import dev.qtremors.acqua.platform.permissions.DownloadPermissionGate
import dev.qtremors.acqua.platform.permissions.DownloadPermissionPrompt
import dev.qtremors.acqua.ui.theme.AcquaTheme
import dev.qtremors.acqua.ui.theme.ThemeState
import dev.qtremors.acqua.ui.security.SecureWindowEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.system.exitProcess

// Notification permission constants are only reached through explicit API 33 runtime guards.
@SuppressLint("InlinedApi")
class MainActivity : ComponentActivity() {
    private val dependencies by lazy { (application as AcquaApp).dependencies }
    private val browserResolutionCoordinator by viewModels<BrowserResolutionCoordinator>()
    private val downloadPermissionGate by viewModels<DownloadPermissionGate>()

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            downloadPermissionGate.resumePending()?.let { pending ->
                runWithDownloadPermissions(pending.needsNotification, pending.action)
            }
        } else {
            downloadPermissionGate.show(
                if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    DownloadPermissionPrompt.STORAGE_RATIONALE
                } else {
                    DownloadPermissionPrompt.STORAGE_SETTINGS
                }
            )
        }
    }
    private var pendingNotificationResult: ((Boolean) -> Unit)? = null
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val onboardingResult = pendingNotificationResult
            pendingNotificationResult = null
            if (onboardingResult != null) {
                onboardingResult(granted)
            } else if (granted) {
                downloadPermissionGate.runPendingWithoutNotifications()
            } else {
                downloadPermissionGate.show(DownloadPermissionPrompt.NOTIFICATION_DENIED)
            }
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
        val appLaunchContext = (application as? AcquaApp)
            ?.appSessionTracker
            ?.onMainActivityCreated(hasSavedInstanceState = savedInstanceState != null)
        installDebugStrictMode()
        val splashScreen = traceStartupSection("Acqua.installSplashScreen") {
            installSplashScreen()
        }
        traceStartupSection("Acqua.activityOnCreate") {
            super.onCreate(savedInstanceState)
        }

        enableEdgeToEdge()
        var keepSplashScreen = true
        lifecycleScope.launch {
            try {
                traceStartupSection("Acqua.splashPreferencePreload") {
                    withTimeoutOrNull(2000L) {
                        dependencies.themePreferences.themeState.first()
                        dependencies.onboardingPreferences.onboardingState.first()
                    }
                }
            } finally {
                keepSplashScreen = false
            }
        }
        splashScreen.setKeepOnScreenCondition { keepSplashScreen }

        val initialUrl = incomingUrl(intent)

        traceStartupSection("Acqua.setContent") {
            setContent {
                val themeState by dependencies.themePreferences.themeState.collectAsStateWithLifecycle(
                    initialValue = ThemeState()
                )
                val onboardingState by dependencies.onboardingPreferences.onboardingState.collectAsStateWithLifecycle(
                    initialValue = dev.qtremors.acqua.data.onboarding.OnboardingState()
                )
                val coroutineScope = rememberCoroutineScope()
                val permissionPrompt by downloadPermissionGate.prompt.collectAsStateWithLifecycle()

                AcquaTheme(themeState = themeState) {
                    DownloadPermissionDialogs(
                        prompt = permissionPrompt,
                        onDismiss = downloadPermissionGate::dismiss,
                        onRequestStorage = {
                            downloadPermissionGate.markStorageRequested()
                            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        },
                        onOpenSettings = {
                            downloadPermissionGate.dismiss()
                            openAppSettings()
                        },
                        onRequestNotifications = {
                            downloadPermissionGate.markNotificationsRequested()
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onContinueWithoutNotifications = downloadPermissionGate::runPendingWithoutNotifications
                    )
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val onboardingViewModel: dev.qtremors.acqua.feature.onboarding.OnboardingViewModel = viewModel(
                            factory = remember {
                                ViewModelFactory { _ ->
                                    dev.qtremors.acqua.feature.onboarding.OnboardingViewModel(
                                        dependencies.onboardingPreferences,
                                        dependencies.backupManager
                                    )
                                }
                            }
                        )

                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                            } else true
                            onboardingViewModel.updatePermissionState(
                                hasStoragePermission = true,
                                hasNotificationPermission = hasNotif,
                                notificationPermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            )
                        }

                        if (!onboardingState.isCompleted) {
                            val onboardingUiState by onboardingViewModel.state.collectAsStateWithLifecycle()
                            dev.qtremors.acqua.feature.onboarding.ui.OnboardingScreen(
                                state = onboardingUiState,
                                currentThemeState = themeState,
                                onThemeChange = { newState ->
                                    coroutineScope.launch {
                                        dependencies.themePreferences.saveThemeState(newState)
                                    }
                                },
                                onNext = onboardingViewModel::next,
                                onBack = onboardingViewModel::back,
                                onStepSelected = onboardingViewModel::setStep,
                                onRequestNotificationPermission = {
                                    requestNotificationPermission { granted ->
                                        onboardingViewModel.handleNotificationPermissionResult()
                                        onboardingViewModel.updatePermissionState(
                                            hasStoragePermission = true,
                                            hasNotificationPermission = granted,
                                            notificationPermissionRequired = true
                                        )
                                    }
                                },
                                restoreState = onboardingUiState.restoreState,
                                onChooseRestoreBackup = onboardingViewModel::previewBackup,
                                onApplyRestoreBackup = onboardingViewModel::applyRestoreBackup,
                                onDismissRestoreBackup = onboardingViewModel::dismissRestoreBackup,
                                onRestartApp = ::restartApp
                            )
                        } else {
                            val downloader: DownloaderViewModel = viewModel(
                                factory = remember {
                                    ViewModelFactory { extras ->
                                        DownloaderViewModel(
                                            dependencies.history,
                                            dependencies.resolution,
                                            dependencies.settings,
                                            dependencies.ytDlpEngine,
                                            dependencies.ytDlpDownloads,
                                            extras.createSavedStateHandle()
                                        )
                                    }
                                }
                            )
                            val browser: BrowserViewModel = viewModel(
                                factory = remember {
                                    ViewModelFactory { extras ->
                                        BrowserViewModel(
                                            dependencies.settings,
                                            dependencies.savedWebsites,
                                            dependencies.browserData,
                                            dependencies.instagramSessions,
                                            dependencies.instagramResolver,
                                            extras.createSavedStateHandle()
                                        )
                                    }
                                }
                            )
                            val history: HistoryViewModel = viewModel(
                                factory = remember {
                                    ViewModelFactory { extras ->
                                        HistoryViewModel(dependencies.history, extras.createSavedStateHandle())
                                    }
                                }
                            )
                            val settings: SettingsViewModel = viewModel(
                                factory = remember {
                                    ViewModelFactory { _ ->
                                        SettingsViewModel(dependencies.settings, dependencies.ytDlpMaintenance)
                                    }
                                }
                            )
                            val feeds: FeedsViewModel = viewModel(
                                factory = remember {
                                    ViewModelFactory { _ ->
                                        FeedsViewModel(
                                            dependencies.trackedRepos,
                                            dependencies.githubApi,
                                            dependencies.githubAuth,
                                            dependencies.installedAppMatcher,
                                            dependencies.appUpdater
                                        )
                                    }
                                }
                            )
                            val settingsState by settings.state.collectAsStateWithLifecycle()
                            SecureWindowEffect(enabled = settingsState.screenProtectionEnabled)
                            DashboardScreen(
                                stateHolders = DashboardStateHolders(
                                    downloader, browser, history, settings, feeds
                                ),
                                services = DashboardServices(
                                    dependencies.mediaDownloader,
                                    dependencies.fileActions,
                                    dependencies.backupManager
                                ),
                                routeState = DashboardRouteState(
                                    initialUrl,
                                    browserResolutionCoordinator.urlHandoff,
                                    browserResolutionCoordinator.browserRevision,
                                    browserResolutionCoordinator.downloadRequestRevision,
                                    themeState
                                ),
                                actions = DashboardActions(
                                    onThemeChange = { newState ->
                                    coroutineScope.launch {
                                        dependencies.themePreferences.saveThemeState(newState)
                                    }
                                },
                                    onUrlHandoffConsumed = browserResolutionCoordinator::consumeUrlHandoff,
                                    resolveInBrowser = ::resolveInBrowser,
                                    requestDownloadAccess = ::runWithDownloadPermissions,
                                    openBrowser = ::openBrowser,
                                    onBrowserDownloadRequest = ::openBrowserDownload
                                )
                            )
                        }
                    }
                }
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

    private fun openBrowserDownload(request: BrowserDownloadRequest) {
        startActivity(
            Intent(this, DownloadActivity::class.java)
                .putExtra(DownloadActivity.EXTRA_URL, request.sourceUrl)
                .putExtra(DownloadActivity.EXTRA_MEDIA_JSON, request.mediaJson)
        )
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

    private fun runWithDownloadPermissions(needsNotification: Boolean, action: () -> Unit) {
        downloadPermissionGate.hold(needsNotification, action)
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            if (downloadPermissionGate.hasRequestedStorage) {
                downloadPermissionGate.show(
                    if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        DownloadPermissionPrompt.STORAGE_RATIONALE
                    } else {
                        DownloadPermissionPrompt.STORAGE_SETTINGS
                    }
                )
            } else {
                downloadPermissionGate.markStorageRequested()
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else if (needsNotification) {
            runWithNotificationPermission(action)
        } else downloadPermissionGate.runPendingWithoutNotifications()
    }

    private fun runWithNotificationPermission(action: () -> Unit) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            downloadPermissionGate.hold(needsNotification = true, action)
            downloadPermissionGate.show(
                if (downloadPermissionGate.hasRequestedNotifications &&
                    !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                ) DownloadPermissionPrompt.NOTIFICATION_DENIED
                else DownloadPermissionPrompt.NOTIFICATION_EXPLANATION
            )
        } else downloadPermissionGate.runPendingWithoutNotifications()
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData("package:$packageName".toUri())
        )
    }

    private fun requestNotificationPermission(onResult: (Boolean) -> Unit) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            onResult(true)
        } else {
            pendingNotificationResult = onResult
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun restartApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        if (launchIntent != null) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.set(
                AlarmManager.RTC,
                System.currentTimeMillis() + 250L,
                pendingIntent
            )
            finishAffinity()
            Process.killProcess(Process.myPid())
            exitProcess(0)
        } else {
            recreate()
        }
    }

    private fun installDebugStrictMode() {
        if (!BuildConfig.DEBUG) return

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build()
        )
    }

    private inline fun <T> traceStartupSection(name: String, block: () -> T): T {
        Trace.beginSection(name)
        return try {
            block()
        } finally {
            Trace.endSection()
        }
    }
}
