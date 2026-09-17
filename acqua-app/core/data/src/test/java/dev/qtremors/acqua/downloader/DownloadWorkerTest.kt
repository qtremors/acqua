package dev.qtremors.acqua.downloader

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `successful execution completes worker and reports each phase`() = runBlocking {
        val phases = mutableListOf<DownloadPhase>()
        val executor = FakeExecution { _, _, onProgress, onPhase ->
            onPhase(DownloadPhase.TRANSFER)
            phases += DownloadPhase.TRANSFER
            onProgress(YtDlpProgress(percent = 60f, downloadedBytes = 600L, totalBytes = 1_000L))
            onPhase(DownloadPhase.PROCESSING)
            phases += DownloadPhase.PROCESSING
            onPhase(DownloadPhase.FINALIZING)
            phases += DownloadPhase.FINALIZING
            completedResult()
        }

        val result = worker(executor).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(
            listOf(DownloadPhase.TRANSFER, DownloadPhase.PROCESSING, DownloadPhase.FINALIZING),
            phases
        )
        assertEquals(1, executor.executions)
    }

    @Test
    fun `processed request reaches the injected executor with processing options`() = runBlocking {
        var observedRequest: DownloadWorkRequest? = null
        val executor = FakeExecution { request, _, _, onPhase ->
            observedRequest = request
            onPhase(DownloadPhase.TRANSFER)
            onPhase(DownloadPhase.PROCESSING)
            onPhase(DownloadPhase.FINALIZING)
            completedResult()
        }

        val result = worker(executor, input = processedInput()).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(MediaBackend.YT_DLP, observedRequest?.media?.backend)
        assertEquals(DownloadContentType.AUDIO, observedRequest?.options?.contentType)
        assertEquals(AudioOutputFormat.M4A, observedRequest?.options?.audioFormat)
    }

    @Test
    fun `transient network failure retries within the bounded attempt budget`() = runBlocking {
        val result = worker(
            FakeExecution { _, _, _, _ -> throw IOException("offline") },
            runAttemptCount = 1
        ).doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `transient failure becomes a typed terminal result after retry budget`() = runBlocking {
        val result = worker(
            FakeExecution { _, _, _, _ -> throw IOException("offline") },
            runAttemptCount = 2
        ).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(
            DownloadFailure.NETWORK.code,
            (result as ListenableWorker.Result.Failure).outputData
                .getString(DownloadWorkData.KEY_ERROR)
        )
    }

    @Test
    fun `permanent failure does not retry`() = runBlocking {
        val result = worker(
            FakeExecution { _, _, _, _ -> error("unsupported response") }
        ).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(
            DownloadFailure.UNKNOWN.code,
            (result as ListenableWorker.Result.Failure).outputData
                .getString(DownloadWorkData.KEY_ERROR)
        )
    }

    @Test
    fun `worker cancellation reaches the injected executor`() = runBlocking {
        var cancelled = false
        val executor = FakeExecution { _, _, _, _ ->
            try {
                awaitCancellation()
            } catch (error: CancellationException) {
                cancelled = true
                throw error
            }
        }
        val running = launch { worker(executor).doWork() }

        while (executor.executions == 0) kotlinx.coroutines.yield()
        running.cancelAndJoin()

        assertTrue(cancelled)
    }

    private fun worker(
        executor: DownloadExecution,
        runAttemptCount: Int = 0,
        input: androidx.work.Data = inputData()
    ): DownloadWorker = TestListenableWorkerBuilder<DownloadWorker>(context)
        .setInputData(input)
        .setRunAttemptCount(runAttemptCount)
        .setWorkerFactory(AcquaWorkerFactory(executor))
        .build()

    private fun inputData() = DownloadWorkData.directRequest(
        ResolvedMedia(
            url = "https://cdn.example/media.mp4",
            kind = MediaKind.VIDEO,
            backend = MediaBackend.DIRECT,
            mimeType = "video/mp4",
            fileExtension = "mp4"
        ),
        itemIndex = 0,
        sourceUrl = "https://example.com/post/1"
    )

    private fun processedInput(): androidx.work.Data {
        val media = ResolvedMedia(
            url = "https://example.com/watch/1",
            kind = MediaKind.VIDEO,
            backend = MediaBackend.YT_DLP,
            title = "Processed media"
        )
        return DownloadWorkData.processedRequest(
            media,
            media.copy(width = 1280, height = 720),
            YtDlpDownloadOptions(
                contentType = DownloadContentType.AUDIO,
                audioFormat = AudioOutputFormat.M4A
            ),
            sourceUrl = media.url
        )
    }

    private fun completedResult() = DownloadExecutionResult(
        uri = Uri.parse("content://downloads/1"),
        title = "Test download",
        mimeType = "video/mp4",
        mediaKind = MediaKind.VIDEO
    )

    private class FakeExecution(
        private val block: suspend (
            DownloadWorkRequest,
            String,
            (YtDlpProgress) -> Unit,
            (DownloadPhase) -> Unit
        ) -> DownloadExecutionResult
    ) : DownloadExecution {
        var executions: Int = 0
            private set

        override suspend fun execute(
            request: DownloadWorkRequest,
            taskKey: String,
            onProgress: (YtDlpProgress) -> Unit,
            onPhase: (DownloadPhase) -> Unit
        ): DownloadExecutionResult {
            executions += 1
            return block(request, taskKey, onProgress, onPhase)
        }
    }
}
