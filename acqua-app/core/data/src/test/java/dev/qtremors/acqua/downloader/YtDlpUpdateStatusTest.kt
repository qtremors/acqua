package dev.qtremors.acqua.downloader

import com.yausername.youtubedl_android.YoutubeDL
import org.junit.Assert.assertEquals
import org.junit.Test

class YtDlpUpdateStatusTest {
    @Test
    fun `library update results map to user-facing states`() {
        assertEquals(YtDlpUpdateStatus.UPDATED, YoutubeDL.UpdateStatus.DONE.toAcquaUpdateStatus())
        assertEquals(
            YtDlpUpdateStatus.ALREADY_CURRENT,
            YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE.toAcquaUpdateStatus()
        )
        assertEquals(YtDlpUpdateStatus.ALREADY_CURRENT, null.toAcquaUpdateStatus())
    }
}
