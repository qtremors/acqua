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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.BuildConfig
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.data.backup.PreferencesBackupManager
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.FilenameFormatter
import dev.qtremors.acqua.downloader.YtDlpFailure
import dev.qtremors.acqua.downloader.YtDlpUpdateStatus
import dev.qtremors.acqua.appinfo.AboutBuildInfo
import dev.qtremors.acqua.appinfo.AboutExternalLink
import dev.qtremors.acqua.appinfo.deviceDescription
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
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    backupManager: PreferencesBackupManager? = null,
    themeState: ThemeState = ThemeState(),
    onThemeChange: (ThemeState) -> Unit = {},
    onOpenUpdates: () -> Unit = {},
    onOpenNotices: () -> Unit = {},
    onOpenLicense: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val webViewProvider = remember { WebViewUpdateManager.currentProvider(context) }
    val settingsState by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) { viewModel.loadYtDlpStatus() }

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

    val openLink: (AboutExternalLink) -> Unit = { link -> uriHandler.openUri(link.url) }

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

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(max = 960.dp)
            .fillMaxWidth()
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
            onCheckUpdates = onOpenUpdates,
            onOpenPrivacy = { openLink(AboutExternalLink.PRIVACY) },
            onOpenNotices = onOpenNotices,
            onOpenLicense = onOpenLicense,
            isCheckingUpdates = false
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = settingsState.autoUpdateYtDlp,
                                    role = Role.Switch,
                                    onValueChange = viewModel::setAutoUpdateYtDlp
                                )
                                .semantics(mergeDescendants = true) { },
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
                                onCheckedChange = null,
                                modifier = Modifier.clearAndSetSemantics { }
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

        SettingsPreferenceSections(
            settingsState = settingsState,
            viewModel = viewModel,
            themeState = themeState,
            onThemeChange = onThemeChange,
            contentAlpha = contentAlphaState.value,
            contentOffset = contentOffsetState.value
        )

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
    }

}
