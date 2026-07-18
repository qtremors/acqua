package dev.qtremors.acqua.platform.storage

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import dev.qtremors.acqua.data.history.HistoryRepository
import dev.qtremors.acqua.data.network.MediaDownloader
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class MediaStorageTest {
    @Test
    fun savedMediaIsRecordedWithDetectedMetadata() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val history = HistoryRepository(context).apply { clear() }
        val settings = AppSettingsRepository(context).apply {
            setBaseFolder("AcquaTest")
            setCategorizeMedia(false)
            setFilenamePattern("storage_{index}")
        }
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setHeader("Content-Type", "image/png").setBody(
                    okio.Buffer().write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
                )
            )
            val storage = MediaStorage(context, history, settings, MediaDownloader())
            val media = ResolvedMedia(
                server.url("photo").toString(), MediaKind.IMAGE,
                mimeType = "image/png", fileExtension = "png"
            )
            val uri = storage.save(media, 0, "https://example.com/source")

            val entry = history.load().single()
            assertEquals("image/png", entry.mimeType)
            assertEquals("storage_1.png", entry.fileName)
            context.contentResolver.delete(uri, null, null)
            history.clear()
        }
    }
}
