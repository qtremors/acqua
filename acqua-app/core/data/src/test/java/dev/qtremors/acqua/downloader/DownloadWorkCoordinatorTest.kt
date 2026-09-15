package dev.qtremors.acqua.downloader

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import java.util.Collections
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DownloadWorkCoordinatorTest {
    @Test
    fun `pre Android 14 fallback executes an appended batch in submission order`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val order = Collections.synchronizedList(mutableListOf<Int>())
        val executor = object : DownloadExecution {
            override suspend fun execute(
                request: DownloadWorkRequest,
                taskKey: String,
                onProgress: (YtDlpProgress) -> Unit,
                onPhase: (DownloadPhase) -> Unit
            ): DownloadExecutionResult {
                order += request.itemIndex
                onPhase(DownloadPhase.TRANSFER)
                onProgress(YtDlpProgress(100f, downloadedBytes = 10L, totalBytes = 10L))
                onPhase(DownloadPhase.FINALIZING)
                return DownloadExecutionResult(
                    Uri.parse("content://downloads/${request.itemIndex}"),
                    "item-${request.itemIndex}",
                    "video/mp4",
                    MediaKind.VIDEO
                )
            }
        }
        val configuration = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setTaskExecutor(SynchronousExecutor())
            .setWorkerFactory(AcquaWorkerFactory(executor))
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        val coordinator = DownloadWorkCoordinator(context)
        val media = ResolvedMedia(
            url = "https://cdn.example/media.mp4",
            kind = MediaKind.VIDEO,
            backend = MediaBackend.DIRECT,
            mimeType = "video/mp4",
            fileExtension = "mp4"
        )

        val ids = (0..2).map { index ->
            coordinator.enqueueDirect(media, index, "https://example.com/post", itemCount = 3)
        }
        val driver = requireNotNull(WorkManagerTestInitHelper.getTestDriver(context))
        ids.forEach(driver::setAllConstraintsMet)
        val terminal = withTimeout(5_000L) {
            coordinator.observe(ids).first { items -> items.all { !it.isActive } }
        }

        assertEquals(listOf(0, 1, 2), order)
        assertTrue(terminal.all { it.state == DownloadQueueState.SUCCEEDED })
        WorkManager.getInstance(context).pruneWork().result.get()
        Unit
    }
}
