package dev.qtremors.acqua.feature.downloader

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.acqua.MainDispatcherRule
import dev.qtremors.acqua.data.history.HistoryWriter
import dev.qtremors.acqua.data.settings.DownloadSettings
import dev.qtremors.acqua.data.settings.MediaProcessingSettings
import dev.qtremors.acqua.data.settings.SettingsStore
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.MediaResolutionGateway
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.downloader.DownloadQueueGateway
import dev.qtremors.acqua.downloader.DownloadQueueItem
import dev.qtremors.acqua.downloader.DownloadQueueSnapshot
import dev.qtremors.acqua.downloader.YtDlpCancellation
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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

        val restored = createViewModel(settings = settings, savedState = savedState).state.value
        assertEquals("https://example.com/media", restored.url)
        assertEquals(DownloadEngine.ACQUA, restored.downloadEngine)
        assertEquals(DownloadContentType.AUDIO, restored.downloadContentType)
        assertEquals(720, restored.maximumVideoHeight)
        assertEquals(AudioOutputFormat.MP3, restored.audioFormat)
        assertFalse(restored.embedMetadata)
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

    private fun createViewModel(
        settings: FakeSettingsStore = FakeSettingsStore(),
        savedState: SavedStateHandle = SavedStateHandle(),
        resolution: MediaResolutionGateway = EmptyResolution,
        cancellation: FakeCancellation = FakeCancellation()
    ) = DownloaderViewModel(
        history = HistoryWriter { 1L },
        resolution = resolution,
        settings = settings,
        ytDlpEngine = cancellation,
        ytDlpDownloads = FakeQueueGateway(),
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

    private class FakeQueueGateway : DownloadQueueGateway {
        private val queue = MutableStateFlow(DownloadQueueSnapshot())
        override fun enqueue(
            media: ResolvedMedia,
            outputMedia: ResolvedMedia,
            options: YtDlpDownloadOptions,
            sourceUrl: String
        ) = UUID.randomUUID()
        override fun enqueueDirect(media: ResolvedMedia, index: Int, sourceUrl: String, itemCount: Int) =
            UUID.randomUUID()
        override fun observe(ids: List<UUID>): Flow<List<DownloadQueueItem>> = flowOf(emptyList())
        override fun observeQueue(): Flow<DownloadQueueSnapshot> = queue
        override fun cancel(id: UUID) = Unit
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
        override fun setAutoUpdateYtDlp(enabled: Boolean) { media = media.copy(autoUpdateYtDlp = enabled) }
        override fun lastDownloadEngine() = engine
        override fun setLastDownloadEngine(value: DownloadEngine) { engine = value }
        override fun lastDownloadContentType() = contentType
        override fun setLastDownloadContentType(value: DownloadContentType) { contentType = value }
    }
}
