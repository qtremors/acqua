package dev.qtremors.acqua

import android.content.Context
import dev.qtremors.acqua.data.history.HistoryRepository
import dev.qtremors.acqua.data.network.MediaDownloader
import dev.qtremors.acqua.data.session.BrowserDataManager
import dev.qtremors.acqua.data.session.InstagramSessionStore
import dev.qtremors.acqua.data.session.SavedWebsiteRepository
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.domain.MediaResolutionService
import dev.qtremors.acqua.downloader.YtDlpEngine
import dev.qtremors.acqua.downloader.YtDlpDownloadCoordinator
import dev.qtremors.acqua.downloader.YtDlpMaintenance
import dev.qtremors.acqua.platform.FileActions
import dev.qtremors.acqua.platform.storage.MediaStorage
import dev.qtremors.acqua.resolver.instagram.InstagramResolver
import dev.qtremors.acqua.resolver.ytdlp.YtDlpResolver

class MainDependencies(context: Context) {
    val history = HistoryRepository(context)
    val settings = AppSettingsRepository(context)
    val mediaDownloader = MediaDownloader()
    val instagramSessions = InstagramSessionStore(context)
    val savedWebsites = SavedWebsiteRepository(context)
    val instagramResolver = InstagramResolver()
    val ytDlpEngine = YtDlpEngine(context)
    val ytDlpDownloads = YtDlpDownloadCoordinator(context)
    val ytDlpResolver = YtDlpResolver(ytDlpEngine)
    val ytDlpMaintenance = YtDlpMaintenance(context, settings)
    val browserData = BrowserDataManager(context, savedWebsites, instagramSessions, settings)
    val mediaStorage = MediaStorage(context, history, settings, mediaDownloader)
    val resolution = MediaResolutionService(
        sourceResolver = instagramResolver,
        ytDlpResolver = ytDlpResolver,
        inspectMedia = mediaDownloader::validateAndResolveMetadata
    )
    val fileActions = FileActions(context)
    val themePreferences = dev.qtremors.acqua.ui.theme.ThemePreferences(context)
    val onboardingPreferences = dev.qtremors.acqua.data.onboarding.OnboardingPreferences(context)
    val backupManager = dev.qtremors.acqua.data.backup.PreferencesBackupManager(context)
    val appUpdater = dev.qtremors.acqua.data.updater.AppUpdater(context)
}
