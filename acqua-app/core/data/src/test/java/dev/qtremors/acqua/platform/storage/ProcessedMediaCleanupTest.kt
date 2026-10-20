package dev.qtremors.acqua.platform.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.data.history.HistoryRepository
import dev.qtremors.acqua.data.network.HttpMediaClient
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.downloader.DownloadContentType
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProcessedMediaCleanupTest {
    @Test
    fun `legacy save removes its new copy after publication fails and preserves existing files`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = AppSettingsRepository(context).apply {
            setBaseFolder("AcquaFailure_${UUID.randomUUID()}")
            setCategorizeMedia(false)
            setAudioFilenamePattern("Existing")
        }
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), settings.downloadSettings().baseFolder)
        directory.mkdirs()
        val existing = File(directory, "Existing.m4a").apply { writeText("previous download") }
        val source = Files.createTempFile("acqua-processed", ".m4a").toFile().apply { writeText("processed media") }
        val sdk = Build.VERSION.SDK_INT
        try {
            // Exercise only the legacy filesystem branch using the already-cached SDK.
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 28)
            val storage = MediaStorage(context, HistoryRepository(context), settings, HttpMediaClient())
            // This library test manifest has no FileProvider: publication fails after copying.
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { storage.saveProcessedFile(source, ResolvedMedia("https://example.com/audio", MediaKind.AUDIO),
                    DownloadContentType.AUDIO, "https://example.com/source") }
            }
            assertEquals(listOf(existing.name), directory.listFiles()!!.map { it.name })
            assertEquals("previous download", existing.readText())
        } finally {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk)
            source.delete()
            existing.delete()
            directory.delete()
        }
    }
}
