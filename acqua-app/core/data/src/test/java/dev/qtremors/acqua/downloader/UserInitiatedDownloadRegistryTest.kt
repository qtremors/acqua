package dev.qtremors.acqua.downloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UserInitiatedDownloadRegistryTest {
    @Test fun `concurrent writers removals and reloads preserve every unrelated task`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        UserInitiatedDownloadRegistry.reload(context)
        val ids = List(12) { UUID.randomUUID() }
        val removed = List(3) { UUID.randomUUID() }
        removed.forEach { UserInitiatedDownloadRegistry.update(context, DownloadQueueItem(it.toString(), DownloadQueueState.QUEUED)) }
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(4)
        try {
            val jobs = ids.chunked(4).map { group -> executor.submit {
                check(start.await(5, TimeUnit.SECONDS))
                group.forEach { id ->
                    repeat(5) { progress -> UserInitiatedDownloadRegistry.update(context,
                        DownloadQueueItem(id.toString(), DownloadQueueState.RUNNING, progress = progress * 20f)) }
                    UserInitiatedDownloadRegistry.update(context,
                        DownloadQueueItem(id.toString(), DownloadQueueState.SUCCEEDED, progress = 100f, phase = DownloadPhase.COMPLETE))
                }
            } } + executor.submit {
                check(start.await(5, TimeUnit.SECONDS))
                removed.forEach { UserInitiatedDownloadRegistry.remove(context, it); UserInitiatedDownloadRegistry.reload(context) }
            }
            start.countDown()
            jobs.forEach { it.get(10, TimeUnit.SECONDS) }
            val beforeReload = UserInitiatedDownloadRegistry.observe(context).value
            assertTrue(removed.none { it in beforeReload })
            ids.forEach { assertEquals(DownloadQueueState.SUCCEEDED, beforeReload[it]?.state) }
            UserInitiatedDownloadRegistry.reload(context)
            assertEquals(beforeReload, UserInitiatedDownloadRegistry.observe(context).value)
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun `late callbacks cannot reopen or replace any terminal outcome`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        UserInitiatedDownloadRegistry.reload(context)
        val executor = Executors.newSingleThreadExecutor()
        try {
            for (state in listOf(DownloadQueueState.SUCCEEDED, DownloadQueueState.SKIPPED, DownloadQueueState.FAILED, DownloadQueueState.CANCELLED)) {
                val id = UUID.randomUUID()
                val running = DownloadQueueItem(id.toString(), DownloadQueueState.RUNNING)
                UserInitiatedDownloadRegistry.update(context, running)
                val finished = CountDownLatch(1)
                val late = executor.submit {
                    check(finished.await(5, TimeUnit.SECONDS))
                    UserInitiatedDownloadRegistry.update(context, running.copy(progress = 75f))
                    UserInitiatedDownloadRegistry.update(context, running.copy(state = DownloadQueueState.RETRYING))
                    UserInitiatedDownloadRegistry.update(context, running, starting = true)
                }
                val terminal = running.copy(state = state, progress = 100f, phase = DownloadPhase.COMPLETE, artifactCount = 1)
                UserInitiatedDownloadRegistry.update(context, terminal)
                finished.countDown()
                late.get(5, TimeUnit.SECONDS)
                assertEquals(terminal, UserInitiatedDownloadRegistry.observe(context).value[id])
                UserInitiatedDownloadRegistry.reload(context)
                assertEquals(terminal, UserInitiatedDownloadRegistry.observe(context).value[id])
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun `terminal retention cleanup preserves active native records across reload`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val active = UUID.randomUUID()
        val finished = UUID.randomUUID()
        UserInitiatedDownloadRegistry.reload(context)
        UserInitiatedDownloadRegistry.update(context, DownloadQueueItem(active.toString(), DownloadQueueState.RUNNING))
        UserInitiatedDownloadRegistry.update(context, DownloadQueueItem(finished.toString(), DownloadQueueState.SUCCEEDED))
        UserInitiatedDownloadRegistry.removeTerminals(context, listOf(active.toString(), finished.toString()))
        UserInitiatedDownloadRegistry.reload(context)
        assertEquals(DownloadQueueState.RUNNING, UserInitiatedDownloadRegistry.observe(context).value[active]?.state)
        assertNull(UserInitiatedDownloadRegistry.observe(context).value[finished])
        UserInitiatedDownloadRegistry.remove(context, active)
    }
    @Test fun `late progress cannot overwrite retry or explicit cancellation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = UUID.randomUUID()
        UserInitiatedDownloadRegistry.reload(context)
        val running = DownloadQueueItem(id.toString(), DownloadQueueState.RUNNING, phase = DownloadPhase.TRANSFER)
        UserInitiatedDownloadRegistry.update(context, running)
        UserInitiatedDownloadRegistry.update(context, running.copy(state = DownloadQueueState.RETRYING, phase = DownloadPhase.QUEUED))
        UserInitiatedDownloadRegistry.update(context, running.copy(progress = 90f))
        assertEquals(DownloadQueueState.RETRYING, UserInitiatedDownloadRegistry.observe(context).value[id]?.state)
        UserInitiatedDownloadRegistry.update(context, running, starting = true)
        assertEquals(DownloadQueueState.RUNNING, UserInitiatedDownloadRegistry.observe(context).value[id]?.state)
        UserInitiatedDownloadRegistry.update(context, running.copy(state = DownloadQueueState.CANCELLED))
        UserInitiatedDownloadRegistry.update(context, running.copy(state = DownloadQueueState.SUCCEEDED))
        UserInitiatedDownloadRegistry.reload(context)
        assertEquals(DownloadQueueState.CANCELLED, UserInitiatedDownloadRegistry.observe(context).value[id]?.state)
        UserInitiatedDownloadRegistry.remove(context, id)
    }

    @Test fun `orphaned platform tasks become recoverable terminal rows`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = UUID.randomUUID()
        UserInitiatedDownloadRegistry.reload(context)
        UserInitiatedDownloadRegistry.update(context, DownloadQueueItem(id.toString(), DownloadQueueState.RUNNING))
        UserInitiatedDownloadRegistry.reconcile(context)
        val recovered = UserInitiatedDownloadRegistry.observe(context).value[id]!!
        assertEquals(DownloadQueueState.FAILED, recovered.state)
        assertEquals(DownloadFailure.PLATFORM_LIMIT.code, recovered.error)
        assertEquals(DownloadPhase.COMPLETE, recovered.phase)
        UserInitiatedDownloadRegistry.remove(context, id)
    }
    @Test
    fun `durable checkpoint restores retry state phase and progress`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = UUID.randomUUID()
        val checkpoint = DownloadQueueItem(
            id = id.toString(),
            state = DownloadQueueState.RETRYING,
            progress = 47.5f,
            etaSeconds = 21L,
            downloadedBytes = 475L,
            totalBytes = 1_000L,
            error = DownloadFailure.PLATFORM_LIMIT.code,
            phase = DownloadPhase.PROCESSING
        )

        try {
            UserInitiatedDownloadRegistry.update(context, checkpoint)
            UserInitiatedDownloadRegistry.reload(context)

            assertEquals(checkpoint, UserInitiatedDownloadRegistry.observe(context).value[id])
        } finally {
            UserInitiatedDownloadRegistry.remove(context, id)
        }
    }

    @Test
    fun `missing optional failure stays null after reload`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = UUID.randomUUID()
        val checkpoint = DownloadQueueItem(
            id = id.toString(),
            state = DownloadQueueState.RUNNING,
            phase = DownloadPhase.TRANSFER
        )

        try {
            UserInitiatedDownloadRegistry.update(context, checkpoint)
            UserInitiatedDownloadRegistry.reload(context)

            assertNull(UserInitiatedDownloadRegistry.observe(context).value[id]?.error)
        } finally {
            UserInitiatedDownloadRegistry.remove(context, id)
        }
    }
}
