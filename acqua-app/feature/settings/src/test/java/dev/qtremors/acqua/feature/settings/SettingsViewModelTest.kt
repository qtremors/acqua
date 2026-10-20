package dev.qtremors.acqua.feature.settings

import dev.qtremors.acqua.MainDispatcherRule
import dev.qtremors.acqua.data.settings.DownloadSettings
import dev.qtremors.acqua.data.settings.MediaProcessingSettings
import dev.qtremors.acqua.data.settings.SettingsStore
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.FilenameFormatter
import dev.qtremors.acqua.downloader.YtDlpFailure
import dev.qtremors.acqua.downloader.YtDlpMaintenanceGateway
import dev.qtremors.acqua.downloader.YtDlpMaintenanceResult
import dev.qtremors.acqua.downloader.YtDlpUpdateStatus
import dev.qtremors.acqua.downloader.YtDlpUpdateChannel
import dev.qtremors.acqua.downloader.YtDlpUpdateInterval
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `saved low and inspected quality values remain selectable after reload and recreation`() = runTest {
        val store = FakeSettingsStore()
        val viewModel = SettingsViewModel(store, FakeMaintenance(), { _, _ -> })
        for (height in listOf(360, 540, 0)) {
            store.setMaximumVideoHeight(height)
            viewModel.reload()
            assertEquals(height, viewModel.state.value.maximumVideoHeight)
            assertTrue(height in viewModel.state.value.videoQualities)
            val restored = SettingsViewModel(store, FakeMaintenance(), { _, _ -> }).state.value
            assertEquals(height, restored.maximumVideoHeight)
            assertTrue(height in restored.videoQualities)
            assertEquals(0, restored.videoQualities.first())
            assertEquals(1, restored.videoQualities.count { it == height })
        }
        advanceUntilIdle()
    }

    @Test
    fun `preference intents update the store and authoritative state`() = runTest {
        val store = FakeSettingsStore()
        val viewModel = SettingsViewModel(store, FakeMaintenance(), { _, _ -> })
        advanceUntilIdle()

        viewModel.setBaseFolder("Media")
        viewModel.setMaximumVideoHeight(1080)
        viewModel.setAudioFormat(AudioOutputFormat.MP3)
        viewModel.setScreenProtectionEnabled(true)
        advanceUntilIdle()

        assertEquals("Media", store.download.baseFolder)
        assertEquals(1080, store.media.maximumVideoHeight)
        assertEquals(AudioOutputFormat.MP3, store.media.audioFormat)
        assertTrue(viewModel.state.value.screenProtectionEnabled)
    }

    @Test
    fun `initial update check runs once and exposes completion`() = runTest {
        val maintenance = FakeMaintenance(
            result = Result.success(
                YtDlpMaintenanceResult("2026.09.15", YtDlpUpdateStatus.UPDATED, 123L)
            )
        )
        val viewModel = SettingsViewModel(FakeSettingsStore(), maintenance, { _, _ -> })

        viewModel.loadYtDlpStatus()
        viewModel.loadYtDlpStatus()
        advanceUntilIdle()

        assertEquals(listOf(false), maintenance.forces)
        assertEquals("2026.09.15", viewModel.state.value.ytDlpVersion)
        assertEquals(YtDlpUpdateStatus.UPDATED, viewModel.state.value.ytDlpUpdateStatus)
        assertFalse(viewModel.state.value.isUpdatingYtDlp)
    }

    @Test
    fun `update failure is typed and a retry can recover`() = runTest {
        val maintenance = FakeMaintenance(Result.failure(IOException("private endpoint")))
        val failures = mutableListOf<YtDlpFailure>()
        val viewModel = SettingsViewModel(FakeSettingsStore(), maintenance) { failure, _ ->
            failures += failure
        }

        viewModel.updateYtDlp()
        advanceUntilIdle()
        assertEquals(YtDlpFailure.NETWORK, viewModel.state.value.ytDlpUpdateError)
        assertEquals(listOf(YtDlpFailure.NETWORK), failures)

        maintenance.result = Result.success(
            YtDlpMaintenanceResult("current", YtDlpUpdateStatus.ALREADY_CURRENT, 456L)
        )
        viewModel.updateYtDlp()
        advanceUntilIdle()

        assertEquals(null, viewModel.state.value.ytDlpUpdateError)
        assertEquals(YtDlpUpdateStatus.ALREADY_CURRENT, viewModel.state.value.ytDlpUpdateStatus)
    }

    @Test fun `update channel and interval choices survive reload and recreation`() = runTest {
        val store = FakeSettingsStore()
        val model = SettingsViewModel(store, FakeMaintenance(), { _, _ -> })
        model.setYtDlpUpdateChannel(YtDlpUpdateChannel.NIGHTLY)
        model.setYtDlpUpdateInterval(YtDlpUpdateInterval.DAILY)
        model.reload()
        assertEquals(YtDlpUpdateChannel.NIGHTLY, model.state.value.ytDlpUpdateChannel)
        val restored = SettingsViewModel(store, FakeMaintenance(), { _, _ -> }).state.value
        assertEquals(YtDlpUpdateChannel.NIGHTLY, restored.ytDlpUpdateChannel)
        assertEquals(YtDlpUpdateInterval.DAILY, restored.ytDlpUpdateInterval)
        advanceUntilIdle()
    }

    @Test fun `extended diagnostics preference survives reload without changing history defaults`() = runTest {
        val store = FakeSettingsStore().apply { defaults = dev.qtremors.acqua.downloader.YtDlpDownloadOptions(recordHistory = false) }
        val model = SettingsViewModel(store, FakeMaintenance(), { _, _ -> })
        model.setVerboseDiagnostics(true)
        model.reload()
        assertTrue(model.state.value.verboseDiagnostics)
        assertFalse(store.defaults.recordHistory)
        assertTrue(SettingsViewModel(store, FakeMaintenance(), { _, _ -> }).state.value.verboseDiagnostics)
        advanceUntilIdle()
    }

    @Test fun `output defaults survive recreation and update remembered choices without losing other options`() = runTest {
        val store = FakeSettingsStore()
        val remembered = dev.qtremors.acqua.data.settings.LastDownloadSelection(DownloadEngine.YT_DLP,
            dev.qtremors.acqua.downloader.YtDlpDownloadOptions(videoContainer = dev.qtremors.acqua.downloader.VideoContainer.MP4,
                audioBitrateKbps = 128, skipArchived = true))
        store.selections[dev.qtremors.acqua.domain.MediaKind.VIDEO] = remembered
        store.selections[dev.qtremors.acqua.domain.MediaKind.AUDIO] = remembered
        val model = SettingsViewModel(store, FakeMaintenance(), { _, _ -> })
        model.setVideoContainer(dev.qtremors.acqua.downloader.VideoContainer.MKV)
        model.setAudioBitrate(256)
        model.setCropArtwork(false)
        model.setPreserveOriginalMetadata(true)
        val restored = SettingsViewModel(store, FakeMaintenance(), { _, _ -> }).state.value
        assertEquals(dev.qtremors.acqua.downloader.VideoContainer.MKV, restored.videoContainer)
        assertEquals(256, restored.audioBitrateKbps)
        assertFalse(restored.cropArtwork)
        assertTrue(restored.preserveOriginalMetadata)
        assertEquals(dev.qtremors.acqua.downloader.VideoContainer.MKV, store.selections.getValue(dev.qtremors.acqua.domain.MediaKind.VIDEO).options.videoContainer)
        assertEquals(256, store.selections.getValue(dev.qtremors.acqua.domain.MediaKind.AUDIO).options.audioBitrateKbps)
        assertTrue(store.selections.values.all { it.options.skipArchived })
        advanceUntilIdle()
    }

    private class FakeMaintenance(
        var result: Result<YtDlpMaintenanceResult> = Result.success(
            YtDlpMaintenanceResult(null, null, 0L)
        )
    ) : YtDlpMaintenanceGateway {
        val forces = mutableListOf<Boolean>()
        override suspend fun update(force: Boolean): YtDlpMaintenanceResult {
            forces += force
            return result.getOrThrow()
        }
    }

    private class FakeSettingsStore : SettingsStore {
        val selections = mutableMapOf<dev.qtremors.acqua.domain.MediaKind, dev.qtremors.acqua.data.settings.LastDownloadSelection>()
        override fun lastDownloadSelection(kind: dev.qtremors.acqua.domain.MediaKind) = selections[kind]
        override fun rememberDownloadSelection(kind: dev.qtremors.acqua.domain.MediaKind, selection: dev.qtremors.acqua.data.settings.LastDownloadSelection) { selections[kind] = selection }
        var defaults = dev.qtremors.acqua.downloader.YtDlpDownloadOptions()
        override fun downloadDefaults() = defaults
        override fun setDownloadDefaults(options: dev.qtremors.acqua.downloader.YtDlpDownloadOptions) { defaults = options }
        val protection = MutableStateFlow(false)
        var download = DownloadSettings(
            baseFolder = "Acqua",
            categorizeMedia = false,
            filenamePattern = FilenameFormatter.DEFAULT_PATTERN
        )
        var media = MediaProcessingSettings(
            maximumVideoHeight = 0,
            audioFormat = AudioOutputFormat.ORIGINAL,
            embedMetadata = true,
            embedThumbnail = true,
            autoUpdateYtDlp = true,
            lastYtDlpUpdate = 0L
        )

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
        override fun setYtDlpUpdateChannel(value: YtDlpUpdateChannel) { media = media.copy(ytDlpUpdateChannel = value) }
        override fun setYtDlpUpdateInterval(value: YtDlpUpdateInterval) { media = media.copy(ytDlpUpdateInterval = value) }
        override fun lastDownloadEngine() = DownloadEngine.YT_DLP
        override fun setLastDownloadEngine(value: DownloadEngine) = Unit
        override fun lastDownloadContentType() = null
        override fun setLastDownloadContentType(value: dev.qtremors.acqua.downloader.DownloadContentType) = Unit
    }
}
