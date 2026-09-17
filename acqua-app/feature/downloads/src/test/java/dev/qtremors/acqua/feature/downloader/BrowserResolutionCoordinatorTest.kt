package dev.qtremors.acqua.feature.downloader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserResolutionCoordinatorTest {
    @Test
    fun `new request cancels the previous request and stale clears are ignored`() = runBlocking {
        val coordinator = BrowserResolutionCoordinator()
        val first = coordinator.begin(explicitSessionAuthorization = false)
        val second = coordinator.begin(explicitSessionAuthorization = true)

        val cancellation = runCatching { first.deferred.await() }.exceptionOrNull()
        assertTrue(cancellation is CancellationException)
        assertTrue(second.explicitSessionAuthorization)
        assertSame(second, coordinator.pending)

        coordinator.clear(first)
        assertSame(second, coordinator.pending)
        coordinator.clear(second)
        assertNull(coordinator.pending)
    }

    @Test
    fun `browser return refreshes browser data without creating a download handoff`() {
        val coordinator = BrowserResolutionCoordinator()

        coordinator.recordBrowserReturn()

        assertEquals(1, coordinator.browserRevision)
        assertEquals(0, coordinator.downloadRequestRevision)
        assertNull(coordinator.urlHandoff)
    }

    @Test
    fun `external URLs create normal retryable handoffs`() {
        val coordinator = BrowserResolutionCoordinator()

        coordinator.acceptExternalUrl("https://example.com/shared")

        assertEquals(1, coordinator.downloadRequestRevision)
        assertEquals("https://example.com/shared", coordinator.urlHandoff)
    }
}
