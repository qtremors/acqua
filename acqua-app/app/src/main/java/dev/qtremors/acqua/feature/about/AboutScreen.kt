package dev.qtremors.acqua.feature.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.BuildConfig
import dev.qtremors.acqua.R
import dev.qtremors.acqua.ui.components.SettingsActionRow
import dev.qtremors.acqua.ui.components.SettingsSection

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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            AboutHero(buildInfo)
        }

        item {
            SettingsSection(title = stringResource(R.string.about_app_info)) {
                SettingsActionRow(
                    index = 0,
                    count = 5,
                    title = stringResource(R.string.about_version),
                    description = buildInfo.displayVersion,
                    leadingIcon = Icons.Filled.Info,
                    onClick = { copyText("Acqua ${buildInfo.displayVersion}") }
                )
                SettingsActionRow(
                    index = 1,
                    count = 5,
                    title = stringResource(R.string.about_developer),
                    description = stringResource(R.string.about_developer_name),
                    leadingIcon = Icons.Filled.Code,
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = { openLink(AboutExternalLink.DEVELOPER) }
                )
                SettingsActionRow(
                    index = 2,
                    count = 5,
                    title = stringResource(R.string.about_repository),
                    description = stringResource(R.string.about_repository_address),
                    leadingIcon = Icons.Filled.Source,
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = { openLink(AboutExternalLink.REPOSITORY) }
                )
                SettingsActionRow(
                    index = 3,
                    count = 5,
                    title = stringResource(R.string.about_device),
                    description = device,
                    leadingIcon = Icons.Filled.PhoneAndroid,
                    onClick = { copyText(device) }
                )
                SettingsActionRow(
                    index = 4,
                    count = 5,
                    title = stringResource(R.string.about_package),
                    description = buildInfo.displayPackage,
                    leadingIcon = Icons.Filled.Info,
                    onClick = { copyText(buildInfo.displayPackage) }
                )
            }
        }

        item {
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

        item {
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
