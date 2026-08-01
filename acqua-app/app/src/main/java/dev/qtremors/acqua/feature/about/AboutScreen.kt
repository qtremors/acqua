package dev.qtremors.acqua.feature.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.BuildConfig
import dev.qtremors.acqua.R

@Composable
fun AboutScreen(
    onOpenNotices: () -> Unit,
    onOpenLicense: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clipboardLabel = stringResource(R.string.app_name)
    val buildInfo = AboutBuildInfo(
        versionName = BuildConfig.VERSION_NAME,
        applicationId = BuildConfig.APPLICATION_ID,
        buildType = BuildConfig.BUILD_TYPE
    )
    val device = deviceDescription(Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE)
    val copyText: (String) -> Unit = { text ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(clipboardLabel, text))
        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }
    val openLink: (AboutExternalLink) -> Unit = { link -> uriHandler.openUri(link.url) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            AboutHero(buildInfo)
        }
        item {
            AboutSection(title = stringResource(R.string.about_app_info)) {
                AboutActionRow(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.about_version),
                    description = buildInfo.displayVersion,
                    onClick = { copyText("Acqua ${buildInfo.displayVersion}") }
                )
                AboutDivider()
                AboutActionRow(
                    icon = Icons.Filled.Code,
                    title = stringResource(R.string.about_developer),
                    description = stringResource(R.string.about_developer_name),
                    external = true,
                    onClick = { openLink(AboutExternalLink.DEVELOPER) }
                )
                AboutDivider()
                AboutActionRow(
                    icon = Icons.Filled.Source,
                    title = stringResource(R.string.about_repository),
                    description = stringResource(R.string.about_repository_address),
                    external = true,
                    onClick = { openLink(AboutExternalLink.REPOSITORY) }
                )
                AboutDivider()
                AboutActionRow(
                    icon = Icons.Filled.PhoneAndroid,
                    title = stringResource(R.string.about_device),
                    description = device,
                    onClick = { copyText(device) }
                )
                AboutDivider()
                AboutActionRow(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.about_package),
                    description = buildInfo.displayPackage,
                    onClick = { copyText(buildInfo.displayPackage) }
                )
            }
        }
        item {
            AboutSection(title = stringResource(R.string.about_privacy)) {
                AboutActionRow(
                    icon = Icons.Filled.Lock,
                    title = stringResource(R.string.about_privacy_policy),
                    description = stringResource(R.string.about_privacy_description),
                    external = true,
                    onClick = { openLink(AboutExternalLink.PRIVACY) }
                )
            }
        }
        item {
            AboutSection(title = stringResource(R.string.about_support)) {
                AboutActionRow(
                    icon = Icons.Filled.History,
                    title = stringResource(R.string.about_releases),
                    description = stringResource(R.string.about_releases_description),
                    external = true,
                    onClick = { openLink(AboutExternalLink.RELEASES) }
                )
                AboutDivider()
                AboutActionRow(
                    icon = Icons.Filled.BugReport,
                    title = stringResource(R.string.about_report_issue),
                    description = stringResource(R.string.about_report_issue_description),
                    external = true,
                    onClick = { openLink(AboutExternalLink.REPORT_ISSUE) }
                )
                AboutDivider()
                AboutActionRow(
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    title = stringResource(R.string.about_open_source_notices),
                    description = stringResource(R.string.about_open_source_notices_description),
                    onClick = onOpenNotices
                )
                AboutDivider()
                AboutActionRow(
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    title = stringResource(R.string.about_license),
                    description = stringResource(R.string.about_license_description),
                    onClick = onOpenLicense
                )
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun AboutHero(buildInfo: AboutBuildInfo) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.acqua),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.size(96.dp)
        )
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            stringResource(R.string.about_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            buildInfo.displayVersion,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun AboutSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(content = { content() })
        }
    }
}

@Composable
private fun AboutActionRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    external: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (external) {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = stringResource(R.string.open_in_browser),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AboutDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
