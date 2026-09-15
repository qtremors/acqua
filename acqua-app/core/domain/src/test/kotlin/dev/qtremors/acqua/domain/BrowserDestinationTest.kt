package dev.qtremors.acqua.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserDestinationTest {
    @Test
    fun `opens host names as websites`() {
        assertEquals("https://example.com/path", BrowserDestination.fromInput("example.com/path"))
    }

    @Test
    fun `searches words and phrases`() {
        assertEquals("https://duckduckgo.com/?q=kotlin", BrowserDestination.fromInput("kotlin"))
        assertEquals("https://duckduckgo.com/?q=compose+pager", BrowserDestination.fromInput("compose pager"))
    }

    @Test
    fun `extracts an explicitly shared URL and ignores blank input`() {
        assertEquals(
            "https://example.com/video",
            BrowserDestination.fromInput("Open https://example.com/video please")
        )
        assertNull(BrowserDestination.fromInput("  "))
    }
}
