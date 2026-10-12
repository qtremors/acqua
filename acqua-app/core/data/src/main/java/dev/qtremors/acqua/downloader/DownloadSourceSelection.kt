package dev.qtremors.acqua.downloader

import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.WebLink

internal data class DownloadSourceSelection(val url: String, val collectionIndex: Int? = null) {
    companion object {
        fun from(media: ResolvedMedia): DownloadSourceSelection {
            val entry = WebLink.normalize(media.url)
            val collection = media.collectionUrl
            return if (entry != null && entry != collection) DownloadSourceSelection(entry)
                else if (collection != null && media.collectionIndex > 0) {
                    DownloadSourceSelection(collection, media.collectionIndex)
                } else DownloadSourceSelection(media.url)
        }
    }
}
