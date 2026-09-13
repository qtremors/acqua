package dev.qtremors.acqua.downloader

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserInitiatedDownloadSchedulerTest {
    @Test
    fun `job id is stable and isolated in the Acqua namespace`() {
        val id = UUID.fromString("9ad92435-3d2d-4f95-804a-d57c6f9feac0")

        val first = UserInitiatedDownloadScheduler.jobId(id)
        val second = UserInitiatedDownloadScheduler.jobId(id)

        assertEquals(first, second)
        assertEquals(0x50000000, first and 0xf0000000.toInt())
        assertTrue(first > 0)
    }

    @Test
    fun `different task ids normally map to different scheduler ids`() {
        val first = UserInitiatedDownloadScheduler.jobId(UUID(1L, 2L))
        val second = UserInitiatedDownloadScheduler.jobId(UUID(3L, 4L))

        assertNotEquals(first, second)
    }
}
