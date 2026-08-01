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
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class MediaStorageTest {
    @Test
    fun savedMediaIsRecordedWithDetectedMetadata() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val history = HistoryRepository(context).apply { clear() }
        val filenamePrefix = "storage_${UUID.randomUUID()}"
        val settings = AppSettingsRepository(context).apply {
            setBaseFolder("AcquaTest")
            setCategorizeMedia(false)
            setFilenamePattern("${filenamePrefix}_{index}")
            setUseBrowserSessions(false)
        }
        val savedUris = mutableListOf<android.net.Uri>()
        MockWebServer().use { server ->
            try {
                server.enqueue(
                    MockResponse().setHeader("Content-Type", "image/png").setBody(
                        okio.Buffer().write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
                    )
                )
                val storage = MediaStorage(context, history, settings, MediaDownloader())
                val media = ResolvedMedia(
                    server.url("photo").toString(), MediaKind.IMAGE,
                    requestCookies = "sessionid=explicit",
                    explicitBrowserSessionAuthorized = true,
                    mimeType = "image/png",
                    fileExtension = "png"
                )
                savedUris += storage.save(media, 0, "https://example.com/source")

                val entry = history.load().single()
                assertEquals("sessionid=explicit", server.takeRequest().getHeader("Cookie"))
                assertEquals("image/png", entry.mimeType)
                assertEquals("${filenamePrefix}_1.png", entry.fileName)

                server.enqueue(
                    MockResponse().setHeader("Content-Type", "image/png").setBody(
                        okio.Buffer().write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
                    )
                )
                savedUris += storage.save(
                    media.copy(explicitBrowserSessionAuthorized = false),
                    1,
                    "https://example.com/source"
                )
                assertNull(server.takeRequest().getHeader("Cookie"))
            } finally {
                savedUris.forEach { context.contentResolver.delete(it, null, null) }
                history.clear()
            }
        }
    }
}
