package dev.qtremors.acqua.resolver.web

import dev.qtremors.acqua.data.session.InstagramSessionStore
import dev.qtremors.acqua.data.session.SavedWebsiteRepository
import dev.qtremors.acqua.data.settings.AppSettingsRepository

interface RenderedPageResolverDependencies {
    val resolverSettings: AppSettingsRepository
    val resolverSavedWebsites: SavedWebsiteRepository
    val resolverInstagramSessions: InstagramSessionStore
}
