@file:OptIn(ExperimentalLayoutApi::class)

package dev.qtremors.acqua.feature.settings

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.qtremors.acqua.BuildConfig
import dev.qtremors.acqua.R
import dev.qtremors.acqua.data.backup.PreferencesBackupManager
import dev.qtremors.acqua.data.updater.AppUpdateInfo
import dev.qtremors.acqua.data.updater.AppUpdater
import dev.qtremors.acqua.data.updater.UpdateDownloadState
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.FilenameFormatter
import dev.qtremors.acqua.downloader.YtDlpFailure
import dev.qtremors.acqua.downloader.YtDlpUpdateStatus
import dev.qtremors.acqua.feature.about.AboutBuildInfo
import dev.qtremors.acqua.feature.about.AboutExternalLink
import dev.qtremors.acqua.feature.about.deviceDescription
import dev.qtremors.acqua.platform.HapticSignal
import dev.qtremors.acqua.platform.WebViewUpdateManager
import dev.qtremors.acqua.platform.performHaptic
import dev.qtremors.acqua.ui.components.SettingsCardContainer
import dev.qtremors.acqua.ui.components.SettingsSection
import dev.qtremors.acqua.ui.components.SettingsSwitchRow
import dev.qtremors.acqua.ui.settings.AccentColorSelector
import dev.qtremors.acqua.ui.settings.ThemeModeSelector
import dev.qtremors.acqua.ui.theme.ThemePreset
import dev.qtremors.acqua.ui.theme.ThemeState
import dev.qtremors.acqua.ui.theme.bounceClickable
import dev.qtremors.acqua.ui.updater.AppUpdateDialog
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    backupManager: PreferencesBackupManager? = null,
    themeState: ThemeState = ThemeState(),
    onThemeChange: (ThemeState) -> Unit = {},
    appUpdater: AppUpdater? = null,
    onOpenNotices: () -> Unit = {},
    onOpenLicense: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val uriHandler = LocalUriHandler.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val webViewProvider = remember { WebViewUpdateManager.currentProvider(context) }
    val settingsState by viewModel.state.collectAsState()

    val buildInfo = remember {
        AboutBuildInfo(
            versionName = BuildConfig.VERSION_NAME,
            applicationId = BuildConfig.APPLICATION_ID,
            buildType = BuildConfig.BUILD_TYPE
        )
    }
    val device = remember { deviceDescription(Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE) }
    val clipboardLabel = stringResource(R.string.app_name)
    val copyText: (String) -> Unit = { text ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(clipboardLabel, text))
        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var downloadState by remember { mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle) }
    val currentDownloadState by rememberUpdatedState(downloadState)

    val launchInstaller: (File) -> Unit = { apkFile ->
        appUpdater?.installApk(apkFile)?.fold(
            onSuccess = {
                updateInfo = null
                downloadState = UpdateDownloadState.Idle
            },
            onFailure = { error ->
                downloadState = UpdateDownloadState.Error(
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
                state is UpdateDownloadState.Downloaded &&
                appUpdater?.canRequestPackageInstalls() == true
            ) {
                launchInstaller(state.apkFile)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                            downloadState = UpdateDownloadState.Idle
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

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0 && overscrollOffset.value > 0) {
                    val toConsume = if (overscrollOffset.value + available.y >= 0) available.y else -overscrollOffset.value
                    coroutineScope.launch { overscrollOffset.snapTo(overscrollOffset.value + toConsume) }
                    return Offset(0f, toConsume)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && scrollState.value == 0) {
                    val newOffset = (overscrollOffset.value + available.y * 0.5f).coerceAtMost(350f)
                    coroutineScope.launch { overscrollOffset.snapTo(newOffset) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
        }
    }

    var isAnimatingIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isAnimatingIn = true }

    val contentAlphaState = animateFloatAsState(
        targetValue = if (isAnimatingIn) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = EaseOut),
        label = "content_alpha"
    )
    val contentOffsetState = animateDpAsState(
        targetValue = if (isAnimatingIn) 0.dp else 24.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "content_offset"
    )

    val layoutDirection = LocalLayoutDirection.current
    val effectivePadding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
        top = contentPadding.calculateTopPadding() + 8.dp,
        end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp,
        bottom = contentPadding.calculateBottomPadding() + 80.dp
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Release && overscrollOffset.value > 0) {
                            coroutineScope.launch {
                                overscrollOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        }
                    }
                }
            }
            .verticalScroll(scrollState)
            .padding(effectivePadding),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Settings Hero Header with App Icon and Quick Action Buttons
        SettingsHero(
            buildInfo = buildInfo,
            device = device,
            onCopyText = copyText,
            overscrollOffset = overscrollOffset.value,
            contentAlpha = { contentAlphaState.value },
            contentOffset = { contentOffsetState.value },
            onOpenGitHub = { openLink(AboutExternalLink.REPOSITORY) },
            onOpenReleases = { openLink(AboutExternalLink.RELEASES) },
            onOpenIssues = { openLink(AboutExternalLink.REPORT_ISSUE) },
            onCheckUpdates = handleCheckUpdates,
            onOpenPrivacy = { openLink(AboutExternalLink.PRIVACY) },
            onOpenNotices = onOpenNotices,
            onOpenLicense = onOpenLicense,
            isCheckingUpdates = isCheckingUpdates
        )

        // 2. Engine & Tools Section (Elevated at the top with modern UI)
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = contentAlphaState.value
                translationY = contentOffsetState.value.toPx()
            }
        ) {
            SettingsSection(title = stringResource(R.string.engine_settings_title)) {
                // Media Engine: yt-dlp
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = colors.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Header row with Icon, Title, Subtitle, and Version Chip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(colors.primaryContainer, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Bolt,
                                    contentDescription = null,
                                    tint = colors.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.ytdlp_engine_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    stringResource(R.string.ytdlp_engine_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colors.secondaryContainer
                            ) {
                                Text(
                                    text = settingsState.ytDlpVersion ?: stringResource(R.string.unknown),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        // Auto-update Switch Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Sync,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.padding(end = 8.dp)) {
                                    Text(
                                        stringResource(R.string.auto_update_ytdlp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        stringResource(R.string.auto_update_ytdlp_explanation),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = settingsState.autoUpdateYtDlp,
                                onCheckedChange = viewModel::setAutoUpdateYtDlp
                            )
                        }

                        // Last Checked & Feedback status
                        if (settingsState.lastYtDlpUpdate > 0L) {
                            val checkedAt = remember(settingsState.lastYtDlpUpdate) {
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                    .format(Date(settingsState.lastYtDlpUpdate))
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    stringResource(R.string.ytdlp_last_checked, checkedAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }

                        // Error or Update Success Alerts
                        settingsState.ytDlpUpdateError?.let { failure ->
                            val message = when (failure) {
                                YtDlpFailure.RUNTIME -> R.string.ytdlp_error_runtime
                                YtDlpFailure.NETWORK -> R.string.ytdlp_error_network
                                YtDlpFailure.STORAGE -> R.string.ytdlp_error_storage
                                YtDlpFailure.UPDATE_SERVICE -> R.string.ytdlp_error_update_service
                                YtDlpFailure.UNKNOWN -> R.string.ytdlp_error_unknown
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colors.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.ErrorOutline,
                                        contentDescription = null,
                                        tint = colors.onErrorContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        stringResource(message),
                                        color = colors.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        settingsState.ytDlpUpdateStatus?.let { status ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colors.primaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = colors.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        stringResource(
                                            when (status) {
                                                YtDlpUpdateStatus.UPDATED -> R.string.ytdlp_updated
                                                YtDlpUpdateStatus.ALREADY_CURRENT -> R.string.ytdlp_already_current
                                            }
                                        ),
                                        color = colors.onPrimaryContainer,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        // Update Engine Action Button
                        FilledTonalButton(
                            onClick = {
                                context.performHaptic(HapticSignal.CLICK)
                                viewModel.updateYtDlp()
                            },
                            enabled = !settingsState.isUpdatingYtDlp,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            if (settingsState.isUpdatingYtDlp) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(stringResource(if (settingsState.isUpdatingYtDlp) R.string.updating_ytdlp else R.string.update_ytdlp))
                        }
                    }
                }

                // Browser Engine: System WebView
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = colors.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(colors.secondaryContainer, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Language,
                                    contentDescription = null,
                                    tint = colors.onSecondaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    webViewProvider?.label ?: stringResource(R.string.webview_provider_unknown),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    stringResource(R.string.webview_engine_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colors.surfaceVariant
                            ) {
                                Text(
                                    stringResource(
                                        R.string.webview_version,
                                        webViewProvider?.versionName ?: stringResource(R.string.unknown)
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Text(
                            stringResource(R.string.webview_update_explanation),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )

                        OutlinedButton(
                            onClick = {
                                if (!WebViewUpdateManager.openUpdatePage(context, webViewProvider?.packageName)) {
                                    Toast.makeText(context, R.string.webview_update_unavailable, Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.check_webview_updates))
                        }
                    }
                }
            }
        }

        // 3. Section: Appearance
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = contentAlphaState.value
                translationY = contentOffsetState.value.toPx()
            }
        ) {
            SettingsSection(title = stringResource(R.string.appearance)) {
                SettingsCardContainer(index = 0, count = 3) {
                    ThemeModeSelector(
                        currentMode = themeState.themeMode,
                        onModeSelected = { newMode ->
                            onThemeChange(themeState.copy(themeMode = newMode))
                        }
                    )
                    AccentColorSelector(
                        currentAccent = themeState.accentColor,
                        onAccentSelected = { newAccent ->
                            onThemeChange(themeState.copy(accentColor = newAccent, themePreset = ThemePreset.NONE))
                        }
                    )
                }
                SettingsSwitchRow(
                    index = 1,
                    count = 3,
                    title = stringResource(R.string.harmonize_colors),
                    description = stringResource(R.string.harmonize_colors_description),
                    checked = themeState.harmonizeColors,
                    leadingIcon = Icons.Filled.Palette,
                    onCheckedChange = { harmonize ->
                        onThemeChange(themeState.copy(harmonizeColors = harmonize))
                    }
                )
                SettingsSwitchRow(
                    index = 2,
                    count = 3,
                    title = stringResource(R.string.vibrations),
                    description = stringResource(R.string.vibrations_description),
                    checked = themeState.vibrationsEnabled,
                    leadingIcon = Icons.Filled.Vibration,
                    onCheckedChange = { vibrations ->
                        onThemeChange(themeState.copy(vibrationsEnabled = vibrations))
                    }
                )
            }
        }

        // 4. Section: Downloads and Storage
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = contentAlphaState.value
                translationY = contentOffsetState.value.toPx()
            }
        ) {
            SettingsSection(title = stringResource(R.string.downloads_and_filenames)) {
                SettingsCardContainer(index = 0, count = 4) {
                    OutlinedTextField(
                        value = settingsState.baseFolder,
                        onValueChange = viewModel::setBaseFolder,
                        label = { Text(stringResource(R.string.downloads_subfolder)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        leadingIcon = {
                            Icon(Icons.Filled.Folder, null, tint = colors.primary)
                        }
                    )
                }
                SettingsSwitchRow(
                    index = 1,
                    count = 4,
                    title = stringResource(R.string.categorize_media),
                    description = stringResource(R.string.category_explanation),
                    checked = settingsState.categorizeMedia,
                    leadingIcon = Icons.Filled.FolderSpecial,
                    onCheckedChange = viewModel::setCategorizeMedia
                )
                SettingsCardContainer(index = 2, count = 4) {
                    OutlinedTextField(
                        value = settingsState.filenamePattern,
                        onValueChange = viewModel::setFilenamePattern,
                        label = { Text(stringResource(R.string.filename_pattern)) },
                        placeholder = { Text(FilenameFormatter.DEFAULT_PATTERN) },
                        supportingText = { Text(stringResource(R.string.filename_variables_guidance)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Text(
                        stringResource(R.string.filename_preview, FilenameFormatter.preview(settingsState.filenamePattern)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilenameFormatter.variables.forEach { variable ->
                            val selected = settingsState.filenamePattern.contains(variable)
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.toggleFilenameVariable(variable) },
                                label = { Text(variable) },
                                leadingIcon = if (selected) {{
                                    Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                                }} else null
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = viewModel::resetFilenamePattern,
                        enabled = settingsState.filenamePattern != FilenameFormatter.DEFAULT_PATTERN,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.reset_filename_pattern))
                    }
                }
                SettingsCardContainer(index = 3, count = 4) {
                    OutlinedTextField(
                        value = settingsState.audioFilenamePattern,
                        onValueChange = viewModel::setAudioFilenamePattern,
                        label = { Text(stringResource(R.string.audio_filename_pattern)) },
                        placeholder = { Text(FilenameFormatter.DEFAULT_AUDIO_PATTERN) },
                        supportingText = { Text(stringResource(R.string.filename_variables_guidance)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Text(
                        stringResource(R.string.audio_filename_preview, FilenameFormatter.previewAudio(settingsState.audioFilenamePattern)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilenameFormatter.audioVariables.forEach { variable ->
                            val selected = settingsState.audioFilenamePattern.contains(variable)
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.toggleAudioFilenameVariable(variable) },
                                label = { Text(variable) },
                                leadingIcon = if (selected) {{
                                    Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                                }} else null
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = viewModel::resetAudioFilenamePattern,
                        enabled = settingsState.audioFilenamePattern != FilenameFormatter.DEFAULT_AUDIO_PATTERN,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.reset_audio_filename_pattern))
                    }
                }
            }
        }

        // 5. Section: Media Processing
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = contentAlphaState.value
                translationY = contentOffsetState.value.toPx()
            }
        ) {
            SettingsSection(title = stringResource(R.string.media_processing)) {
                SettingsCardContainer(index = 0, count = 4) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.HighQuality, null, tint = colors.primary)
                        Text(stringResource(R.string.default_video_quality), fontWeight = FontWeight.Bold)
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0, 2160, 1440, 1080, 720, 480).forEach { height ->
                            FilterChip(
                                selected = settingsState.maximumVideoHeight == height,
                                onClick = { viewModel.setMaximumVideoHeight(height) },
                                label = { Text(if (height == 0) stringResource(R.string.best) else "${height}p") }
                            )
                        }
                    }
                }
                SettingsCardContainer(index = 1, count = 4) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Audiotrack, null, tint = colors.primary)
                        Text(stringResource(R.string.default_audio_format), fontWeight = FontWeight.Bold)
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AudioOutputFormat.entries.forEach { format ->
                            FilterChip(
                                selected = settingsState.audioFormat == format,
                                onClick = { viewModel.setAudioFormat(format) },
                                label = {
                                    Text(
                                        when (format) {
                                            AudioOutputFormat.ORIGINAL -> stringResource(R.string.original_audio)
                                            AudioOutputFormat.M4A -> "M4A"
                                            AudioOutputFormat.MP3 -> "MP3"
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
                SettingsSwitchRow(
                    index = 2,
                    count = 4,
                    title = stringResource(R.string.embed_metadata),
                    description = stringResource(R.string.embed_metadata_explanation),
                    checked = settingsState.embedMetadata,
                    leadingIcon = Icons.Filled.Movie,
                    onCheckedChange = viewModel::setEmbedMetadata
                )
                SettingsSwitchRow(
                    index = 3,
                    count = 4,
                    title = stringResource(R.string.embed_thumbnail),
                    description = stringResource(R.string.embed_thumbnail_explanation),
                    checked = settingsState.embedThumbnail,
                    leadingIcon = Icons.Filled.Image,
                    onCheckedChange = viewModel::setEmbedThumbnail
                )
            }
        }

        // 6. Section: Privacy and Security
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = contentAlphaState.value
                translationY = contentOffsetState.value.toPx()
            }
        ) {
            SettingsSection(title = stringResource(R.string.privacy_and_security)) {
                SettingsSwitchRow(
                    index = 0,
                    count = 1,
                    title = stringResource(R.string.screen_protection),
                    description = stringResource(R.string.screen_protection_description),
                    checked = settingsState.screenProtectionEnabled,
                    leadingIcon = Icons.Filled.Security,
                    onCheckedChange = viewModel::setScreenProtectionEnabled
                )
            }
        }

        // 7. Section: Backup and Restore
        if (backupManager != null) {
            Box(
                modifier = Modifier.graphicsLayer {
                    alpha = contentAlphaState.value
                    translationY = contentOffsetState.value.toPx()
                }
            ) {
                BackupRestoreSection(
                    backupManager = backupManager,
                    onRestoreCompleted = { viewModel.reload() }
                )
            }
        }


        Spacer(Modifier.height(12.dp))
    }

    updateInfo?.let { info ->
        val downloadUrl = info.apkDownloadUrl
        val fileName = info.apkName ?: "acqua-v${info.latestVersionName}.apk"
        AppUpdateDialog(
            updateInfo = info,
            downloadState = downloadState,
            onDownloadAndInstall = {
                if (downloadUrl != null && appUpdater != null) {
                    downloadState = UpdateDownloadState.Downloading(0, info.apkSizeBytes, 0)
                    coroutineScope.launch {
                        appUpdater.downloadUpdate(
                            downloadUrl = downloadUrl,
                            fileName = fileName,
                            expectedSizeBytes = info.apkSizeBytes,
                            expectedVersionCode = info.latestVersionCode,
                            onProgress = { downloaded, total, percent ->
                                downloadState = UpdateDownloadState.Downloading(downloaded, total, percent)
                            }
                        ).fold(
                            onSuccess = { apkFile ->
                                downloadState = UpdateDownloadState.Downloaded(apkFile)
                                if (appUpdater.canRequestPackageInstalls()) {
                                    launchInstaller(apkFile)
                                } else {
                                    runCatching { appUpdater.openInstallPermissionSettings() }.onFailure { error ->
                                        downloadState = UpdateDownloadState.Error(
                                            error.message ?: "Could not open install permission settings"
                                        )
                                    }
                                }
                            },
                            onFailure = { error ->
                                downloadState = UpdateDownloadState.Error(error.message ?: "Download failed")
                            }
                        )
                    }
                }
            },
            onInstallDownloadedApk = {
                val state = downloadState
                if (state is UpdateDownloadState.Downloaded && appUpdater != null) {
                    if (appUpdater.canRequestPackageInstalls()) {
                        launchInstaller(state.apkFile)
                    } else {
                        runCatching { appUpdater.openInstallPermissionSettings() }.onFailure { error ->
                            downloadState = UpdateDownloadState.Error(
                                error.message ?: "Could not open install permission settings"
                            )
                        }
                    }
                }
            },
            onDismiss = {
                updateInfo = null
                downloadState = UpdateDownloadState.Idle
            }
        )
    }
}

@Composable
private fun SettingsHero(
    buildInfo: AboutBuildInfo,
    device: String = "",
    onCopyText: (String) -> Unit = {},
    overscrollOffset: Float = 0f,
    contentAlpha: () -> Float = { 1f },
    contentOffset: () -> Dp = { 0.dp },
    onOpenGitHub: () -> Unit = {},
    onOpenReleases: () -> Unit = {},
    onOpenIssues: () -> Unit = {},
    onCheckUpdates: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onOpenNotices: () -> Unit = {},
    onOpenLicense: () -> Unit = {},
    isCheckingUpdates: Boolean = false
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val extraSize = with(density) { overscrollOffset.toDp() }
    val extraBottomPadding = with(density) { (overscrollOffset * 0.35f).toDp() }
    val logoDownwardTranslation = overscrollOffset * 0.2f

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Invisible container with top and dynamic bottom padding to prevent notification shade intrusion and title overlap
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = 28.dp,
                    bottom = 20.dp + extraBottomPadding
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.acqua),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier
                    .graphicsLayer {
                        alpha = contentAlpha()
                        translationY = contentOffset().toPx() + logoDownwardTranslation
                        scaleX = 1f + (overscrollOffset / 1200f)
                        scaleY = 1f + (overscrollOffset / 1200f)
                    }
                    .size(180.dp + extraSize)
            )
        }
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier
                .graphicsLayer {
                    alpha = contentAlpha()
                    translationY = contentOffset().toPx()
                }
                .padding(top = 2.dp)
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

        // Application ID & Version single horizontal row wrapped in grouped containers (tap to copy)
        Row(
            modifier = Modifier
                .graphicsLayer {
                    alpha = contentAlpha()
                    translationY = contentOffset().toPx()
                }
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val startShape = splitButtonShape(0, 2)
            Surface(
                shape = startShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .clip(startShape)
                    .bounceClickable {
                        context.performHaptic(HapticSignal.CLICK)
                        onCopyText(buildInfo.displayPackage)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.Android,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        buildInfo.displayPackage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val endShape = splitButtonShape(1, 2)
            Surface(
                shape = endShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .clip(endShape)
                    .bounceClickable {
                        context.performHaptic(HapticSignal.CLICK)
                        onCopyText(buildInfo.displayVersion)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.LocalOffer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        buildInfo.displayVersion,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (device.isNotBlank()) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = contentAlpha()
                        translationY = contentOffset().toPx()
                    }
                    .padding(top = 6.dp)
                    .clip(CircleShape)
                    .bounceClickable {
                        context.performHaptic(HapticSignal.CLICK)
                        onCopyText(device)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        device,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Action buttons grouped in arcile SplitButtonGroup style
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = contentAlpha()
                    translationY = contentOffset().toPx()
                }
                .padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Group 1: Issues, GitHub, Releases
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GroupedActionButton(
                    icon = { Icon(Icons.Filled.BugReport, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface) },
                    label = stringResource(R.string.about_action_issues),
                    onClick = {
                        context.performHaptic(HapticSignal.CLICK)
                        onOpenIssues()
                    },
                    shape = splitButtonShape(0, 3),
                    modifier = Modifier.weight(1f)
                )
                GroupedActionButton(
                    icon = { Icon(painterResource(R.drawable.ic_github), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface) },
                    label = stringResource(R.string.about_action_github),
                    onClick = {
                        context.performHaptic(HapticSignal.CLICK)
                        onOpenGitHub()
                    },
                    shape = splitButtonShape(1, 3),
                    modifier = Modifier.weight(1f)
                )
                GroupedActionButton(
                    icon = { Icon(Icons.Filled.NewReleases, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface) },
                    label = stringResource(R.string.about_action_releases),
                    onClick = {
                        context.performHaptic(HapticSignal.CLICK)
                        onOpenReleases()
                    },
                    shape = splitButtonShape(2, 3),
                    modifier = Modifier.weight(1f)
                )
            }

            // Group 2: Notices, Privacy, License
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GroupedActionButton(
                    icon = { Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface) },
                    label = stringResource(R.string.about_action_notices),
                    onClick = {
                        context.performHaptic(HapticSignal.CLICK)
                        onOpenNotices()
                    },
                    shape = splitButtonShape(0, 3),
                    modifier = Modifier.weight(1f)
                )
                GroupedActionButton(
                    icon = { Icon(Icons.Filled.PrivacyTip, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface) },
                    label = stringResource(R.string.about_action_privacy),
                    onClick = {
                        context.performHaptic(HapticSignal.CLICK)
                        onOpenPrivacy()
                    },
                    shape = splitButtonShape(1, 3),
                    modifier = Modifier.weight(1f)
                )
                GroupedActionButton(
                    icon = { Icon(Icons.Filled.Balance, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface) },
                    label = stringResource(R.string.about_action_license),
                    onClick = {
                        context.performHaptic(HapticSignal.CLICK)
                        onOpenLicense()
                    },
                    shape = splitButtonShape(2, 3),
                    modifier = Modifier.weight(1f)
                )
            }

            // Group 3: Updates
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(CircleShape)
                    .bounceClickable(enabled = !isCheckingUpdates) {
                        context.performHaptic(HapticSignal.CLICK)
                        onCheckUpdates()
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isCheckingUpdates) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = stringResource(if (isCheckingUpdates) R.string.checking_for_updates else R.string.check_for_updates),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun splitButtonShape(index: Int, count: Int): Shape {
    return when {
        count <= 1 -> CircleShape
        index == 0 -> RoundedCornerShape(50, 15, 15, 50)
        index == count - 1 -> RoundedCornerShape(15, 50, 50, 15)
        else -> RoundedCornerShape(15)
    }
}

@Composable
private fun GroupedActionButton(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .height(50.dp)
            .clip(shape)
            .bounceClickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            icon()
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
