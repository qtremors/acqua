package dev.qtremors.acqua.downloader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadDuplicateGateTest {
    @Test fun `matching recipes serialize while an unrelated recipe can run`() = runTest {
        val release = CompletableDeferred<Unit>()
        val entered = mutableListOf<String>()
        val first = launch { DownloadDuplicateGate.run("recipe") { entered += "first"; release.await() } }
        runCurrent()
        val second = launch { DownloadDuplicateGate.run("recipe") { entered += "second" } }
        val other = launch { DownloadDuplicateGate.run("other") { entered += "other" } }
        runCurrent()
        assertEquals(listOf("first", "other"), entered)
        release.complete(Unit)
        first.join(); second.join(); other.join()
        assertEquals(listOf("first", "other", "second"), entered)
    }

    @Test fun `cancelled matching waiters do not discard the active recipe lock`() = runTest {
        val release = CompletableDeferred<Unit>()
        val first = launch { DownloadDuplicateGate.run("cancel") { release.await() } }
        runCurrent()
        var cancelledRan = false
        val cancelled = launch { DownloadDuplicateGate.run("cancel") { cancelledRan = true } }
        runCurrent(); cancelled.cancelAndJoin()
        var nextRan = false
        val next = launch { DownloadDuplicateGate.run("cancel") { nextRan = true } }
        runCurrent(); assertFalse(nextRan)
        release.complete(Unit); first.join(); next.join()
        assertFalse(cancelledRan)
        assertTrue(nextRan)
    }

    @Test fun `failed matching requests release the recipe for a later attempt`() = runTest {
        val first = async { runCatching { DownloadDuplicateGate.run("failure") { error("failure") } } }
        assertTrue(first.await().isFailure)
        assertEquals("ready", DownloadDuplicateGate.run("failure") { "ready" })
    }
}
