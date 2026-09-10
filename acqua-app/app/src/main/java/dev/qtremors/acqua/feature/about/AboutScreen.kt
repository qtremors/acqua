package dev.qtremors.acqua.feature.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import dev.qtremors.acqua.platform.HapticSignal
import dev.qtremors.acqua.platform.performHaptic
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.qtremors.acqua.BuildConfig
import dev.qtremors.acqua.R
import dev.qtremors.acqua.data.updater.AppUpdateInfo
import dev.qtremors.acqua.data.updater.AppUpdater
import dev.qtremors.acqua.data.updater.UpdateDownloadState
import dev.qtremors.acqua.ui.components.SettingsActionRow
import dev.qtremors.acqua.ui.components.SettingsSection
import dev.qtremors.acqua.ui.updater.AppUpdateDialog
import kotlinx.coroutines.launch

@Composable
fun AboutScreen(
    appUpdater: dev.qtremors.acqua.data.updater.AppUpdater? = null,
    onOpenNotices: () -> Unit,
    onOpenLicense: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val uriHandler = LocalUriHandler.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val clipboardLabel = stringResource(R.string.app_name)
    val buildInfo = AboutBuildInfo(
        versionName = BuildConfig.VERSION_NAME,
        applicationId = BuildConfig.APPLICATION_ID,
        buildType = BuildConfig.BUILD_TYPE
    )
    val device = deviceDescription(Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE)

    var isCheckingUpdates by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var updateInfo by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<dev.qtremors.acqua.data.updater.AppUpdateInfo?>(null) }
    var downloadState by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<dev.qtremors.acqua.data.updater.UpdateDownloadState>(dev.qtremors.acqua.data.updater.UpdateDownloadState.Idle) }
    val currentDownloadState by rememberUpdatedState(downloadState)

    val launchInstaller: (java.io.File) -> Unit = { apkFile ->
        appUpdater?.installApk(apkFile)?.fold(
            onSuccess = {
                updateInfo = null
                downloadState = dev.qtremors.acqua.data.updater.UpdateDownloadState.Idle
            },
            onFailure = { error ->
                downloadState = dev.qtremors.acqua.data.updater.UpdateDownloadState.Error(
                    error.message ?: "Could not open the Android installer"
                )
            }
        )
    }

    DisposableEffect(lifecycleOwner, appUpdater) {
        val observer = LifecycleEventObserver { _, event ->
            val state = currentDownloadState
            if (
                event == Lifecycle.Event.ON_RESUME &&
                state is dev.qtremors.acqua.data.updater.UpdateDownloadState.Downloaded &&
                appUpdater?.canRequestPackageInstalls() == true
            ) {
                launchInstaller(state.apkFile)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val copyText: (String) -> Unit = { text ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(clipboardLabel, text))
        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }
    val openLink: (AboutExternalLink) -> Unit = { link -> uriHandler.openUri(link.url) }

    val handleCheckUpdates: () -> Unit = {
        if (appUpdater == null) {
            // No updater available
        } else if (appUpdater.isDebugBuild) {
            Toast.makeText(context, R.string.updates_disabled_in_debug, Toast.LENGTH_SHORT).show()
        } else {
            isCheckingUpdates = true
            coroutineScope.launch {
                appUpdater.checkForUpdate().fold(
                    onSuccess = { info ->
                        isCheckingUpdates = false
                        if (info.isUpdateAvailable && info.apkDownloadUrl != null) {
                            updateInfo = info
                            downloadState = dev.qtremors.acqua.data.updater.UpdateDownloadState.Idle
                        } else if (info.isUpdateAvailable) {
                            Toast.makeText(context, R.string.update_no_compatible_apk, Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(
                                context,
                                resources.getString(R.string.app_up_to_date_description, info.currentVersionName),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onFailure = { error ->
                        isCheckingUpdates = false
                        Toast.makeText(
                            context,
                            resources.getString(R.string.update_check_failed, error.message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }
    }

    val scrollState = rememberScrollState()
    val overscrollOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y < 0 && overscrollOffset.value > 0) {
                    val toConsume =
                        if (overscrollOffset.value + available.y >= 0) available.y else -overscrollOffset.value
                    scope.launch {
                        overscrollOffset.snapTo(overscrollOffset.value + toConsume)
                    }
                    return Offset(0f, toConsume)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y > 0 && scrollState.value == 0) {
                    val prevValue = overscrollOffset.value
                    if (prevValue >= 350f) return Offset.Zero

                    val newValue = (prevValue + available.y * 0.5f).coerceAtMost(350f)
                    scope.launch {
                        overscrollOffset.snapTo(newValue)
                    }

                    // Tactile haptic feedback when stretched significantly
                    if (prevValue < 300f && newValue >= 300f) {
                        context.performHaptic(HapticSignal.CLICK)
                    }

                    return Offset(0f, available.y)
                }
                return super.onPostScroll(consumed, available, source)
            }
        }
    }

    var isStartupAnimationRunning by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isStartupAnimationRunning = true
    }

    val contentAlphaState = animateFloatAsState(
        targetValue = if (isStartupAnimationRunning) 1f else 0f,
        animationSpec = tween(durationMillis = 400, delayMillis = 0, easing = EaseOut),
        label = "contentAlpha"
    )

    val contentOffsetState = animateDpAsState(
        targetValue = if (isStartupAnimationRunning) 0.dp else 40.dp,
        animationSpec = tween(durationMillis = 400, delayMillis = 0, easing = FastOutSlowInEasing),
        label = "contentOffset"
    )

    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val effectivePadding = remember(contentPadding, layoutDirection) {
        PaddingValues(
            start = 16.dp + contentPadding.calculateStartPadding(layoutDirection),
            end = 16.dp + contentPadding.calculateEndPadding(layoutDirection),
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Release && overscrollOffset.value > 0) {
                            scope.launch {
                                overscrollOffset.animateTo(
                                    0f,
                                    spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                        }
                    }
                }
            }
            .verticalScroll(scrollState)
            .padding(effectivePadding),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        AboutHero(
            buildInfo = buildInfo,
            overscrollOffset = overscrollOffset.value,
            contentAlpha = { contentAlphaState.value },
            contentOffset = { contentOffsetState.value }
        )

        Box(
            modifier = Modifier.graphicsLayer {
                alpha = contentAlphaState.value
                translationY = contentOffsetState.value.toPx()
            }
        ) {
            SettingsSection(title = stringResource(R.string.about_app_info)) {
                SettingsActionRow(
                    index = 0,
                    count = 6,
                    title = stringResource(R.string.about_version),
                    description = buildInfo.displayVersion,
                    leadingIcon = Icons.Filled.Info,
                    onClick = { copyText("Acqua ${buildInfo.displayVersion}") }
                )
                SettingsActionRow(
                    index = 1,
                    count = 6,
                    title = stringResource(R.string.check_for_updates),
                    description = if (isCheckingUpdates) stringResource(R.string.checking_for_updates) else stringResource(R.string.check_for_updates_description),
                    leadingIcon = Icons.Filled.SystemUpdate,
                    onClick = handleCheckUpdates
                )
                SettingsActionRow(
                    index = 2,
                    count = 6,
                    title = stringResource(R.string.about_developer),
                    description = stringResource(R.string.about_developer_name),
                    leadingIcon = Icons.Filled.Code,
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = { openLink(AboutExternalLink.DEVELOPER) }
                )
                SettingsActionRow(
                    index = 3,
                    count = 6,
                    title = stringResource(R.string.about_repository),
                    description = stringResource(R.string.about_repository_address),
                    leadingIcon = Icons.Filled.Source,
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = { openLink(AboutExternalLink.REPOSITORY) }
                )
                SettingsActionRow(
                    index = 4,
                    count = 6,
                    title = stringResource(R.string.about_device),
                    description = device,
                    leadingIcon = Icons.Filled.PhoneAndroid,
                    onClick = { copyText(device) }
                )
                SettingsActionRow(
                    index = 5,
                    count = 6,
                    title = stringResource(R.string.about_package),
                    description = buildInfo.displayPackage,
                    leadingIcon = Icons.Filled.Info,
                    onClick = { copyText(buildInfo.displayPackage) }
                )
            }
        }

        Box(
            modifier = Modifier.graphicsLayer {
                alpha = contentAlphaState.value
                translationY = contentOffsetState.value.toPx()
            }
        ) {
            SettingsSection(title = stringResource(R.string.about_privacy)) {
                SettingsActionRow(
                    index = 0,
                    count = 1,
                    title = stringResource(R.string.about_privacy_policy),
                    description = stringResource(R.string.about_privacy_description),
                    leadingIcon = Icons.Filled.Lock,
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = { openLink(AboutExternalLink.PRIVACY) }
                )
            }
        }

        Box(
            modifier = Modifier.graphicsLayer {
                alpha = contentAlphaState.value
                translationY = contentOffsetState.value.toPx()
            }
        ) {
            SettingsSection(title = stringResource(R.string.about_support)) {
                SettingsActionRow(
                    index = 0,
                    count = 4,
                    title = stringResource(R.string.about_releases),
                    description = stringResource(R.string.about_releases_description),
                    leadingIcon = Icons.Filled.History,
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = { openLink(AboutExternalLink.RELEASES) }
                )
                SettingsActionRow(
                    index = 1,
                    count = 4,
                    title = stringResource(R.string.about_report_issue),
                    description = stringResource(R.string.about_report_issue_description),
                    leadingIcon = Icons.Filled.BugReport,
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = { openLink(AboutExternalLink.REPORT_ISSUE) }
                )
                SettingsActionRow(
                    index = 2,
                    count = 4,
                    title = stringResource(R.string.about_open_source_notices),
                    description = stringResource(R.string.about_open_source_notices_description),
                    leadingIcon = Icons.AutoMirrored.Filled.Assignment,
                    onClick = onOpenNotices
                )
                SettingsActionRow(
                    index = 3,
                    count = 4,
                    title = stringResource(R.string.about_license),
                    description = stringResource(R.string.about_license_description),
                    leadingIcon = Icons.Filled.Policy,
                    onClick = onOpenLicense
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }

    updateInfo?.let { info ->
        val downloadUrl = info.apkDownloadUrl
        val fileName = info.apkName ?: "acqua-v${info.latestVersionName}.apk"
        dev.qtremors.acqua.ui.updater.AppUpdateDialog(
            updateInfo = info,
            downloadState = downloadState,
            onDownloadAndInstall = {
                if (downloadUrl != null && appUpdater != null) {
                    downloadState = dev.qtremors.acqua.data.updater.UpdateDownloadState.Downloading(0, info.apkSizeBytes, 0)
                    coroutineScope.launch {
                        appUpdater.downloadUpdate(
                            downloadUrl = downloadUrl,
                            fileName = fileName,
                            expectedSizeBytes = info.apkSizeBytes,
                            expectedVersionCode = info.latestVersionCode,
                            onProgress = { downloaded, total, percent ->
                                downloadState = dev.qtremors.acqua.data.updater.UpdateDownloadState.Downloading(downloaded, total, percent)
                            }
                        ).fold(
                            onSuccess = { apkFile ->
                                downloadState = dev.qtremors.acqua.data.updater.UpdateDownloadState.Downloaded(apkFile)
                                if (appUpdater.canRequestPackageInstalls()) {
                                    launchInstaller(apkFile)
                                } else {
                                    runCatching { appUpdater.openInstallPermissionSettings() }.onFailure { error ->
                                        downloadState = dev.qtremors.acqua.data.updater.UpdateDownloadState.Error(
                                            error.message ?: "Could not open install permission settings"
                                        )
                                    }
                                }
                            },
                            onFailure = { error ->
                                downloadState = dev.qtremors.acqua.data.updater.UpdateDownloadState.Error(error.message ?: "Download failed")
                            }
                        )
                    }
                }
            },
            onInstallDownloadedApk = {
                val state = downloadState
                if (state is dev.qtremors.acqua.data.updater.UpdateDownloadState.Downloaded && appUpdater != null) {
                    if (appUpdater.canRequestPackageInstalls()) {
                        launchInstaller(state.apkFile)
                    } else {
                        runCatching { appUpdater.openInstallPermissionSettings() }.onFailure { error ->
                            downloadState = dev.qtremors.acqua.data.updater.UpdateDownloadState.Error(
                                error.message ?: "Could not open install permission settings"
                            )
                        }
                    }
                }
            },
            onDismiss = {
                updateInfo = null
                downloadState = dev.qtremors.acqua.data.updater.UpdateDownloadState.Idle
            }
        )
    }
}

@Composable
private fun AboutHero(
    buildInfo: AboutBuildInfo,
    overscrollOffset: Float = 0f,
    contentAlpha: () -> Float = { 1f },
    contentOffset: () -> Dp = { 0.dp }
) {
    val density = LocalDensity.current
    val extraSize = with(density) { overscrollOffset.toDp() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.acqua),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier
                .graphicsLayer {
                    alpha = contentAlpha()
                    translationY = contentOffset().toPx()
                    scaleX = 1f + (overscrollOffset / 1000f)
                    scaleY = 1f + (overscrollOffset / 1000f)
                }
                .size(96.dp + extraSize)
                .padding(bottom = 8.dp)
        )
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier
                .graphicsLayer {
                    alpha = contentAlpha()
                    translationY = contentOffset().toPx()
                }
                .padding(top = 4.dp)
        )
        Text(
            stringResource(R.string.about_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .graphicsLayer {
                    alpha = contentAlpha()
                    translationY = contentOffset().toPx()
                }
                .padding(top = 4.dp)
        )
        Text(
            buildInfo.displayVersion,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .graphicsLayer {
                    alpha = contentAlpha()
                    translationY = contentOffset().toPx()
                }
                .padding(top = 8.dp)
        )
    }
}
