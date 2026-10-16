package dev.qtremors.acqua.feature.downloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.SavedStateHandle
import dev.qtremors.acqua.MainDispatcherRule
import dev.qtremors.acqua.data.history.HistoryWriter
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.domain.*
import dev.qtremors.acqua.downloader.*
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActiveSelectionRenameTest {
    @get:Rule val main = MainDispatcherRule()
    @Test fun renamingSelectedCarouselUsesThatItemsTask() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ids = mutableListOf<UUID>()
        var renamed: UUID? = null
        val downloads = object : DownloadQueueGateway {
            override fun enqueue(media: ResolvedMedia, outputMedia: ResolvedMedia, options: YtDlpDownloadOptions, sourceUrl: String) = UUID.randomUUID().also { ids += it }
            override fun enqueueDirect(media: ResolvedMedia, index: Int, sourceUrl: String, itemCount: Int) = UUID.randomUUID().also { ids += it }
            override fun observe(ids: List<UUID>) = MutableStateFlow(ids.map { DownloadQueueItem(it.toString(), DownloadQueueState.RUNNING) })
            override fun observeQueue() = flowOf(DownloadQueueSnapshot())
            override fun cancel(id: UUID) {}
            override fun rename(id: UUID, name: String): Boolean { renamed = id; return true }
        }
        val resolution = MediaResolutionService(MediaResolver { emptyList() }, inspectMedia = { it })
        val viewModel = DownloaderViewModel(HistoryWriter { 0L }, resolution, AppSettingsRepository(context),
            object : YtDlpCancellation { override fun cancelAll() {} }, downloads, SavedStateHandle(), main.dispatcher)
        viewModel.seedResolvedMedia("https://example.com/post", (0..2).map { ResolvedMedia("https://example.com/photo$it.png", MediaKind.IMAGE) })
        viewModel.selectPreviewItems(setOf(1, 2))
        viewModel.downloadAll(false) { _, _ -> emptyList() }
        runCurrent()
        assertEquals(2, ids.size)
        assertTrue(viewModel.state.value.isSaving)
        viewModel.renameMedia(1, "Renamed first selected photo")
        assertEquals("Carousel index 1 is the first accepted task", ids.first(), renamed)
        viewModel.cancelActiveDownload()
    }
}
