package dev.qtremors.acqua

import android.content.Context
import dev.qtremors.acqua.data.history.HistoryRepository
import dev.qtremors.acqua.data.network.HttpMediaClient
import dev.qtremors.acqua.data.session.BrowserDataManager
import dev.qtremors.acqua.data.session.InstagramSessionStore
import dev.qtremors.acqua.data.session.SavedWebsiteRepository
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.data.updater.AppUpdater
import dev.qtremors.acqua.data.updater.GitHubApiClient
import dev.qtremors.acqua.data.updater.GitHubAuthRepository
import dev.qtremors.acqua.data.updater.InstalledAppMatcher
import dev.qtremors.acqua.data.updater.TrackedRepoRepository
import dev.qtremors.acqua.domain.MediaResolutionService
import dev.qtremors.acqua.downloader.YtDlpEngine
import dev.qtremors.acqua.downloader.DownloadWorkCoordinator
import dev.qtremors.acqua.downloader.YtDlpMaintenance
import dev.qtremors.acqua.downloader.DownloadExecutor
import dev.qtremors.acqua.downloader.AcquaWorkerFactory
import dev.qtremors.acqua.platform.FileActions
import dev.qtremors.acqua.platform.storage.MediaStorage
import dev.qtremors.acqua.resolver.instagram.InstagramResolver
import dev.qtremors.acqua.resolver.ytdlp.YtDlpResolver

class MainDependencies(context: Context) {
    val history = HistoryRepository(context)
    val settings = AppSettingsRepository(context)
    val mediaDownloader = HttpMediaClient()
    val instagramSessions = InstagramSessionStore(context)
    val savedWebsites = SavedWebsiteRepository(context)
    val instagramResolver = InstagramResolver()
    val ytDlpEngine = YtDlpEngine(context)
    val ytDlpDownloads by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DownloadWorkCoordinator(context)
    }
    val ytDlpResolver = YtDlpResolver(ytDlpEngine)
    val ytDlpMaintenance = YtDlpMaintenance(context, settings)
    val browserData = BrowserDataManager(context, savedWebsites, instagramSessions, settings)
    val mediaStorage = MediaStorage(context, history, settings, mediaDownloader)
    val downloadExecutor = DownloadExecutor(context, settings, history, mediaDownloader)
    internal val workerFactory = AcquaWorkerFactory(downloadExecutor)
    val resolution = MediaResolutionService(
        sourceResolver = instagramResolver,
        ytDlpResolver = ytDlpResolver,
        inspectMedia = mediaDownloader::validateAndResolveMetadata
    )
    val fileActions = FileActions(context)
    val themePreferences = dev.qtremors.acqua.settings.ThemePreferences(context)
    val onboardingPreferences = dev.qtremors.acqua.data.onboarding.OnboardingPreferences(context)
    val backupManager = dev.qtremors.acqua.data.backup.PreferencesBackupManager(context)
    val appUpdater = AppUpdater(context)
    val trackedRepos = TrackedRepoRepository(context)
    val githubAuth = GitHubAuthRepository(context)
    val githubApi = GitHubApiClient(tokenProvider = { githubAuth.token })
    val installedAppMatcher = InstalledAppMatcher(context)
}
