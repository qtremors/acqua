package dev.qtremors.acqua.data.settings

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.downloader.FilenameFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSettingsRepositoryTest {
    @Test
    fun legacyDefaultFilenamePatternMigratesWithoutChangingCustomPatterns() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences(AppSettingsRepository.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        val repository = AppSettingsRepository(context)
        assertFalse(repository.screenProtectionEnabled())

        repository.setFilenamePattern(FilenameFormatter.LEGACY_DEFAULT_PATTERN)
        assertEquals(FilenameFormatter.DEFAULT_PATTERN, repository.downloadSettings().filenamePattern)

        repository.setFilenamePattern("custom_{title}")
        assertEquals("custom_{title}", repository.downloadSettings().filenamePattern)
        assertEquals(FilenameFormatter.DEFAULT_AUDIO_PATTERN, repository.downloadSettings().audioFilenamePattern)
    }

    @Test
    fun settingsPersistAndFolderIsSanitized() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences(AppSettingsRepository.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        val repository = AppSettingsRepository(context)
        repository.setBaseFolder(" Acqua/Unsafe:* ")
        repository.setCategorizeMedia(true)
        repository.setFilenamePattern("{date}_{index}")
        repository.setAudioFilenamePattern("{artist} - {title}")
        repository.setLastDownloadEngine(DownloadEngine.ACQUA)
        repository.setLastDownloadContentType(DownloadContentType.AUDIO)
        repository.setMaximumVideoHeight(1080)
        repository.setAudioFormat(AudioOutputFormat.MP3)
        repository.setEmbedMetadata(false)
        repository.setEmbedThumbnail(false)
        repository.markYtDlpUpdated(123456789L)
        repository.setScreenProtectionEnabled(true)

        val restoredRepository = AppSettingsRepository(context)
        val restored = restoredRepository.downloadSettings()
        val restoredMedia = restoredRepository.mediaProcessingSettings()
        assertEquals(" Acqua/Unsafe:* ", restored.baseFolder)
        assertTrue(restored.categorizeMedia)
        assertEquals("{date}_{index}", restored.filenamePattern)
        assertEquals("{artist} - {title}", restored.audioFilenamePattern)
        assertEquals("Acqua_Unsafe__", repository.sanitizedBaseFolder(restored.baseFolder))
        assertEquals(DownloadEngine.ACQUA, restoredRepository.lastDownloadEngine())
        assertEquals(DownloadContentType.AUDIO, restoredRepository.lastDownloadContentType())
        assertEquals(1080, restoredMedia.maximumVideoHeight)
        assertEquals(AudioOutputFormat.MP3, restoredMedia.audioFormat)
        assertFalse(restoredMedia.embedMetadata)
        assertFalse(restoredMedia.embedThumbnail)
        assertEquals(123456789L, restoredMedia.lastYtDlpUpdate)
        assertTrue(restoredRepository.screenProtectionEnabled())
    }
}
