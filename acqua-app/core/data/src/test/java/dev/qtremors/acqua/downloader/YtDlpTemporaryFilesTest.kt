package dev.qtremors.acqua.downloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class YtDlpTemporaryFilesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `process restart removes incomplete media and cookie files`() {
        val taskFile = File(context.cacheDir, "yt-dlp/interrupted/media.part").apply {
            parentFile!!.mkdirs()
            writeText("partial")
        }
        val cookieFile = File(context.cacheDir, "yt-dlp-cookies/cookies-interrupted.txt").apply {
            parentFile!!.mkdirs()
            writeText("secret")
        }
        assertTrue(taskFile.exists())
        assertTrue(cookieFile.exists())

        YtDlpTemporaryFiles.cleanupAfterProcessRestart(context)

        assertFalse(taskFile.exists())
        assertFalse(cookieFile.exists())
    }
}
