package dev.qtremors.acqua.feature.downloader

import org.junit.Assert.*
import org.junit.Test

class ClipTimeTest {
    @Test fun `time fields accept precise seconds minutes and hours and reject invalid boundaries`() {
        assertEquals(12.5, parseClipTime("12.5")!!, 0.0)
        assertEquals(75.25, parseClipTime("1:15.25")!!, 0.0)
        assertEquals(3675.0, parseClipTime("1:01:15")!!, 0.0)
        for (value in listOf("", "-1", "NaN", "Infinity", "1:60", "1:60:00", "1.5:00", "0:0:0:0"))
            assertNull(value, parseClipTime(value))
        assertEquals("1:15.25", formatClipTime(75.25))
        assertEquals("1:01:15", formatClipTime(3675.0))
    }
}
