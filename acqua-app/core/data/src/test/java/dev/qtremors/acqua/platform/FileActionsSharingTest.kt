package dev.qtremors.acqua.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.data.history.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FileActionsSharingTest {
    @Test fun `multi file sharing grants every unique URI and preserves their mixed media types`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entries = listOf(entry("1", "audio/mp4"), entry("2", "video/mp4"), entry("1", "audio/mp4"))
        FileActions(context).shareMany(entries)
        val chooser = shadowOf(context as android.app.Application).nextStartedActivity
        @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND_MULTIPLE, send.action)
        assertEquals("*/*", send.type)
        @Suppress("DEPRECATION") val uris = send.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals(listOf("content://fixture/1", "content://fixture/2"), uris.map(Uri::toString))
        assertEquals(uris.size, send.clipData!!.itemCount)
        assertEquals(uris, (0 until send.clipData!!.itemCount).map { send.clipData!!.getItemAt(it).uri })
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    private fun entry(id: String, mime: String) = HistoryEntry(id, 1, "https://example.com/$id",
        "Fixture $id", "content://fixture/$id", mime.startsWith("video/"), mime, 10, true)
}
