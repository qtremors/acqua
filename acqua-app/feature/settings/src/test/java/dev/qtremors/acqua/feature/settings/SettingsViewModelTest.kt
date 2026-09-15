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
        override fun setAutoUpdateYtDlp(enabled: Boolean) { media = media.copy(autoUpdateYtDlp = enabled) }
        override fun lastDownloadEngine() = DownloadEngine.YT_DLP
        override fun setLastDownloadEngine(value: DownloadEngine) = Unit
        override fun lastDownloadContentType() = null
        override fun setLastDownloadContentType(value: dev.qtremors.acqua.downloader.DownloadContentType) = Unit
    }
}
