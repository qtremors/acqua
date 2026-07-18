package dev.qtremors.acqua

import android.content.Context
import dev.qtremors.acqua.data.history.HistoryRepository
import dev.qtremors.acqua.data.network.MediaDownloader
import dev.qtremors.acqua.data.session.BrowserDataManager
import dev.qtremors.acqua.data.session.InstagramSessionStore
import dev.qtremors.acqua.data.session.SavedWebsiteRepository
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.domain.MediaResolutionService
import dev.qtremors.acqua.platform.FileActions
import dev.qtremors.acqua.platform.storage.MediaStorage
import dev.qtremors.acqua.resolver.instagram.InstagramResolver

class MainDependencies(context: Context) {
    val history = HistoryRepository(context)
    val settings = AppSettingsRepository(context)
    val mediaDownloader = MediaDownloader()
    val instagramSessions = InstagramSessionStore(context)
    val savedWebsites = SavedWebsiteRepository(context)
    val instagramResolver = InstagramResolver()
    val browserData = BrowserDataManager(context, savedWebsites, instagramSessions, settings)
    val mediaStorage = MediaStorage(context, history, settings, mediaDownloader)
    val resolution = MediaResolutionService(instagramResolver, mediaDownloader::validateAndResolveMetadata)
    val fileActions = FileActions(context)
}
