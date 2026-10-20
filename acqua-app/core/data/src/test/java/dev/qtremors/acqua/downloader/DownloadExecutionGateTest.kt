package dev.qtremors.acqua.downloader

import android.app.job.JobParameters
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class DownloadExecutionGateTest {
    @Test fun `failure releases the execution slot for the next request`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async { runCatching { DownloadExecutionGate.run { entered.complete(Unit); release.await(); error("failed") } } }
        entered.await()
        var secondRan = false
        val second = async { DownloadExecutionGate.run { secondRan = true } }
        yield()
        assertFalse(secondRan)
        release.complete(Unit)
        assertTrue(first.await().isFailure)
        withTimeout(1000) { second.await() }
        assertTrue(secondRan)
    }
    @Test fun `canceling a waiting request leaves the active and following requests intact`() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val active = launch { DownloadExecutionGate.run { entered.complete(Unit); release.await() } }
        entered.await()
        val cancelled = launch { DownloadExecutionGate.run { fail("Cancelled request executed") } }
        yield()
        cancelled.cancelAndJoin()
        val following = async { DownloadExecutionGate.run { "complete" } }
        release.complete(Unit)
        active.join()
        assertEquals("complete", withTimeout(1000) { following.await() })
    }
    @Test fun `connectivity and platform limits retry while user stops remain final`() {
        assertTrue(DownloadStopPolicy.shouldRetry(JobParameters.STOP_REASON_CONSTRAINT_CONNECTIVITY))
        assertTrue(DownloadStopPolicy.shouldRetry(JobParameters.STOP_REASON_TIMEOUT))
        assertTrue(DownloadStopPolicy.shouldRetry(JobParameters.STOP_REASON_QUOTA))
        assertFalse(DownloadStopPolicy.shouldRetry(JobParameters.STOP_REASON_USER))
        assertFalse(DownloadStopPolicy.shouldRetry(JobParameters.STOP_REASON_CANCELLED_BY_APP))
    }
}
