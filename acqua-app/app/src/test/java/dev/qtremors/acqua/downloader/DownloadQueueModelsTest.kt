package dev.qtremors.acqua.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadQueueModelsTest {
    @Test
    fun `queued running and retrying items are active`() {
        val states = listOf(
            DownloadQueueState.QUEUED,
            DownloadQueueState.RUNNING,
            DownloadQueueState.RETRYING
        )

        states.forEach { state ->
            assertTrue(DownloadQueueItem(id = state.name, state = state).isActive)
        }
    }

    @Test
    fun `terminal items are not active`() {
        val states = listOf(
            DownloadQueueState.SUCCEEDED,
            DownloadQueueState.FAILED,
            DownloadQueueState.CANCELLED
        )

        states.forEach { state ->
            assertFalse(DownloadQueueItem(id = state.name, state = state).isActive)
        }
    }

    @Test
    fun `snapshot exposes only active items`() {
        val snapshot = DownloadQueueSnapshot(
            DownloadQueueState.entries.map { state ->
                DownloadQueueItem(id = state.name, state = state)
            }
        )

        assertEquals(3, snapshot.activeCount)
        assertEquals(
            listOf(
                DownloadQueueState.QUEUED,
                DownloadQueueState.RUNNING,
                DownloadQueueState.RETRYING
            ),
            snapshot.activeItems.map(DownloadQueueItem::state)
        )
    }

    @Test
    fun `aggregate progress averages active items only`() {
        val snapshot = DownloadQueueSnapshot(
            listOf(
                item("queued", DownloadQueueState.QUEUED, 0f),
                item("running", DownloadQueueState.RUNNING, 50f),
                item("retrying", DownloadQueueState.RETRYING, 100f),
                item("finished", DownloadQueueState.SUCCEEDED, 20f)
            )
        )

        assertEquals(50f, snapshot.aggregateProgress, 0.001f)
    }

    @Test
    fun `aggregate progress clamps malformed worker values`() {
        val snapshot = DownloadQueueSnapshot(
            listOf(
                item("negative", DownloadQueueState.RUNNING, -30f),
                item("oversized", DownloadQueueState.RUNNING, 180f)
            )
        )

        assertEquals(50f, snapshot.aggregateProgress, 0.001f)
    }

    @Test
    fun `aggregate progress is zero without active items`() {
        val snapshot = DownloadQueueSnapshot(
            listOf(item("finished", DownloadQueueState.SUCCEEDED, 100f))
        )

        assertEquals(0f, snapshot.aggregateProgress, 0.001f)
    }

    @Test
    fun `only three most recent failures are exposed`() {
        val failures = (1..5).map { index ->
            DownloadQueueItem(
                id = "failure-$index",
                state = DownloadQueueState.FAILED,
                error = "Error $index"
            )
        }
        val snapshot = DownloadQueueSnapshot(
            failures + item("success", DownloadQueueState.SUCCEEDED, 100f)
        )

        assertEquals(
            listOf("failure-3", "failure-4", "failure-5"),
            snapshot.mostRecentFailures.map(DownloadQueueItem::id)
        )
    }

    @Test
    fun `without removes only the selected item and leaves original unchanged`() {
        val first = item("first", DownloadQueueState.RUNNING, 20f)
        val second = item("second", DownloadQueueState.QUEUED, 0f)
        val original = DownloadQueueSnapshot(listOf(first, second))

        val updated = original.without(first.id)

        assertEquals(listOf(second), updated.items)
        assertEquals(listOf(first, second), original.items)
    }

    @Test
    fun `without unknown id preserves snapshot value`() {
        val original = DownloadQueueSnapshot(
            listOf(item("download", DownloadQueueState.RUNNING, 75f))
        )

        assertEquals(original, original.without("missing"))
    }

    private fun item(
        id: String,
        state: DownloadQueueState,
        progress: Float
    ) = DownloadQueueItem(id = id, state = state, progress = progress)
}
