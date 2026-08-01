package dev.qtremors.acqua.downloader

enum class DownloadQueueState {
    QUEUED,
    RUNNING,
    RETRYING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}

data class DownloadQueueItem(
    val id: String,
    val state: DownloadQueueState,
    val progress: Float = 0f,
    val etaSeconds: Long = 0L,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val error: String? = null
) {
    val isActive: Boolean
        get() = state == DownloadQueueState.QUEUED ||
            state == DownloadQueueState.RUNNING ||
            state == DownloadQueueState.RETRYING
}

data class DownloadQueueSnapshot(
    val items: List<DownloadQueueItem> = emptyList()
) {
    val activeItems: List<DownloadQueueItem>
        get() = items.filter(DownloadQueueItem::isActive)

    val activeCount: Int
        get() = activeItems.size

    val aggregateProgress: Float
        get() {
            val active = activeItems
            if (active.isEmpty()) return 0f
            return active.map { it.progress.coerceIn(0f, 100f) }.average().toFloat()
        }

    val aggregateTotalBytes: Long
        get() = activeItems.filter { it.totalBytes > 0L }.sumOf { it.totalBytes }

    val aggregateDownloadedBytes: Long
        get() {
            val knownTotals = activeItems.filter { it.totalBytes > 0L }
            return (knownTotals.ifEmpty { activeItems }).sumOf { it.downloadedBytes }
        }

    val mostRecentFailures: List<DownloadQueueItem>
        get() = items.filter { it.state == DownloadQueueState.FAILED }.takeLast(MAX_VISIBLE_FAILURES)

    fun without(id: String): DownloadQueueSnapshot = copy(items = items.filterNot { it.id == id })

    private companion object {
        const val MAX_VISIBLE_FAILURES = 3
    }
}
