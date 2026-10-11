package dev.qtremors.acqua.feature.downloader

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.acqua.MainDispatcherRule
import dev.qtremors.acqua.data.history.HistoryWriter
import dev.qtremors.acqua.data.settings.DownloadSettings
import dev.qtremors.acqua.data.settings.MediaProcessingSettings
import dev.qtremors.acqua.data.settings.SettingsStore
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.MediaResolutionGateway
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.downloader.DownloadFailure
import dev.qtremors.acqua.downloader.DownloadQueueGateway
import dev.qtremors.acqua.downloader.DownloadQueueItem
import dev.qtremors.acqua.downloader.DownloadQueueSnapshot
import dev.qtremors.acqua.downloader.DownloadQueueState
import dev.qtremors.acqua.downloader.YtDlpCancellation
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloaderViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `saved inputs restore and preference intents use narrow settings contract`() = runTest {
        val settings = FakeSettingsStore()
        val savedState = SavedStateHandle()
        val viewModel = createViewModel(settings = settings, savedState = savedState)

        viewModel.updateUrl("https://example.com/media")
        viewModel.setDownloadEngine(DownloadEngine.ACQUA)
        viewModel.setDownloadContentType(DownloadContentType.AUDIO)
        viewModel.setMaximumVideoHeight(720)
        viewModel.setAudioFormat(AudioOutputFormat.MP3)
        viewModel.setEmbedMetadata(false)
        viewModel.setCropArtwork(false)
        viewModel.setPreserveOriginalMetadata(true)

        val restored = createViewModel(settings = settings, savedState = savedState).state.value
        assertEquals("https://example.com/media", restored.url)
        assertEquals(DownloadEngine.ACQUA, restored.downloadEngine)
        assertEquals(DownloadContentType.AUDIO, restored.downloadContentType)
        assertEquals(720, restored.maximumVideoHeight)
        assertEquals(AudioOutputFormat.MP3, restored.audioFormat)
        assertFalse(restored.embedMetadata)
        assertFalse(restored.cropArtwork)
        assertTrue(restored.preserveOriginalMetadata)
        assertEquals(DownloadEngine.ACQUA, settings.engine)
        assertEquals(DownloadContentType.AUDIO, settings.contentType)
    }

    @Test
    fun `changing url cancels stale resolution and clears its state`() = runTest {
        var resolutionCancelled = false
        val resolution = object : MediaResolutionGateway {
            override suspend fun resolve(
                input: String,
                browserSessionsEnabled: Boolean,
                forceBrowserResolution: Boolean,
                engine: DownloadEngine,
                allowBrowserFallback: Boolean,
                browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>
            ): List<ResolvedMedia> {
                try {
                    awaitCancellation()
                } finally {
                    resolutionCancelled = true
                }
            }
        }
        val engine = FakeCancellation()
        val viewModel = createViewModel(resolution = resolution, cancellation = engine)

        viewModel.updateUrl("https://example.com/first")
        viewModel.resolve(false, 1) { _, _ -> emptyList() }
        runCurrent()
        advanceTimeBy(351)
        runCurrent()
        viewModel.updateUrl("https://example.com/second")
        advanceUntilIdle()

        assertTrue(resolutionCancelled)
        assertTrue(engine.count >= 2)
        assertEquals("https://example.com/second", viewModel.state.value.url)
        assertFalse(viewModel.state.value.isResolving)
        assertNull(viewModel.state.value.media)
    }

    @Test
    fun `resolved media becomes authoritative and duplicate request is idempotent`() = runTest {
        var calls = 0
        val item = ResolvedMedia("https://cdn.example.com/image.jpg", MediaKind.IMAGE)
        val resolution = object : MediaResolutionGateway {
            override suspend fun resolve(
                input: String,
                browserSessionsEnabled: Boolean,
                forceBrowserResolution: Boolean,
                engine: DownloadEngine,
                allowBrowserFallback: Boolean,
                browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>
            ): List<ResolvedMedia> {
                calls += 1
                return listOf(item)
            }
        }
        val viewModel = createViewModel(resolution = resolution)

        viewModel.updateUrl("https://example.com/post")
        viewModel.resolve(false, 7) { _, _ -> emptyList() }
        viewModel.resolve(false, 7) { _, _ -> emptyList() }
        runCurrent()
        advanceTimeBy(351)
        advanceUntilIdle()

        assertEquals(1, calls)
        assertEquals(listOf(item), viewModel.state.value.media)
        assertFalse(viewModel.state.value.isResolving)
    }

    @Test
    fun `metadata rename reaches the active transfer and stops when finalization begins`() = runTest {
        val item = ResolvedMedia("https://cdn.example.com/song.m4a", MediaKind.AUDIO, title = "Original title")
        val resolution = object : MediaResolutionGateway {
            override suspend fun resolve(
                input: String, browserSessionsEnabled: Boolean, forceBrowserResolution: Boolean,
                engine: DownloadEngine, allowBrowserFallback: Boolean,
                browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>
            ) = listOf(item)
        }
        val queue = FakeQueueGateway(hold = true)
        val viewModel = createViewModel(resolution = resolution, queue = queue)
        viewModel.updateUrl("https://example.com/song")
        viewModel.resolve(false, 1) { _, _ -> emptyList() }
        advanceUntilIdle()
        viewModel.downloadOne(item, 0, 0, 0)
        runCurrent()
        assertEquals(0, viewModel.state.value.savingItemIndex)

        viewModel.renameMedia(0, "My song")
        val id = UUID.fromString(queue.jobs.value.single().id)
        assertEquals("My song", queue.renamed[id])
        assertEquals("My song", viewModel.state.value.media!!.single().downloadName)
        assertEquals("Original title", viewModel.state.value.media!!.single().title)
        queue.acceptsRename = false
        viewModel.renameMedia(0, "Too late")
        assertEquals("My song", viewModel.state.value.media!!.single().downloadName)
        queue.jobs.value = queue.jobs.value.map { it.copy(state = DownloadQueueState.SUCCEEDED) }
        advanceUntilIdle()
    }

    @Test
    fun `session retry resolves fresh media after a failed browser seeded download in either engine`() = runTest {
        for (backend in MediaBackend.entries) {
            val original = ResolvedMedia("https://example.com/expired", MediaKind.VIDEO, backend = backend)
            val refreshed = original.copy(url = "https://example.com/current", explicitBrowserSessionAuthorized = true)
            var calls = 0
            val resolution = object : MediaResolutionGateway {
                override suspend fun resolve(
                    input: String, browserSessionsEnabled: Boolean, forceBrowserResolution: Boolean,
                    engine: DownloadEngine, allowBrowserFallback: Boolean,
                    browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>
                ): List<ResolvedMedia> {
                    calls++
                    assertTrue(browserSessionsEnabled)
                    assertEquals(if (backend == MediaBackend.DIRECT) DownloadEngine.ACQUA else DownloadEngine.YT_DLP, engine)
                    return listOf(refreshed)
                }
            }
            val queue = FakeQueueGateway().apply { nextState = DownloadQueueState.FAILED }
            val viewModel = createViewModel(resolution = resolution, queue = queue)
            viewModel.seedResolvedMedia("https://example.com/post", listOf(original))
            viewModel.downloadOne(original, 0, 0, 0)
            advanceUntilIdle()
            assertTrue(viewModel.state.value.error is DownloaderProblem.Download)

            viewModel.resetResolution()
            viewModel.resolve(true, 1) { _, _ -> error("Unexpected browser launch") }
            advanceUntilIdle()

            assertEquals(1, calls)
            assertEquals(listOf(refreshed), viewModel.state.value.media)
            assertNull(viewModel.state.value.error)
            assertFalse(viewModel.state.value.showBrowserAction)
        }
    }

    @Test
    fun `individual retries clear the failure and browser actions in either engine`() = runTest {
        for (backend in MediaBackend.entries) {
            val item = ResolvedMedia("https://example.com/media", MediaKind.VIDEO, backend = backend)
            val queue = FakeQueueGateway().apply { nextState = DownloadQueueState.FAILED }
            val viewModel = createViewModel(queue = queue)
            viewModel.seedResolvedMedia("https://example.com/post", listOf(item))
            viewModel.downloadOne(item, 0, 0, 0)
            advanceUntilIdle()
            assertTrue(viewModel.state.value.showBrowserAction)

            queue.nextState = DownloadQueueState.SUCCEEDED
            viewModel.downloadOne(item, 0, 0, 0)
            advanceUntilIdle()

            assertNull(viewModel.state.value.error)
            assertNull(viewModel.state.value.resolutionFailure)
            assertFalse(viewModel.state.value.showBrowserAction)
            assertEquals(MediaKind.VIDEO, viewModel.state.value.lastDownloadedKind)
        }
    }

    @Test
    fun `download all routes each item through its own backend`() = runTest {
        for (backend in MediaBackend.entries) {
            val items = (0..1).map { ResolvedMedia("https://example.com/$it", MediaKind.VIDEO, backend = backend) }
            val queue = FakeQueueGateway()
            val viewModel = createViewModel(queue = queue)
            viewModel.seedResolvedMedia("https://example.com/post", items)

            viewModel.downloadAll(false) { _, _ -> error("Media is already resolved") }
            advanceUntilIdle()

            assertEquals(items, if (backend == MediaBackend.DIRECT) queue.direct else queue.processed)
            assertTrue((if (backend == MediaBackend.DIRECT) queue.processed else queue.direct).isEmpty())
            assertEquals(MediaKind.VIDEO, viewModel.state.value.lastDownloadedKind)
            assertNull(viewModel.state.value.error)
        }
    }

    private fun createViewModel(
        settings: FakeSettingsStore = FakeSettingsStore(),
        savedState: SavedStateHandle = SavedStateHandle(),
        resolution: MediaResolutionGateway = EmptyResolution,
        cancellation: FakeCancellation = FakeCancellation(),
        queue: FakeQueueGateway = FakeQueueGateway()
    ) = DownloaderViewModel(
        history = HistoryWriter { 1L },
        resolution = resolution,
        settings = settings,
        ytDlpEngine = cancellation,
        ytDlpDownloads = queue,
        savedStateHandle = savedState,
        ioDispatcher = mainDispatcherRule.dispatcher
    )

    private object EmptyResolution : MediaResolutionGateway {
        override suspend fun resolve(
            input: String,
            browserSessionsEnabled: Boolean,
            forceBrowserResolution: Boolean,
            engine: DownloadEngine,
            allowBrowserFallback: Boolean,
            browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>
        ) = emptyList<ResolvedMedia>()
    }

    private class FakeCancellation : YtDlpCancellation {
        var count = 0
        override fun cancelAll() { count += 1 }
    }

    private class FakeQueueGateway(val hold: Boolean = false) : DownloadQueueGateway {
        private val queue = MutableStateFlow(DownloadQueueSnapshot())
        val jobs = MutableStateFlow<List<DownloadQueueItem>>(emptyList())
        val renamed = mutableMapOf<UUID, String>()
        val processed = mutableListOf<ResolvedMedia>()
        val options = mutableListOf<YtDlpDownloadOptions>()
        val direct = mutableListOf<ResolvedMedia>()
        var nextState = if (hold) DownloadQueueState.RUNNING else DownloadQueueState.SUCCEEDED
        var acceptsRename = true
        override fun enqueue(
            media: ResolvedMedia,
            outputMedia: ResolvedMedia,
            options: YtDlpDownloadOptions,
            sourceUrl: String
        ): UUID {
            processed += media
            this.options += options
            return enqueueJob()
        }
        override fun enqueueDirect(media: ResolvedMedia, index: Int, sourceUrl: String, itemCount: Int): UUID =
            enqueueJob().also { direct += media }
        private fun enqueueJob(): UUID = UUID.randomUUID().also { id ->
            jobs.value += DownloadQueueItem(id.toString(), nextState, error =
                if (nextState == DownloadQueueState.FAILED) DownloadFailure.NETWORK.code else null)
        }
        override fun observe(ids: List<UUID>): Flow<List<DownloadQueueItem>> {
            val requested = ids.map(UUID::toString).toSet()
            return if (hold) jobs.map { items -> items.filter { it.id in requested } }
            else flowOf(jobs.value.filter { it.id in requested })
        }
        override fun observeQueue(): Flow<DownloadQueueSnapshot> = queue
        override fun cancel(id: UUID) = Unit
        override fun rename(id: UUID, name: String): Boolean {
            if (!acceptsRename) return false
            renamed[id] = name
            return true
        }
    }

    private class FakeSettingsStore : SettingsStore {
        private val protection = MutableStateFlow(false)
        var download = DownloadSettings("Acqua", false, "{title}.{ext}", "{title}.{ext}")
        var media = MediaProcessingSettings(0, AudioOutputFormat.ORIGINAL, true, true, true, 0L)
        var engine = DownloadEngine.YT_DLP
        var contentType: DownloadContentType? = null

        override fun screenProtectionEnabled() = protection.value
        override fun setScreenProtectionEnabled(enabled: Boolean) { protection.value = enabled }
        override fun screenProtectionEnabledFlow(): Flow<Boolean> = protection
        override fun downloadSettings() = download
        override fun setBaseFolder(value: String) { download = download.copy(baseFolder = value) }
        override fun setCategorizeMedia(enabled: Boolean) { download = download.copy(categorizeMedia = enabled) }
        override fun setFilenamePattern(value: String) { download = download.copy(filenamePattern = value) }
        override fun setAudioFilenamePattern(value: String) { download = download.copy(audioFilenamePattern = value) }
        override fun mediaProcessingSettings() = media
        override fun setMaximumVideoHeight(value: Int) { media = media.copy(maximumVideoHeight = value) }
        override fun setAudioFormat(value: AudioOutputFormat) { media = media.copy(audioFormat = value) }
        override fun setEmbedMetadata(enabled: Boolean) { media = media.copy(embedMetadata = enabled) }
        override fun setEmbedThumbnail(enabled: Boolean) { media = media.copy(embedThumbnail = enabled) }
        override fun setCropArtwork(enabled: Boolean) { media = media.copy(cropArtwork = enabled) }
        override fun setPreserveOriginalMetadata(enabled: Boolean) { media = media.copy(preserveOriginalMetadata = enabled) }
        override fun setAutoUpdateYtDlp(enabled: Boolean) { media = media.copy(autoUpdateYtDlp = enabled) }
        override fun lastDownloadEngine() = engine
        override fun setLastDownloadEngine(value: DownloadEngine) { engine = value }
        override fun lastDownloadContentType() = contentType
        override fun setLastDownloadContentType(value: DownloadContentType) { contentType = value }
    }
}
