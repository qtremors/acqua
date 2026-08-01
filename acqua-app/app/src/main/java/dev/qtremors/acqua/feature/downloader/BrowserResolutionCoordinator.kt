package dev.qtremors.acqua.feature.downloader

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.qtremors.acqua.domain.ResolvedMedia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

internal data class PendingBrowserResolution(
    val deferred: CompletableDeferred<List<ResolvedMedia>>,
    val explicitSessionAuthorization: Boolean
)

internal class BrowserResolutionCoordinator : ViewModel() {
    var pending: PendingBrowserResolution? = null
        private set
    var urlHandoff by mutableStateOf<String?>(null)
        private set
    var browserRevision by mutableIntStateOf(0)
        private set
    var downloadRequestRevision by mutableIntStateOf(0)
        private set

    fun recordBrowserReturn() {
        browserRevision++
    }

    fun acceptExternalUrl(url: String) {
        urlHandoff = url
        downloadRequestRevision++
    }

    fun consumeUrlHandoff() {
        urlHandoff = null
    }

    fun begin(explicitSessionAuthorization: Boolean): PendingBrowserResolution {
        pending?.deferred?.completeExceptionally(
            CancellationException("A newer request replaced this one.")
        )
        return PendingBrowserResolution(
            CompletableDeferred(),
            explicitSessionAuthorization
        ).also { pending = it }
    }

    fun clear(candidate: PendingBrowserResolution) {
        if (pending === candidate) pending = null
    }

    override fun onCleared() {
        pending?.deferred?.completeExceptionally(CancellationException("Activity destroyed"))
        pending = null
    }
}
