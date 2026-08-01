package dev.qtremors.acqua.feature

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.R
import dev.qtremors.acqua.data.network.MediaDownloader
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.feature.about.AboutDestination
import dev.qtremors.acqua.feature.about.AboutScreen
import dev.qtremors.acqua.feature.about.LegalDocumentScreen
import dev.qtremors.acqua.feature.about.OpenSourceNoticesScreen
import dev.qtremors.acqua.feature.browser.BrowserScreen
import dev.qtremors.acqua.feature.browser.BrowserViewModel
import dev.qtremors.acqua.feature.downloader.DownloaderScreen
import dev.qtremors.acqua.feature.downloader.DownloaderViewModel
import dev.qtremors.acqua.feature.history.HistoryScreen
import dev.qtremors.acqua.feature.history.HistoryViewModel
import dev.qtremors.acqua.feature.settings.SettingsScreen
import dev.qtremors.acqua.feature.settings.SettingsViewModel
import dev.qtremors.acqua.platform.FileActions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    downloaderViewModel: DownloaderViewModel,
    browserViewModel: BrowserViewModel,
    historyViewModel: HistoryViewModel,
    settingsViewModel: SettingsViewModel,
    mediaDownloader: MediaDownloader,
    fileActions: FileActions,
    initialUrl: String,
    urlHandoff: String?,
    browserRevision: Int,
    downloadRequestRevision: Int,
    onUrlHandoffConsumed: () -> Unit,
    resolveInBrowser: suspend (String, Boolean) -> List<ResolvedMedia>,
    requestDownloadAccess: (needsNotification: Boolean, action: () -> Unit) -> Unit,
    openBrowser: (String?, Boolean, String?) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var overlay by remember { androidx.compose.runtime.mutableStateOf<AboutDestination?>(null) }
    val browserState by browserViewModel.state.collectAsState()
    BackHandler(enabled = overlay != null) {
        overlay = if (overlay == AboutDestination.NOTICES || overlay == AboutDestination.LICENSE) {
            AboutDestination.ABOUT
        } else {
            null
        }
    }
    LaunchedEffect(initialUrl) { downloaderViewModel.setInitialUrl(initialUrl) }
    LaunchedEffect(browserRevision) {
        if (browserRevision > 0) browserViewModel.refresh()
    }
    LaunchedEffect(urlHandoff) {
        urlHandoff?.let { url ->
            downloaderViewModel.updateUrl(url)
            if (browserState.initialized) {
                downloaderViewModel.resolve(
                    browserState.useSessions,
                    downloadRequestRevision,
                    resolveInBrowser
                )
            }
            tab = 0
            onUrlHandoffConsumed()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(
                            when (overlay) {
                                AboutDestination.ABOUT -> R.string.about_title
                                AboutDestination.NOTICES -> R.string.about_open_source_notices
                                AboutDestination.LICENSE -> R.string.about_license
                                null -> R.string.app_name
                            }
                        ),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (overlay != null) {
                        IconButton(onClick = {
                            overlay = if (
                                overlay == AboutDestination.NOTICES ||
                                overlay == AboutDestination.LICENSE
                            ) {
                                AboutDestination.ABOUT
                            } else {
                                null
                            }
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                stringResource(R.string.back)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            )
        },
        bottomBar = {
            if (overlay == null) {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            ) {
                val items = listOf(
                    Triple(R.string.downloader, Icons.Filled.Download, 0),
                    Triple(R.string.browser, Icons.Filled.Public, 1),
                    Triple(R.string.history, Icons.Filled.History, 2),
                    Triple(R.string.settings, Icons.Filled.Settings, 3)
                )
                items.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icon, stringResource(label)) },
                        label = { Text(stringResource(label)) }
                    )
                }
            }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            when (overlay) {
                AboutDestination.ABOUT -> AboutScreen(
                    onOpenNotices = { overlay = AboutDestination.NOTICES },
                    onOpenLicense = { overlay = AboutDestination.LICENSE },
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
                AboutDestination.NOTICES -> OpenSourceNoticesScreen(
                    Modifier.fillMaxSize().padding(padding)
                )
                AboutDestination.LICENSE -> LegalDocumentScreen(
                    title = stringResource(R.string.about_license),
                    assetName = "LICENSE.md",
                    introduction = stringResource(R.string.acqua_legal_notice),
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
                null -> when (tab) {
                0 -> DownloaderScreen(
                    downloaderViewModel,
                    mediaDownloader,
                    browserState.useSessions,
                    browserState.initialized,
                    downloadRequestRevision,
                    resolveInBrowser,
                    requestDownloadAccess,
                    onOpenBrowser = { openBrowser(it, false, null) },
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
                1 -> BrowserScreen(
                    browserViewModel,
                    onAddWebsite = { name, url -> openBrowser(url, true, name) },
                    onOpenWebsite = { openBrowser(it, false, null) },
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
                2 -> HistoryScreen(
                    historyViewModel,
                    mediaDownloader,
                    fileActions,
                    active = true,
                    onRefetch = { downloaderViewModel.updateUrl(it); tab = 0 },
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
                else -> SettingsScreen(
                    settingsViewModel,
                    onOpenAbout = { overlay = AboutDestination.ABOUT },
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
                }
            }
        }
    }
}
