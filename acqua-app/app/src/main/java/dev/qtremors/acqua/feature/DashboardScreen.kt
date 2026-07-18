package dev.qtremors.acqua.feature

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
    browserDownloadUrl: String?,
    browserRevision: Int,
    onBrowserDownloadConsumed: () -> Unit,
    resolveInBrowser: suspend (String) -> List<ResolvedMedia>,
    requestStorageAccess: (() -> Unit) -> Unit,
    openBrowser: (String?, Boolean, String?) -> Unit,
    onSessionStateChanged: (Boolean) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val browserState by browserViewModel.state.collectAsState()
    LaunchedEffect(initialUrl) { downloaderViewModel.setInitialUrl(initialUrl) }
    LaunchedEffect(browserRevision) { browserViewModel.refresh() }
    LaunchedEffect(browserState.useSessions) { onSessionStateChanged(browserState.useSessions) }
    LaunchedEffect(browserDownloadUrl) {
        browserDownloadUrl?.let {
            downloaderViewModel.updateUrl(it)
            tab = 0
            onBrowserDownloadConsumed()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            )
        },
        bottomBar = {
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
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            when (tab) {
                0 -> DownloaderScreen(
                    downloaderViewModel,
                    mediaDownloader,
                    browserState.useSessions,
                    browserState.initialized,
                    browserRevision,
                    resolveInBrowser,
                    requestStorageAccess,
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
                else -> SettingsScreen(settingsViewModel, Modifier.fillMaxSize().padding(padding))
            }
        }
    }
}
