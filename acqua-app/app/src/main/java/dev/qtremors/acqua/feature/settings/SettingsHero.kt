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
import dev.qtremors.acqua.BuildConfig
import dev.qtremors.acqua.R
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
internal fun SettingsHero(
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

internal fun splitButtonShape(index: Int, count: Int): Shape {
    return when {
        count <= 1 -> CircleShape
        index == 0 -> RoundedCornerShape(50, 15, 15, 50)
        index == count - 1 -> RoundedCornerShape(15, 50, 50, 15)
        else -> RoundedCornerShape(15)
    }
}

@Composable
internal fun GroupedActionButton(
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
