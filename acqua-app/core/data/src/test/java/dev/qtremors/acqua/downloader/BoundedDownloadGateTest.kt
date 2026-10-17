package dev.qtremors.acqua.downloader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BoundedDownloadGateTest {
    @Test fun `mixed backend requests remain FIFO with at most two active tasks and isolated failures`() = runTest {
        val gate = BoundedDownloadGate(2)
        val releases = List(6) { CompletableDeferred<Unit>() }
        val entered = mutableListOf<String>()
        var active = 0
        var peak = 0
        val tasks = (0..5).map { index -> async {
            runCatching { gate.run {
                active++; peak = maxOf(peak, active)
                entered += "${if (index % 2 == 0) "worker" else "job"}-$index"
                try { releases[index].await(); if (index == 2) error("isolated failure"); index } finally { active-- }
            } }
        } }
        runCurrent()
        assertEquals(listOf("worker-0", "job-1"), entered)
        tasks[3].cancel()
        releases[0].complete(Unit); runCurrent()
        assertEquals("worker-2", entered.last())
        releases[2].complete(Unit); runCurrent()
        assertEquals("worker-4", entered.last())
        releases[1].complete(Unit); runCurrent()
        assertEquals("job-5", entered.last())
        releases[4].complete(Unit); releases[5].complete(Unit)
        advanceUntilIdle()
        assertEquals(2, peak)
        assertEquals(0, active)
        assertTrue(tasks[2].await().isFailure)
        assertEquals(4, tasks[4].await().getOrThrow())
        assertFalse("job-3" in entered)
    }

    @Test fun `raising the limit admits a waiting task and lowering it lets active tasks finish`() = runTest {
        val gate = BoundedDownloadGate()
        val entered = mutableListOf<Int>()
        val releases = List(3) { CompletableDeferred<Unit>() }
        val jobs = (0..2).map { index -> launch { gate.run { entered += index; releases[index].await() } } }
        runCurrent(); assertEquals(listOf(0), entered)
        gate.configure(2); runCurrent(); assertEquals(listOf(0, 1), entered)
        gate.configure(1)
        releases[0].complete(Unit); runCurrent(); assertEquals(listOf(0, 1), entered)
        releases[1].complete(Unit); runCurrent(); assertEquals(listOf(0, 1, 2), entered)
        releases[2].complete(Unit); jobs.forEach { it.join() }
    }

    @Test fun `cancelling after a permit is granted returns it before a queued block can execute`() = runTest {
        val gate = BoundedDownloadGate()
        val release = CompletableDeferred<Unit>()
        val active = launch(UnconfinedTestDispatcher(testScheduler)) { gate.run { release.await() } }
        var cancelledRan = false
        val waiting = launch { gate.run { cancelledRan = true } }
        runCurrent()
        release.complete(Unit)
        waiting.cancel()
        val following = async { gate.run { "complete" } }
        advanceUntilIdle()
        active.join(); waiting.join()
        assertFalse(cancelledRan)
        assertEquals("complete", following.await())
    }

    @Test fun `invalid limits fall back to serial execution`() = runTest {
        for (invalid in listOf(0, 3, Int.MAX_VALUE)) {
            val gate = BoundedDownloadGate(invalid)
            val release = CompletableDeferred<Unit>()
            var entered = 0
            val first = launch { gate.run { entered++; release.await() } }
            val second = launch { gate.run { entered++ } }
            runCurrent(); assertEquals(1, entered)
            gate.configure(invalid); runCurrent(); assertEquals(1, entered)
            release.complete(Unit); first.join(); second.join()
            assertEquals(2, entered)
        }
    }
}
