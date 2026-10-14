package dev.qtremors.acqua.downloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class YtDlpMaintenanceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = AppSettingsRepository(context)
    private var time = 5_000_000_000L

    @Test fun `disabled checks do not initialize runtime and manual updates use every selected channel`() = runBlocking {
        val channels = mutableListOf<YtDlpUpdateChannel>()
        val maintenance = maintenance { channels += it; YtDlpUpdateStatus.UPDATED }
        settings.setAutoUpdateYtDlp(false)
        assertNull(maintenance.update(false).version)
        assertEquals(emptyList<YtDlpUpdateChannel>(), channels)
        for (channel in YtDlpUpdateChannel.entries) {
            settings.setYtDlpUpdateChannel(channel)
            val result = maintenance.update(true)
            assertEquals(YtDlpUpdateStatus.UPDATED, result.updateStatus)
            assertEquals(time, settings.mediaProcessingSettings().lastYtDlpUpdate)
            assertEquals("current", settings.cachedYtDlpVersion())
        }
        assertEquals(YtDlpUpdateChannel.entries, channels)
    }

    @Test fun `daily weekly and monthly intervals survive recreation and check only when due`() = runBlocking {
        var calls = 0
        for (interval in YtDlpUpdateInterval.entries) {
            settings.setYtDlpUpdateInterval(interval)
            val last = time
            settings.markYtDlpUpdated(last, "current")
            settings.markYtDlpAttempt(last)
            time = last + interval.milliseconds - 1
            assertNull(maintenance { calls++; YtDlpUpdateStatus.ALREADY_CURRENT }.update(false).updateStatus)
            time++
            assertEquals(YtDlpUpdateStatus.ALREADY_CURRENT, maintenance { calls++; YtDlpUpdateStatus.ALREADY_CURRENT }.update(false).updateStatus)
            assertNull(maintenance { calls++; YtDlpUpdateStatus.UPDATED }.update(false).updateStatus)
        }
        assertEquals(3, calls)
        assertEquals(YtDlpUpdateInterval.MONTHLY, AppSettingsRepository(context).mediaProcessingSettings().ytDlpUpdateInterval)
    }

    @Test fun `failed checks keep the last success and retry after six hours or a manual request`() = runBlocking {
        val lastSuccess = time - YtDlpUpdateInterval.WEEKLY.milliseconds
        settings.markYtDlpUpdated(lastSuccess, "previous")
        var calls = 0
        var failing = true
        val maintenance = maintenance {
            calls++
            if (failing) throw IOException("offline")
            YtDlpUpdateStatus.UPDATED
        }
        assertThrows(IOException::class.java) { runBlocking { maintenance.update(false) } }
        assertEquals(lastSuccess, settings.mediaProcessingSettings().lastYtDlpUpdate)
        assertEquals("previous", maintenance.update(false).version)
        time += 6 * 60 * 60 * 1000L - 1
        assertNull(maintenance.update(false).updateStatus)
        time++
        assertThrows(IOException::class.java) { runBlocking { maintenance.update(false) } }
        assertEquals(2, calls)
        failing = false
        assertEquals(YtDlpUpdateStatus.UPDATED, maintenance.update(true).updateStatus)
        assertEquals(time, settings.mediaProcessingSettings().lastYtDlpUpdate)
        assertEquals(3, calls)
    }

    @Test fun `simultaneous automatic entry points perform one update`() = runBlocking {
        val calls = AtomicInteger()
        val results = (1..8).map {
            async(Dispatchers.Default) { maintenance { calls.incrementAndGet(); YtDlpUpdateStatus.UPDATED }.update(false) }
        }.awaitAll()
        assertEquals(1, calls.get())
        assertEquals(1, results.count { it.updateStatus != null })
    }

    @Test fun `channel changes during an update remain due on the next entry`() = runBlocking {
        val channels = mutableListOf<YtDlpUpdateChannel>()
        val maintenance = maintenance { channel ->
            channels += channel
            if (channel == YtDlpUpdateChannel.STABLE) settings.setYtDlpUpdateChannel(YtDlpUpdateChannel.NIGHTLY)
            YtDlpUpdateStatus.UPDATED
        }
        maintenance.update(false)
        assertEquals(0L, settings.mediaProcessingSettings().lastYtDlpUpdate)
        maintenance.update(false)
        assertEquals(listOf(YtDlpUpdateChannel.STABLE, YtDlpUpdateChannel.NIGHTLY), channels)
        assertEquals(time, settings.mediaProcessingSettings().lastYtDlpUpdate)
    }

    private fun maintenance(update: (YtDlpUpdateChannel) -> YtDlpUpdateStatus) = YtDlpMaintenance(
        context, settings, now = { time }, updateRuntime = update, runtimeVersion = { "current" }
    )
}
