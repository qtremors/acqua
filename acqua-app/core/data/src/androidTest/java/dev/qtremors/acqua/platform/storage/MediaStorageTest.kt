package dev.qtremors.acqua.platform.storage

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import dev.qtremors.acqua.data.history.HistoryRepository
import dev.qtremors.acqua.data.network.HttpMediaClient
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class MediaStorageTest {
    @Test
    fun savedMediaIsRecordedWithDetectedMetadata() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val history = HistoryRepository(context).apply { clear() }
        val filenamePrefix = "storage_${UUID.randomUUID()}"
        val settings = AppSettingsRepository(context).apply {
            setBaseFolder("AcquaTest")
            setCategorizeMedia(false)
            setFilenamePattern("${filenamePrefix}_{index}")
            setUseBrowserSessions(true)
        }
        val savedUris = mutableListOf<android.net.Uri>()
        val png = ByteArrayOutputStream().use { stream ->
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
        MockWebServer().use { server ->
            try {
                server.enqueue(
                    MockResponse().setHeader("Content-Type", "image/png").setBody(
                        okio.Buffer().write(png)
                    )
                )
                val storage = MediaStorage(context, history, settings, HttpMediaClient())
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
                assertEquals("${filenamePrefix}.png", entry.fileName)

                settings.setUseBrowserSessions(false)

                server.enqueue(
                    MockResponse().setHeader("Content-Type", "image/png").setBody(
                        okio.Buffer().write(png)
                    )
                )
                savedUris += storage.save(
                    media,
                    1,
                    "https://example.com/source",
                    finalDownloadName = { "Renamed during transfer" }
                )
                assertNull(server.takeRequest().getHeader("Cookie"))
                assertEquals("Renamed during transfer.png", history.load().first().fileName)
            } finally {
                savedUris.forEach { context.contentResolver.delete(it, null, null) }
                history.clear()
            }
        }
    }
}
