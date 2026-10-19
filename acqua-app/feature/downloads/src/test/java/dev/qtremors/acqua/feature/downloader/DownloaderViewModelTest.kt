package dev.qtremors.acqua.feature.downloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.SavedStateHandle
import dev.qtremors.acqua.MainDispatcherRule
import dev.qtremors.acqua.data.history.HistoryWriter
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.data.settings.DownloadSettings
import dev.qtremors.acqua.data.settings.MediaProcessingSettings
import dev.qtremors.acqua.data.settings.SettingsStore
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.MediaResolutionGateway
import dev.qtremors.acqua.domain.MediaResolutionFailure
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.downloader.CaptionMode
import dev.qtremors.acqua.downloader.CaptionFormat
import dev.qtremors.acqua.downloader.VideoContainer
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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

    @Test fun `partial scheduling retains accepted work and retries only rejected items`() = runTest {
        for (backend in MediaBackend.entries) {
          for (rejectedIndex in 0..2) {
            val queue = FakeQueueGateway().apply { failAt = rejectedIndex }
            val viewModel = createViewModel(queue = queue)
            val items = (0..2).map { ResolvedMedia("https://example.com/$it", MediaKind.VIDEO, backend = backend) }
            viewModel.seedResolvedMedia("https://example.com/post", items)
            viewModel.downloadAll(false) { _, _ -> emptyList() }
            advanceUntilIdle()
            assertEquals(2, queue.jobs.value.size)
            assertEquals(2, viewModel.state.value.acceptedMediaTasks.size)
            assertFalse(viewModel.state.value.isSaving)
            assertTrue(viewModel.state.value.error is DownloaderProblem.Download)
            queue.failAt = null
            viewModel.downloadAll(false) { _, _ -> emptyList() }
            advanceUntilIdle()
            assertEquals(3, queue.jobs.value.size)
            assertNull(viewModel.state.value.error)
            assertEquals(3, viewModel.state.value.acceptedMediaTasks.size)
          }
        }
    }

    @Test fun `single item enqueue failure restores usable controls`() = runTest {
      for (backend in MediaBackend.entries) {
        val queue = FakeQueueGateway().apply { failAt = 0 }
        val viewModel = createViewModel(queue = queue)
        val item = ResolvedMedia("https://example.com/file", MediaKind.VIDEO, backend = backend)
        viewModel.seedResolvedMedia("https://example.com/post", listOf(item))
        viewModel.downloadOne(item, 0, 0, 0)
        advanceUntilIdle()
        assertNull(viewModel.state.value.savingItemIndex)
        assertTrue(viewModel.state.value.error is DownloaderProblem.Download)
        assertTrue(queue.jobs.value.isEmpty())
      }
    }

    @Test fun `preview selection restores and automatically cancels when its last item is removed`() = runTest {
        val saved = SavedStateHandle()
        val model = createViewModel(savedState = saved)
        val items = List(10) { ResolvedMedia("https://example.com/$it", MediaKind.IMAGE) }
        model.seedResolvedMedia("https://example.com/post", items)
        model.selectPreviewItem(0, true)
        assertEquals(setOf(0), model.state.value.selectedCollectionItems)
        model.selectPreviewItem(2, false)
        assertEquals(setOf(0, 2), createViewModel(savedState = saved).state.value.selectedCollectionItems)
        model.selectPreviewItem(2, true)
        assertEquals(setOf(0, 2), model.state.value.selectedCollectionItems)
        model.selectPreviewItems(model.state.value.availableCollectionIndices() - model.state.value.selectedIndices())
        assertEquals(setOf(1, 3, 4, 5, 6, 7, 8, 9), model.state.value.selectedCollectionItems)
        model.selectPreviewItems(model.state.value.availableCollectionIndices())
        model.selectPreviewItems(model.state.value.availableCollectionIndices() - model.state.value.selectedIndices())
        assertNull(model.state.value.selectedCollectionItems)
        model.selectPreviewItem(1, true)
        model.selectPreviewItem(1, false)
        assertNull(createViewModel(savedState = saved).state.value.selectedCollectionItems)
        model.selectPreviewItem(2, true)
        model.updateUrl("https://example.com/another-post")
        assertNull(model.state.value.selectedCollectionItems)
        model.seedResolvedMedia("https://example.com/post", items)
        model.selectPreviewItem(0, true)
        model.setDownloadEngine(DownloadEngine.YT_DLP)
        assertNull(createViewModel(savedState = saved).state.value.selectedCollectionItems)
    }

    @Test fun `preview selections enqueue only chosen media across engines and media types`() = runTest {
        for (backend in MediaBackend.entries) for (kind in MediaKind.entries) {
            val queue = FakeQueueGateway()
            val model = createViewModel(queue = queue)
            val items = List(4) { ResolvedMedia("https://example.com/$it", kind, backend = backend) }
            model.seedResolvedMedia("https://example.com/post", items)
            model.selectPreviewItem(1, true)
            model.selectPreviewItem(3, false)
            model.downloadAll(false) { _, _ -> error("Already fetched") }
            advanceUntilIdle()
            assertEquals("$backend $kind", listOf(items[1], items[3]), if (backend == MediaBackend.DIRECT) queue.direct else queue.processed)
        }
    }

    @Test fun `preview selections exclude unavailable entries without changing individual downloads`() = runTest {
        val queue = FakeQueueGateway()
        val model = createViewModel(queue = queue)
        val items = List(3) { ResolvedMedia("https://example.com/$it", MediaKind.VIDEO,
            unavailableReason = if (it == 2) "Unavailable" else null) }
        model.seedResolvedMedia("https://example.com/post", items)
        model.selectPreviewItem(2, true)
        assertNull(model.state.value.selectedCollectionItems)
        model.selectPreviewItems(setOf(0, 1, 2, 99))
        assertEquals(setOf(0, 1), model.state.value.selectedCollectionItems)
        model.selectPreviewItems(setOf(0))
        model.downloadOne(items[1], 1, 0, 0)
        advanceUntilIdle()
        assertEquals(listOf(items[1]), queue.direct)
    }

    @Test fun `preview completion reports the kind of only the selected media`() = runTest {
        val queue = FakeQueueGateway()
        val model = createViewModel(queue = queue)
        val items = listOf(MediaKind.IMAGE, MediaKind.VIDEO, MediaKind.AUDIO).mapIndexed { index, kind ->
            ResolvedMedia("https://example.com/$index", kind)
        }
        model.seedResolvedMedia("https://example.com/post", items)
        model.selectPreviewItem(0, true)
        model.downloadAll(false) { _, _ -> error("Already fetched") }
        advanceUntilIdle()
        assertEquals(listOf(items[0]), queue.direct)
        assertEquals(MediaKind.IMAGE, model.state.value.lastDownloadedKind)
    }

    @Test fun `collection ranges enqueue only selected entries`() = runTest {
        val queue = FakeQueueGateway()
        val saved = SavedStateHandle()
        val viewModel = createViewModel(queue = queue, savedState = saved)
        val items = (1..5).map { ResolvedMedia("https://example.com/$it", MediaKind.VIDEO,
            backend = MediaBackend.YT_DLP, collectionUrl = "https://example.com/list", collectionIndex = it) }
        viewModel.seedResolvedMedia("https://example.com/list", items)
        assertTrue(viewModel.selectCollectionRange("1,3-4"))
        assertFalse(viewModel.selectCollectionRange("0"))
        assertEquals(setOf(0, 2, 3), createViewModel(savedState = saved).state.value.selectedCollectionItems)
        viewModel.downloadAll(false) { _, _ -> emptyList() }
        advanceUntilIdle()
        assertEquals(listOf(items[0], items[2], items[3]), queue.processed)
        viewModel.selectAllCollectionItems(false)
        assertEquals(emptySet<Int>(), createViewModel(savedState = saved).state.value.selectedCollectionItems)
        viewModel.updateUrl("https://example.com/new")
        assertNull(createViewModel(savedState = saved).state.value.selectedCollectionItems)
    }

    @Test fun `advanced choices survive screen recreation and reach accepted requests`() = runTest {
        val saved = SavedStateHandle()
        val queue = FakeQueueGateway()
        val viewModel = createViewModel(queue = queue, savedState = saved)
        val item = ResolvedMedia("https://example.com/video", MediaKind.VIDEO, backend = MediaBackend.YT_DLP,
            formats = listOf(dev.qtremors.acqua.domain.MediaFormatOption("137", "mp4", "avc1", "none"),
                dev.qtremors.acqua.domain.MediaFormatOption("140", "m4a", "none", "aac")))
        viewModel.seedResolvedMedia(item.url, listOf(item))
        val advanced = YtDlpDownloadOptions(videoFormatId = "137", videoFormatIncludesAudio = false,
            audioFormatId = "140", captionMode = dev.qtremors.acqua.downloader.CaptionMode.BOTH,
            captionLanguages = "en,hi", automaticCaptions = true)
        viewModel.setAdvancedOptions(advanced)
        assertEquals(advanced, createViewModel(savedState = saved).state.value.advancedOptions)
        viewModel.downloadOne(item, 0, 0, 0)
        advanceUntilIdle()
        assertEquals("137", queue.options.single().videoFormatId)
        assertEquals("140", queue.options.single().audioFormatId)
        assertEquals(advanced.captionMode, queue.options.single().captionMode)
        viewModel.updateUrl("https://example.com/another")
        assertNull(viewModel.state.value.advancedOptions.videoFormatId)
        assertNull(viewModel.state.value.advancedOptions.audioFormatId)
    }

    @Test fun `pasted batches ignore saved links and continue after a failed link`() = runTest {
        val queue = FakeQueueGateway()
        queue.savePending("https://old.example/video")
        val saved = SavedStateHandle(mapOf("downloader.pending_selection" to arrayListOf<String>()))
        val resolution = object : MediaResolutionGateway {
            override suspend fun resolve(input: String, browserSessionsEnabled: Boolean, forceBrowserResolution: Boolean,
                engine: DownloadEngine, allowBrowserFallback: Boolean,
                browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>): List<ResolvedMedia> {
                if (input.contains("bad")) error("unavailable")
                return listOf(ResolvedMedia(input, MediaKind.VIDEO, backend = MediaBackend.YT_DLP))
            }
        }
        val viewModel = createViewModel(queue = queue, resolution = resolution, savedState = saved)
        viewModel.updateUrl("https://bad.example/video https://good.example/video")
        runCurrent()
        assertTrue(viewModel.state.value.configurationActionEnabled())
        viewModel.downloadAll(false) { _, _ -> emptyList() }
        advanceUntilIdle()
        assertEquals(listOf("https://good.example/video"), queue.processed.map { it.url })
        assertEquals("Failed to queue", viewModel.state.value.pendingLinks.single { it.url == "https://bad.example/video" }.status)
        assertEquals(1, viewModel.state.value.pendingLinks.last().acceptedItems.size)
        viewModel.downloadPendingLinks(false) { _, _ -> emptyList() }
        advanceUntilIdle()
        assertEquals(1, queue.jobs.value.size)
        assertFalse(viewModel.state.value.isBatchRunning)
    }

    @Test fun `template batches snapshot each accepted request and cancellation stops later links`() = runTest {
        val queue = FakeQueueGateway()
        val visited = mutableListOf<String>()
        val wait = kotlinx.coroutines.CompletableDeferred<Unit>()
        val resolution = object : MediaResolutionGateway {
            override suspend fun resolve(input: String, browserSessionsEnabled: Boolean, forceBrowserResolution: Boolean,
                engine: DownloadEngine, allowBrowserFallback: Boolean,
                browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>): List<ResolvedMedia> {
                visited += input
                if (input.contains("second")) wait.await()
                return listOf(ResolvedMedia(input, MediaKind.VIDEO, backend = MediaBackend.YT_DLP))
            }
        }
        val model = createViewModel(queue = queue, resolution = resolution)
        model.updateUrl("https://example.com/first https://example.com/second https://example.com/third")
        runCurrent()
        val template = dev.qtremors.acqua.downloader.CommandTemplate(name = "Batch", command = "--format 'bv*+ba/b'")
        model.applyDownloadConfiguration(template.applyTo(model.state.value.configurationOptions()))
        model.downloadAll(false) { _, _ -> emptyList() }
        runCurrent()
        assertEquals(template.id, queue.options.single().commandTemplateId)
        model.cancelActiveDownload()
        wait.complete(Unit)
        advanceUntilIdle()
        assertEquals(2, visited.size)
        assertEquals(1, queue.processed.size)
        assertEquals("bv*+ba/b", queue.options.single().formatSelectorOverride)
        assertFalse(model.state.value.isBatchRunning)
    }

    @Test fun `pending collections isolate rejected entries and preserve accepted identities`() = runTest {
        for (backend in MediaBackend.entries) {
            for (rejectedIndex in 0..2) {
                val queue = FakeQueueGateway().apply { failAt = rejectedIndex }
                val link = "https://example.com/collection"
                val resolution = object : MediaResolutionGateway {
                    override suspend fun resolve(input: String, browserSessionsEnabled: Boolean,
                        forceBrowserResolution: Boolean, engine: DownloadEngine, allowBrowserFallback: Boolean,
                        browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>) = (1..3).map {
                        ResolvedMedia("https://example.com/entry/$it", MediaKind.VIDEO, backend = backend,
                            collectionUrl = link, collectionIndex = it, collectionSize = 3)
                    }
                }
                val viewModel = createViewModel(queue = queue, resolution = resolution)
                viewModel.updateUrl(link)
                viewModel.savePendingLinks()
                runCurrent()
                viewModel.downloadPendingLinks(false) { _, _ -> emptyList() }
                advanceUntilIdle()
                val accepted = viewModel.state.value.pendingLinks.single().acceptedItems
                assertEquals(2, queue.jobs.value.size)
                assertEquals(2, accepted.size)
                assertEquals("Partially queued", viewModel.state.value.pendingLinks.single().status)

                queue.failAt = null
                viewModel.downloadPendingLinks(false) { _, _ -> emptyList() }
                advanceUntilIdle()
                val completed = viewModel.state.value.pendingLinks.single()
                assertEquals(3, queue.jobs.value.size)
                assertEquals(3, completed.acceptedItems.size)
                accepted.forEach { (key, id) -> assertEquals(id, completed.acceptedItems[key]) }
                assertEquals("Queued", completed.status)
            }
        }
    }

    @Test fun `refreshed asset links do not repeat accepted pending requests`() = runTest {
        val queue = FakeQueueGateway().apply { failAt = 1 }
        var revision = 0
        val resolution = object : MediaResolutionGateway {
            override suspend fun resolve(input: String, browserSessionsEnabled: Boolean,
                forceBrowserResolution: Boolean, engine: DownloadEngine, allowBrowserFallback: Boolean,
                browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>): List<ResolvedMedia> {
                revision++
                return (1..2).map { ResolvedMedia("https://media.cdninstagram.com/$it.mp4?token=$revision", MediaKind.VIDEO) }
            }
        }
        val viewModel = createViewModel(queue = queue, resolution = resolution)
        viewModel.updateUrl("https://example.com/post")
        viewModel.savePendingLinks()
        runCurrent()
        viewModel.downloadPendingLinks(false) { _, _ -> emptyList() }
        advanceUntilIdle()
        val accepted = viewModel.state.value.pendingLinks.single().acceptedItems.values.single()
        queue.failAt = null
        viewModel.downloadPendingLinks(false) { _, _ -> emptyList() }
        advanceUntilIdle()
        assertEquals(2, queue.jobs.value.size)
        assertTrue(accepted in viewModel.state.value.pendingLinks.single().acceptedItems.values)
        assertEquals(2, viewModel.state.value.pendingLinks.single().acceptedItems.size)
    }

    @Test fun `archive skips never emit a newly saved file event`() = runTest {
        val queue = FakeQueueGateway().apply { nextState = DownloadQueueState.SKIPPED }
        val viewModel = createViewModel(queue = queue)
        val events = mutableListOf<DownloaderEvent>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
        viewModel.seedResolvedMedia("https://example.com/post", listOf(ResolvedMedia("https://example.com/video", MediaKind.VIDEO)))
        viewModel.downloadAll(false) { _, _ -> emptyList() }
        advanceUntilIdle()
        assertTrue(events.any { it is DownloaderEvent.DownloadsSkipped })
        assertFalse(events.any { it is DownloaderEvent.DownloadComplete || it is DownloaderEvent.ItemSaved })
        assertFalse(viewModel.state.value.isSaving)
        collector.cancel()
    }

    @Test fun `changing links clears clip and exact stream choices in restored state`() = runTest {
        val saved = SavedStateHandle()
        val viewModel = createViewModel(savedState = saved)
        viewModel.setAdvancedOptions(YtDlpDownloadOptions(videoFormatId = "137", clipStartSeconds = 1.0, clipEndSeconds = 9.0))
        viewModel.updateUrl("https://example.com/new")
        val restored = createViewModel(savedState = saved).state.value.advancedOptions
        assertNull(restored.videoFormatId)
        assertNull(restored.clipStartSeconds)
        assertNull(restored.clipEndSeconds)
    }

    @Test fun `remembered image choices configure every direct batch entry`() = runTest {
        val queue = FakeQueueGateway()
        val settings = realSettings()
        val resolution = object : MediaResolutionGateway {
            override suspend fun resolve(input: String, browserSessionsEnabled: Boolean,
                forceBrowserResolution: Boolean, engine: DownloadEngine, allowBrowserFallback: Boolean,
                browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>) =
                (1..2).map { ResolvedMedia("https://example.com/$it.png", MediaKind.IMAGE) }
        }
        val viewModel = createViewModel(queue = queue, settings = settings, resolution = resolution)
        viewModel.seedResolvedMedia("https://example.com/photo", listOf(ResolvedMedia("https://example.com/photo.png", MediaKind.IMAGE)))
        viewModel.setEmbedMetadata(false)
        viewModel.setPreserveOriginalMetadata(true)
        viewModel.setDefaultDownloadOptions(viewModel.state.value.advancedOptions.copy(skipArchived = true))
        viewModel.updateUrl("https://example.com/list")
        viewModel.savePendingLinks()
        runCurrent()
        viewModel.downloadPendingLinks(false) { _, _ -> emptyList() }
        advanceUntilIdle()
        assertEquals(2, queue.direct.size)
        assertTrue(queue.directOptions.all { !it.embedMetadata && it.preserveOriginalMetadata && it.skipArchived })
    }

    @Test fun `changing links keeps the persistent task workspace visible`() = runTest {
        val queue = FakeQueueGateway()
        val item = DownloadQueueItem(UUID.randomUUID().toString(), DownloadQueueState.FAILED, title = "Retained task")
        queue.setWorkspace(listOf(item))
        val viewModel = createViewModel(queue = queue)
        runCurrent()
        viewModel.updateUrl("https://example.com/another")
        assertEquals(listOf(item), viewModel.state.value.downloadQueue.items)
    }

    @Test
    fun `Settings changes reach the active downloader and persist only reusable extras`() = runTest {
        val settings = realSettings()
        val savedState = SavedStateHandle()
        val viewModel = createViewModel(settings = settings, savedState = savedState)
        runCurrent()
        viewModel.setAdvancedOptions(YtDlpDownloadOptions(videoFormatId = "137", videoContainer = VideoContainer.WEBM))
        settings.setEmbedMetadata(false)
        settings.setEmbedThumbnail(false)
        settings.setCropArtwork(false)
        settings.setPreserveOriginalMetadata(true)
        settings.setFilenamePattern("{id}.{ext}")
        viewModel.setDefaultDownloadOptions(viewModel.state.value.advancedOptions.copy(captionMode = CaptionMode.BOTH,
            captionFormat = CaptionFormat.SRT, captionLanguages = "en,hi", automaticCaptions = true, skipArchived = true))
        runCurrent()
        val state = viewModel.state.value
        assertFalse(state.embedMetadata)
        assertFalse(state.embedThumbnail)
        assertFalse(state.cropArtwork)
        assertTrue(state.preserveOriginalMetadata)
        assertEquals("{id}.{ext}", state.filenamePattern)
        assertEquals("137", state.advancedOptions.videoFormatId)
        assertEquals(VideoContainer.WEBM, state.advancedOptions.videoContainer)
        assertEquals(CaptionFormat.VTT, state.advancedOptions.captionFormat)
        val defaults = settings.downloadDefaults()
        assertNull(defaults.videoFormatId)
        assertNull(defaults.clipStartSeconds)
        assertEquals(VideoContainer.AUTO, defaults.videoContainer)
        assertEquals(CaptionMode.BOTH, defaults.captionMode)
        assertTrue(defaults.skipArchived)
        val newSession = createViewModel(settings = settings).state.value
        assertEquals("en,hi", newSession.advancedOptions.captionLanguages)
        assertTrue(newSession.advancedOptions.automaticCaptions)
        assertFalse(createViewModel(settings = settings, savedState = savedState).state.value.embedMetadata)
        viewModel.setAdvancedOptions(state.advancedOptions.copy(clipStartSeconds = 1.0, clipEndSeconds = 5.0))
        viewModel.setDefaultDownloadOptions(viewModel.state.value.advancedOptions.copy(skipArchived = false))
        runCurrent()
        assertEquals(CaptionMode.BOTH, viewModel.state.value.advancedOptions.captionMode)
        assertEquals(CaptionMode.BOTH, settings.downloadDefaults().captionMode)
        assertNull(settings.downloadDefaults().clipStartSeconds)
    }

    @Test
    fun `thumbnail toggle preserves clips streams and playlist selection and reaches the queued request`() = runTest {
        val settings = realSettings()
        val saved = SavedStateHandle()
        val queue = FakeQueueGateway()
        val playlist = "https://www.youtube.com/playlist?list=PL123"
        val entries = List(2) { ResolvedMedia("https://example.com/video/$it", MediaKind.VIDEO,
            backend = MediaBackend.YT_DLP, durationSeconds = 120, collectionUrl = playlist,
            collectionIndex = it + 1, collectionSize = 2, formats = listOf(
                dev.qtremors.acqua.domain.MediaFormatOption("137", "mp4", "avc1", "none"),
                dev.qtremors.acqua.domain.MediaFormatOption("140", "m4a", "none", "aac"))) }
        val model = createViewModel(settings = settings, savedState = saved, queue = queue)
        model.seedResolvedMedia(playlist, entries)
        model.applyPlaylistSelection(true, setOf(1))
        model.setAdvancedOptions(model.state.value.advancedOptions.copy(videoFormatId = "137", videoFormatIncludesAudio = false, audioFormatId = "140",
            videoContainer = VideoContainer.MKV, clipStartSeconds = 10.0, clipEndSeconds = 30.0))
        val before = model.state.value
        model.applyContentConfiguration(before.advancedOptions.copy(downloadThumbnail = true), before.selectedCollectionItems)
        runCurrent()
        assertEquals(before.copy(advancedOptions = before.advancedOptions.copy(downloadThumbnail = true)), model.state.value)
        assertTrue(settings.lastDownloadSelection(MediaKind.VIDEO)!!.options.downloadThumbnail)
        assertTrue(createViewModel(settings = settings, savedState = saved).state.value.advancedOptions.downloadThumbnail)
        model.downloadAll(false) { _, _ -> error("Fetched entries must be reused") }
        advanceUntilIdle()
        assertTrue(queue.options.single().downloadThumbnail)
        assertEquals(10.0, queue.options.single().clipStartSeconds!!, 0.0)
        assertEquals(30.0, queue.options.single().clipEndSeconds!!, 0.0)
        assertEquals(entries[1].url, queue.processed.single().url)
    }

    @Test
    fun `image audio and video choices remain separate after switching and reopening`() = runTest {
        val settings = realSettings()
        val video = ResolvedMedia("https://example.com/video", MediaKind.VIDEO, backend = MediaBackend.YT_DLP)
        val audio = ResolvedMedia("https://example.com/song.mp3", MediaKind.AUDIO)
        val image = ResolvedMedia("https://example.com/photo.png", MediaKind.IMAGE)
        val viewModel = createViewModel(settings = settings)
        runCurrent()
        viewModel.seedResolvedMedia(video.url, listOf(video))
        viewModel.setMaximumVideoHeight(720)
        viewModel.setAdvancedOptions(viewModel.state.value.advancedOptions.copy(videoContainer = VideoContainer.MKV, downloadThumbnail = true))
        viewModel.setEmbedMetadata(false)
        viewModel.setDefaultDownloadOptions(viewModel.state.value.advancedOptions.copy(captionMode = CaptionMode.SEPARATE))
        viewModel.setDownloadContentType(DownloadContentType.AUDIO)
        viewModel.setAudioFormat(AudioOutputFormat.MP3)
        viewModel.setAdvancedOptions(viewModel.state.value.advancedOptions.copy(audioBitrateKbps = 192))
        viewModel.setEmbedMetadata(true)
        viewModel.setEmbedThumbnail(false)
        viewModel.setDownloadContentType(DownloadContentType.VIDEO)
        runCurrent()
        assertEquals(720, viewModel.state.value.maximumVideoHeight)
        assertEquals(VideoContainer.MKV, viewModel.state.value.advancedOptions.videoContainer)
        assertTrue(viewModel.state.value.advancedOptions.downloadThumbnail)
        assertFalse(viewModel.state.value.embedMetadata)
        assertTrue(viewModel.state.value.embedThumbnail)
        viewModel.seedResolvedMedia(image.url, listOf(image))
        viewModel.setPreserveOriginalMetadata(true)
        viewModel.setDefaultDownloadOptions(viewModel.state.value.advancedOptions.copy(skipArchived = true))
        runCurrent()
        val reopened = createViewModel(settings = settings)
        assertEquals(VideoContainer.MKV, reopened.state.value.advancedOptions.videoContainer)
        assertFalse(reopened.state.value.preserveOriginalMetadata)
        reopened.seedResolvedMedia(audio.url, listOf(audio))
        assertEquals(AudioOutputFormat.MP3, reopened.state.value.audioFormat)
        assertEquals(192, reopened.state.value.advancedOptions.audioBitrateKbps)
        assertTrue(reopened.state.value.embedMetadata)
        assertFalse(reopened.state.value.embedThumbnail)
        assertFalse(reopened.state.value.advancedOptions.downloadThumbnail)
        assertFalse(reopened.state.value.preserveOriginalMetadata)
        reopened.seedResolvedMedia(image.url, listOf(image))
        assertTrue(reopened.state.value.preserveOriginalMetadata)
        assertTrue(reopened.state.value.advancedOptions.skipArchived)
        reopened.seedResolvedMedia(video.url, listOf(video))
        reopened.setAdvancedOptions(reopened.state.value.advancedOptions.copy(videoFormatId = "137", audioFormatId = "140",
            clipStartSeconds = 1.0, clipEndSeconds = 5.0))
        reopened.updateUrl("https://example.com/next")
        assertNull(reopened.state.value.advancedOptions.videoFormatId)
        assertNull(reopened.state.value.advancedOptions.audioFormatId)
        assertNull(reopened.state.value.advancedOptions.clipStartSeconds)
        assertEquals(CaptionMode.SEPARATE, reopened.state.value.advancedOptions.captionMode)
        assertEquals(VideoContainer.MKV, reopened.state.value.advancedOptions.videoContainer)
        settings.setMaximumVideoHeight(1080)
        settings.setAudioFormat(AudioOutputFormat.M4A)
        runCurrent()
        assertEquals(1080, settings.lastDownloadSelection(MediaKind.VIDEO)?.options?.maximumVideoHeight)
        assertEquals(AudioOutputFormat.M4A, settings.lastDownloadSelection(MediaKind.AUDIO)?.options?.audioFormat)
    }

    @Test
    fun `mixed direct batches use each media type remembered choices`() = runTest {
        val settings = realSettings()
        settings.rememberDownloadSelection(MediaKind.IMAGE, dev.qtremors.acqua.data.settings.LastDownloadSelection(
            DownloadEngine.ACQUA, YtDlpDownloadOptions(embedMetadata = false, preserveOriginalMetadata = true, skipArchived = true)))
        settings.rememberDownloadSelection(MediaKind.AUDIO, dev.qtremors.acqua.data.settings.LastDownloadSelection(
            DownloadEngine.ACQUA, YtDlpDownloadOptions(audioFormat = AudioOutputFormat.MP3, audioBitrateKbps = 192)))
        settings.rememberDownloadSelection(MediaKind.VIDEO, dev.qtremors.acqua.data.settings.LastDownloadSelection(
            DownloadEngine.ACQUA, YtDlpDownloadOptions(maximumVideoHeight = 720, videoContainer = VideoContainer.MKV)))
        val queue = FakeQueueGateway()
        val resolution = object : MediaResolutionGateway {
            override suspend fun resolve(input: String, browserSessionsEnabled: Boolean,
                forceBrowserResolution: Boolean, engine: DownloadEngine, allowBrowserFallback: Boolean,
                browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>) = listOf(
                    ResolvedMedia("https://example.com/photo.png", MediaKind.IMAGE),
                    ResolvedMedia("https://example.com/song.mp3", MediaKind.AUDIO),
                    ResolvedMedia("https://example.com/movie.mp4", MediaKind.VIDEO))
        }
        val viewModel = createViewModel(settings = settings, queue = queue, resolution = resolution)
        viewModel.updateUrl("https://example.com/mixed")
        viewModel.downloadAll(false) { _, _ -> emptyList() }
        advanceUntilIdle()
        assertEquals(3, queue.directOptions.size)
        assertFalse(queue.directOptions[0].embedMetadata)
        assertTrue(queue.directOptions[0].preserveOriginalMetadata)
        assertTrue(queue.directOptions[0].skipArchived)
        assertEquals(AudioOutputFormat.MP3, queue.directOptions[1].audioFormat)
        assertEquals(192, queue.directOptions[1].audioBitrateKbps)
        assertEquals(DownloadContentType.AUDIO, queue.directOptions[1].contentType)
        assertEquals(720, queue.directOptions[2].maximumVideoHeight)
        assertEquals(VideoContainer.MKV, queue.directOptions[2].videoContainer)
    }

    @Test fun `grouped configuration applies together preserves Settings and reaches fetched downloads`() = runTest {
        val settings = realSettings()
        val saved = SavedStateHandle()
        val queue = FakeQueueGateway()
        val viewModel = createViewModel(settings = settings, savedState = saved, queue = queue)
        runCurrent()
        val video = ResolvedMedia("https://example.com/video", MediaKind.VIDEO, backend = MediaBackend.YT_DLP)
        viewModel.seedResolvedMedia(video.url, listOf(video))
        viewModel.setDownloadContentType(DownloadContentType.AUDIO)
        viewModel.applyDownloadConfiguration(viewModel.state.value.configurationOptions().copy(audioFormat = AudioOutputFormat.MP3, audioBitrateKbps = 192))
        viewModel.setDownloadContentType(DownloadContentType.VIDEO)
        viewModel.setEmbedMetadata(false)
        viewModel.setDefaultDownloadOptions(viewModel.state.value.advancedOptions.copy(captionMode = CaptionMode.BOTH, captionLanguages = "en,hi"))
        val draft = viewModel.state.value.configurationOptions().copy(maximumVideoHeight = 720, videoContainer = VideoContainer.WEBM,
            captionMode = CaptionMode.NONE, captionLanguages = "de")
        viewModel.applyDownloadConfiguration(draft)
        runCurrent()
        assertEquals(listOf(video), viewModel.state.value.media)
        assertEquals(720, viewModel.state.value.maximumVideoHeight)
        assertEquals(VideoContainer.WEBM, viewModel.state.value.advancedOptions.videoContainer)
        assertEquals(CaptionMode.BOTH, viewModel.state.value.advancedOptions.captionMode)
        assertEquals(CaptionFormat.VTT, viewModel.state.value.advancedOptions.captionFormat)
        assertEquals("en,hi", viewModel.state.value.advancedOptions.captionLanguages)
        assertFalse(viewModel.state.value.embedMetadata)
        assertEquals(AudioOutputFormat.MP3, settings.lastDownloadSelection(MediaKind.AUDIO)?.options?.audioFormat)
        assertEquals(192, settings.lastDownloadSelection(MediaKind.AUDIO)?.options?.audioBitrateKbps)
        val restored = createViewModel(settings = settings, savedState = saved).state.value
        assertEquals(720, restored.maximumVideoHeight)
        assertEquals(VideoContainer.WEBM, restored.advancedOptions.videoContainer)
        viewModel.downloadAll(false) { _, _ -> error("Fetched media must be reused") }
        advanceUntilIdle()
        assertEquals(720, queue.options.single().maximumVideoHeight)
        assertEquals(VideoContainer.WEBM, queue.options.single().videoContainer)
        assertEquals(CaptionFormat.VTT, queue.options.single().captionFormat)
        assertFalse(queue.options.single().embedMetadata)
    }

    @Test fun `playlist scope changes resolve the intended URL and survive recreation`() = runTest {
        val calls = mutableListOf<String>()
        val resolution = object : MediaResolutionGateway {
            override suspend fun resolve(input: String, browserSessionsEnabled: Boolean, forceBrowserResolution: Boolean,
                engine: DownloadEngine, allowBrowserFallback: Boolean,
                browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>): List<ResolvedMedia> {
                calls += input
                return listOf(ResolvedMedia(input, MediaKind.VIDEO, backend = MediaBackend.YT_DLP))
            }
        }
        val saved = SavedStateHandle()
        val model = createViewModel(resolution = resolution, savedState = saved)
        val playlistUrl = "https://www.youtube.com/watch?v=abcdefghijk&list=PL123&index=2&t=60"
        model.updateUrl(playlistUrl)
        model.applyPlaylistSelection(false, null)
        model.resolve(false, 1) { _, _ -> emptyList() }
        advanceUntilIdle()
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk&t=60", calls.single())
        assertFalse(createViewModel(savedState = saved).state.value.downloadPlaylist)
        model.applyPlaylistSelection(true, null)
        assertNull(model.state.value.media)
        model.resolve(false, 1) { _, _ -> emptyList() }
        advanceUntilIdle()
        assertEquals(playlistUrl, calls.last())
    }

    @Test fun `entry inspection keeps collection identity and clips enqueue only the selected entry`() = runTest {
        val playlistUrl = "https://www.youtube.com/playlist?list=PL123"
        val entries = List(3) { ResolvedMedia("https://example.com/video/$it", MediaKind.VIDEO,
            backend = MediaBackend.YT_DLP, collectionUrl = playlistUrl, collectionTitle = "Walks",
            collectionIndex = it + 1, collectionSize = 3) }
        val inspected = entries[1].copy(durationSeconds = 300,
            chapters = listOf(dev.qtremors.acqua.domain.MediaChapter("Trail", 0.0, 300.0)))
        val resolution = object : MediaResolutionGateway {
            override suspend fun resolve(input: String, browserSessionsEnabled: Boolean, forceBrowserResolution: Boolean,
                engine: DownloadEngine, allowBrowserFallback: Boolean,
                browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>) = listOf(inspected.copy(collectionUrl = null))
        }
        val queue = FakeQueueGateway()
        val model = createViewModel(resolution = resolution, queue = queue)
        model.seedResolvedMedia(playlistUrl, entries)
        model.inspectCollectionEntry(1, false) { _, _ -> emptyList() }
        advanceUntilIdle()
        assertEquals(playlistUrl, model.state.value.media!![1].collectionUrl)
        assertEquals(2, model.state.value.media!![1].collectionIndex)
        assertEquals(setOf(1), model.state.value.inspectedCollectionItems)
        model.setAdvancedOptions(model.state.value.advancedOptions.copy(skipArchived = false, videoContainer = VideoContainer.MKV))
        model.applyContentConfiguration(model.state.value.advancedOptions.copy(clipStartSeconds = 60.0, clipEndSeconds = 120.0), setOf(1))
        model.downloadAll(false) { _, _ -> emptyList() }
        advanceUntilIdle()
        assertEquals(listOf(inspected), queue.processed)
        assertEquals(60.0, queue.options.single().clipStartSeconds!!, 0.0)
        assertEquals(VideoContainer.MKV, queue.options.single().videoContainer)
        assertFalse(queue.options.single().skipArchived)
        model.selectAllCollectionItems(true)
        assertNull(model.state.value.advancedOptions.clipStartSeconds)
        assertFalse(model.state.value.advancedOptions.splitChapters)
    }

    @Test fun `history defaults suppress batch links and every accepted backend request`() = runTest {
        for (recordHistory in listOf(false, true)) for (backend in MediaBackend.entries) {
            val entries = mutableListOf<dev.qtremors.acqua.data.history.HistoryEntry>()
            val settings = realSettings().apply { setDownloadDefaults(YtDlpDownloadOptions(recordHistory = recordHistory)) }
            val queue = FakeQueueGateway()
            val resolution = object : MediaResolutionGateway {
                override suspend fun resolve(input: String, browserSessionsEnabled: Boolean,
                    forceBrowserResolution: Boolean, engine: DownloadEngine, allowBrowserFallback: Boolean,
                    browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>) = listOf(
                    ResolvedMedia(input, if (backend == MediaBackend.DIRECT) MediaKind.IMAGE else MediaKind.AUDIO,
                        backend = backend, title = "Original title", artist = "Aster", sourceIdentity = "fixture:1"))
            }
            val model = createViewModel(settings = settings, queue = queue, resolution = resolution,
                history = HistoryWriter { entries += it; 1L })
            model.updateUrl("https://one.example/media https://two.example/media")
            runCurrent()
            model.downloadAll(false) { _, _ -> emptyList() }
            advanceUntilIdle()
            val accepted = queue.options + queue.directOptions
            assertEquals(2, accepted.size)
            assertTrue(accepted.all { it.recordHistory == recordHistory })
            assertEquals(if (recordHistory) 2 else 0, entries.map { it.url }.distinct().size)
            if (recordHistory) assertTrue(entries.any { it.title == "Original title" && it.author == "Aster" })
            settings.setDownloadDefaults(YtDlpDownloadOptions(recordHistory = !recordHistory))
            assertTrue(accepted.all { it.recordHistory == recordHistory })
        }
    }

    @Test fun `per task history choice survives resolution media changes restoration and failures`() = runTest {
        val entries = mutableListOf<dev.qtremors.acqua.data.history.HistoryEntry>()
        val settings = realSettings()
        val saved = SavedStateHandle()
        val queue = FakeQueueGateway()
        var failing = false
        val resolution = object : MediaResolutionGateway {
            override suspend fun resolve(input: String, browserSessionsEnabled: Boolean,
                forceBrowserResolution: Boolean, engine: DownloadEngine, allowBrowserFallback: Boolean,
                browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>): List<ResolvedMedia> {
                if (failing) throw java.io.IOException("offline")
                return listOf(ResolvedMedia(input, MediaKind.IMAGE))
            }
        }
        val model = createViewModel(settings = settings, queue = queue, resolution = resolution, savedState = saved,
            history = HistoryWriter { entries += it; 1L })
        model.updateUrl("https://example.com/image")
        model.applyDownloadConfiguration(model.state.value.advancedOptions.copy(recordHistory = false))
        model.downloadAll(true) { _, _ -> emptyList() }
        advanceUntilIdle()
        assertTrue(entries.isEmpty())
        assertFalse(queue.directOptions.single().recordHistory)
        assertFalse(createViewModel(settings = settings, savedState = saved).state.value.advancedOptions.recordHistory)
        settings.setDownloadDefaults(settings.downloadDefaults().copy(verboseDiagnostics = true))
        advanceUntilIdle()
        assertFalse(model.state.value.advancedOptions.recordHistory)
        assertTrue(model.state.value.advancedOptions.verboseDiagnostics)

        failing = true
        model.updateUrl("https://example.com/fails")
        model.applyDownloadConfiguration(model.state.value.advancedOptions.copy(recordHistory = false))
        model.downloadAll(false) { _, _ -> emptyList() }
        advanceUntilIdle()
        assertTrue(entries.isEmpty())
        assertEquals(MediaResolutionFailure.NETWORK, model.state.value.resolutionFailure)
    }

    @Test fun `output defaults reach the active downloader and settings summaries ignore task overrides`() = runTest {
        val settings = realSettings()
        val model = createViewModel(settings = settings)
        runCurrent()
        model.setAdvancedOptions(model.state.value.advancedOptions.copy(videoFormatId = "137", clipStartSeconds = 1.0, clipEndSeconds = 3.0))
        settings.setDownloadDefaults(settings.downloadDefaults().copy(videoContainer = VideoContainer.WEBM, audioBitrateKbps = 192,
            captionMode = CaptionMode.BOTH, captionFormat = CaptionFormat.SRT))
        runCurrent()
        val options = model.state.value.advancedOptions
        assertEquals(VideoContainer.WEBM, options.videoContainer)
        assertEquals(192, options.audioBitrateKbps)
        assertEquals(CaptionFormat.VTT, options.captionFormat)
        assertEquals("137", options.videoFormatId)
        assertEquals(1.0, options.clipStartSeconds)
        model.applyDownloadConfiguration(model.state.value.configurationOptions().copy(recordHistory = false))
        assertFalse(model.state.value.advancedOptions.recordHistory)
        assertTrue(model.downloadDefaults.value.recordHistory)
        val restored = createViewModel(settings = settings).state.value.advancedOptions
        assertEquals(VideoContainer.WEBM, restored.videoContainer)
        assertEquals(192, restored.audioBitrateKbps)
    }

    private fun realSettings(): AppSettingsRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(AppSettingsRepository.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        return AppSettingsRepository(context)
    }

    @Test fun `saved session changes clear old previews and remain selected through audio inference and queuing`() = runTest {
        val id = UUID.randomUUID().toString()
        val fingerprint = "a".repeat(64)
        val queue = FakeQueueGateway()
        val gateway = object : MediaResolutionGateway {
            override suspend fun resolve(input: String, browserSessionsEnabled: Boolean, forceBrowserResolution: Boolean,
                engine: DownloadEngine, allowBrowserFallback: Boolean, browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>): List<ResolvedMedia> = error("Unexpected current browser")
            override suspend fun resolveConfigured(input: String, browserSessionsEnabled: Boolean, engine: DownloadEngine,
                sessionProfileId: String?, browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>): List<ResolvedMedia> {
                assertEquals(id, sessionProfileId)
                return listOf(ResolvedMedia(input, MediaKind.AUDIO, backend = MediaBackend.YT_DLP))
            }
        }
        val saved = SavedStateHandle()
        val model = createViewModel(queue = queue, resolution = gateway, savedState = saved)
        val previous = ResolvedMedia("https://example.com/private", MediaKind.VIDEO, backend = MediaBackend.YT_DLP)
        model.seedResolvedMedia(previous.url, listOf(previous))
        model.setAdvancedOptions(YtDlpDownloadOptions(videoFormatId = "137", clipRanges = listOf(dev.qtremors.acqua.downloader.ClipRange(0.0, 2.0))))
        model.applyDownloadConfiguration(model.state.value.configurationOptions().copy(cookieProfileId = id, cookieProfileFingerprint = fingerprint))
        assertNull(model.state.value.media)
        assertNull(model.state.value.advancedOptions.videoFormatId)
        assertTrue(model.state.value.advancedOptions.clipRanges.isEmpty())
        model.setDownloadContentType(DownloadContentType.AUDIO)
        assertEquals(id, model.state.value.advancedOptions.cookieProfileId)
        model.downloadAll(true) { _, _ -> error("Unexpected current browser") }
        advanceUntilIdle()
        assertEquals(id, queue.options.single().cookieProfileId)
        assertEquals(fingerprint, queue.options.single().cookieProfileFingerprint)
        assertEquals(DownloadContentType.AUDIO, queue.options.single().contentType)
        assertEquals(id, createViewModel(savedState = saved).state.value.advancedOptions.cookieProfileId)
        model.updateUrl("https://example.com/new")
        assertNull(model.state.value.advancedOptions.cookieProfileId)
        assertNull(model.state.value.advancedOptions.cookieProfileFingerprint)
    }

    @Test fun `templates fill active controls while manual edits and accepted snapshots keep their precedence`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val template = dev.qtremors.acqua.downloader.CommandTemplate(name = "Audio fixture",
            command = "-x --audio-format m4a --no-embed-metadata --write-subs --convert-subs lrc")
        dev.qtremors.acqua.downloader.CommandTemplateStore(context).put(template)
        val saved = SavedStateHandle()
        val settings = realSettings()
        val queue = FakeQueueGateway()
        val model = createViewModel(settings = settings, savedState = saved, queue = queue)
        val media = ResolvedMedia("https://example.com/template-video", MediaKind.VIDEO, backend = MediaBackend.YT_DLP)
        model.seedResolvedMedia(media.url, listOf(media))
        val choices = template.applyTo(model.state.value.configurationOptions())
        model.setDownloadContentType(choices.contentType)
        model.applyDownloadConfiguration(choices)
        assertFalse(model.state.value.embedMetadata)
        assertEquals(AudioOutputFormat.M4A, model.state.value.audioFormat)
        assertEquals(CaptionMode.SEPARATE, model.state.value.advancedOptions.captionMode)
        assertEquals(CaptionFormat.LRC, model.state.value.advancedOptions.captionFormat)
        model.setEmbedMetadata(true)
        model.downloadAll(false) { _, _ -> error("Inspected fixture must be reused") }
        advanceUntilIdle()
        val accepted = queue.options.single()
        assertTrue(accepted.embedMetadata)
        assertEquals(template.id, accepted.commandTemplateId)
        assertEquals(template.command, accepted.commandTemplateSnapshot)
        val restored = dev.qtremors.acqua.downloader.DownloadOptionsCodec.decode(
            dev.qtremors.acqua.downloader.DownloadOptionsCodec.encode(accepted), YtDlpDownloadOptions())
        assertEquals(accepted, restored)
        assertEquals(template.id, createViewModel(settings = settings, savedState = saved).state.value.advancedOptions.commandTemplateId)
        model.updateUrl("https://example.com/new-link")
        assertNull(model.state.value.advancedOptions.commandTemplateId)
        assertNull(model.state.value.advancedOptions.formatSelectorOverride)
    }

    @Test fun `network defaults and per-task overrides survive choices recreation and accepted requests on both engines`() = runTest {
        for (backend in MediaBackend.entries) {
            val settings = realSettings()
            val defaults = dev.qtremors.acqua.domain.TransferNetworkOptions(rateLimitKib = 128)
            settings.setDownloadDefaults(YtDlpDownloadOptions(network = defaults))
            val saved = SavedStateHandle()
            val queue = FakeQueueGateway()
            val resolution = object : MediaResolutionGateway {
                override suspend fun resolve(input: String, browserSessionsEnabled: Boolean, forceBrowserResolution: Boolean,
                    engine: DownloadEngine, allowBrowserFallback: Boolean, browserResolver: suspend (String, Boolean) -> List<ResolvedMedia>) =
                    listOf(ResolvedMedia(input, MediaKind.VIDEO, backend = backend))
            }
            val model = createViewModel(settings = settings, savedState = saved, queue = queue, resolution = resolution)
            assertEquals(defaults, model.state.value.advancedOptions.network)
            val accepted = dev.qtremors.acqua.domain.TransferNetworkOptions(256, "http://localhost:8080", true, 4)
            model.updateUrl("https://example.com/video")
            val categories = setOf(dev.qtremors.acqua.downloader.SegmentCategory.SPONSOR)
            val policy = dev.qtremors.acqua.downloader.DownloadFormatPolicy(dev.qtremors.acqua.downloader.FormatPolicyMode.CUSTOM, customSort = "+size,res")
            model.applyDownloadConfiguration(model.state.value.configurationOptions().copy(network = accepted, removeSegments = categories, formatPolicy = policy))
            model.setDownloadContentType(DownloadContentType.AUDIO)
            model.setDownloadContentType(DownloadContentType.VIDEO)
            assertEquals(accepted, model.state.value.advancedOptions.network)
            assertEquals(categories, model.state.value.advancedOptions.removeSegments)
            assertEquals(policy, model.state.value.advancedOptions.formatPolicy)
            assertEquals(defaults, settings.downloadDefaults().network)
            assertEquals(accepted, createViewModel(settings = settings, savedState = saved).state.value.advancedOptions.network)
            assertEquals(policy, createViewModel(settings = settings, savedState = saved).state.value.advancedOptions.formatPolicy)
            settings.setDownloadDefaults(settings.downloadDefaults().copy(verboseDiagnostics = true))
            advanceUntilIdle()
            assertEquals(accepted, model.state.value.advancedOptions.network)
            model.downloadAll(false) { _, _ -> emptyList() }
            advanceUntilIdle()
            val requests = queue.options + queue.directOptions
            assertEquals(accepted, requests.single().network)
            assertEquals(categories, requests.single().removeSegments)
            assertEquals(policy, requests.single().formatPolicy)
            val next = defaults.copy(rateLimitKib = 512)
            model.updateDownloadDefaults { it.copy(network = next) }
            advanceUntilIdle()
            assertEquals(next, model.state.value.advancedOptions.network)
            assertEquals(accepted, requests.single().network)
        }
    }

    private fun createViewModel(
        settings: SettingsStore = FakeSettingsStore(),
        savedState: SavedStateHandle = SavedStateHandle(),
        resolution: MediaResolutionGateway = EmptyResolution,
        cancellation: FakeCancellation = FakeCancellation(),
        queue: FakeQueueGateway = FakeQueueGateway(),
        history: HistoryWriter = HistoryWriter { 1L }
    ) = DownloaderViewModel(
        history = history,
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
        var failAt: Int? = null
        private var attempts = 0
        private val pending = MutableStateFlow<List<dev.qtremors.acqua.downloader.PendingLink>>(emptyList())
        override fun observePending() = pending
        override fun savePending(text: String) {
            dev.qtremors.acqua.domain.WebLink.extractAll(text).forEach { url ->
                if (pending.value.none { it.url == url }) pending.value += dev.qtremors.acqua.downloader.PendingLink(url, 0L)
            }
        }
        override fun updatePending(link: dev.qtremors.acqua.downloader.PendingLink) { pending.value = pending.value.map { if (it.url == link.url) link else it } }
        private val queue = MutableStateFlow(DownloadQueueSnapshot())
        fun setWorkspace(items: List<DownloadQueueItem>) { queue.value = DownloadQueueSnapshot(items) }
        val jobs = MutableStateFlow<List<DownloadQueueItem>>(emptyList())
        val renamed = mutableMapOf<UUID, String>()
        val processed = mutableListOf<ResolvedMedia>()
        val options = mutableListOf<YtDlpDownloadOptions>()
        val direct = mutableListOf<ResolvedMedia>()
        val directOptions = mutableListOf<YtDlpDownloadOptions>()
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
        override fun enqueueDirectConfigured(media: ResolvedMedia, index: Int, sourceUrl: String, itemCount: Int,
            options: YtDlpDownloadOptions): UUID {
            directOptions += options
            return enqueueDirect(media, index, sourceUrl, itemCount)
        }
        private fun enqueueJob(): UUID = UUID.randomUUID().also { id ->
            if (attempts++ == failAt) error("Scheduling rejected")
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
