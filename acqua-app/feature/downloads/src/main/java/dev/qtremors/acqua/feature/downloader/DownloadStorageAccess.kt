package dev.qtremors.acqua.feature.downloader

import dev.qtremors.acqua.data.settings.DownloadDestinations
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.downloader.DownloadContentType

fun DownloaderUiState.requiresLegacyStorage(destinations: DownloadDestinations): Boolean {
    val kinds = if (media.isNullOrEmpty()) MediaKind.entries else media.map {
        if (it.kind != MediaKind.IMAGE && downloadContentType == DownloadContentType.AUDIO) MediaKind.AUDIO else it.kind
    }
    return kinds.any { destinations[it].treeUri == null }
}
