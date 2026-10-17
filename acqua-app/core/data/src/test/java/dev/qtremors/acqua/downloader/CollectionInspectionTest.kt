package dev.qtremors.acqua.downloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CollectionInspectionTest {
    private val engine = YtDlpEngine(ApplicationProvider.getApplicationContext<Context>())
    private val url = "https://example.com/list"

    @Test fun `nested leaves retain local positions titles and unavailable siblings`() {
        val items = engine.parseInspection("""{"title":"Root","entries":[
            {"title":"First","id":"1","_type":"url","url":"https://example.com/one"},
            {"title":"Nested","webpage_url":"https://example.com/nested","entries":[
                {"id":"2","title":"Second"},null,{"id":"3","title":"Private","availability":"private"}]},
            {"title":"Last","id":"4","_type":"url","url":"https://example.com/four"}]}""", url, false)
        assertEquals(5, items.size)
        assertEquals(listOf("First", "Second", null, "Private", "Last"), items.map { it.title })
        assertEquals("Root / Nested", items[1].collectionTitle)
        assertEquals("https://example.com/nested", items[1].collectionUrl)
        assertEquals(DownloadSourceSelection("https://example.com/nested", 1), DownloadSourceSelection.from(items[1]))
        assertEquals(3, items[3].collectionIndex)
        assertNotNull(items[2].unavailableReason)
        assertEquals("private", items[3].unavailableReason)
        assertNull(items.last().unavailableReason)
    }

    @Test fun `linked collections load once reuse cache and cycles never recurse`() {
        val calls = mutableListOf<String>()
        val nested = "https://example.com/nested"
        val entry = JSONObject().put("_type", "playlist").put("url", nested).put("title", "Nested")
        val json = JSONObject().put("entries", JSONArray().put(entry).put(entry)).toString()
        val items = engine.parseInspection(json, url, false) { link ->
            calls += link
            """{"entries":[{"id":"1","_type":"url","url":"https://example.com/one"},{"_type":"playlist","url":"$url"}]}"""
        }
        assertEquals(listOf(nested), calls)
        assertEquals(4, items.size)
        assertEquals(2, items.count { it.unavailableReason?.contains("cycle") == true })
        assertEquals(2, items.count { it.url == "https://example.com/one" })
    }

    @Test fun `nested load failure preserves siblings and cancellation propagates`() {
        val json = """{"entries":[{"_type":"playlist","url":"https://example.com/nested"},{"id":"2","_type":"url","url":"https://example.com/two"}]}"""
        val items = engine.parseInspection(json, url, false) { throw java.io.IOException("private URL must not leak") }
        assertEquals(2, items.size)
        assertEquals("Nested collection could not be inspected", items.first().unavailableReason)
        assertNull(items.last().unavailableReason)
        assertThrows(CancellationException::class.java) { engine.parseInspection(json, url, false) { throw CancellationException() } }
    }

    @Test fun `empty and addressless nested collections cannot enqueue a guessed root entry`() {
        val empty = engine.parseInspection("""{"entries":[]}""", url, false)
        assertTrue(empty.all { it.unavailableReason != null })
        val nested = engine.parseInspection("""{"entries":[{"entries":[{"id":"1"}]}]}""", url, false)
        assertEquals("Nested entry has no source link", nested.single().unavailableReason)
    }

    @Test fun `node depth and linked inspection budgets are bounded`() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.parseInspection(JSONObject().put("entries", JSONArray((0..2000).map { JSONObject().put("id", it) })).toString(), url, false)
        }
        var nested = JSONObject().put("entries", JSONArray().put(JSONObject().put("id", "1")))
        repeat(9) { index -> nested = JSONObject().put("entries", JSONArray().put(nested.put("url", "https://example.com/n$index"))) }
        assertThrows(IllegalArgumentException::class.java) { engine.parseInspection(nested.toString(), url, false) }
        val links = JSONArray((1..18).map { JSONObject().put("_type", "playlist").put("url", "https://example.com/$it") })
        var calls = 0
        val items = engine.parseInspection(JSONObject().put("entries", links).toString(), url, false) {
            calls++; """{"entries":[{"id":"1"}]}"""
        }
        assertEquals(16, calls)
        assertEquals(18, items.size)
        assertEquals(2, items.count { it.unavailableReason != null })
    }
}
