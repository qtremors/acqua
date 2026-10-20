package dev.qtremors.acqua.downloader

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadOutputSetTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun `manifest preserves every media output and requested caption`() {
        val directory = temporary.newFolder("task")
        val first = File(directory, "first.mp4").apply { writeText("large video") }
        val second = File(directory, "second.mp4").apply { writeText("small") }
        val caption = File(directory, "first.en.srt").apply { writeText("caption") }
        File(directory, "unused.jpg").writeText("cover")
        File(directory, "transfer.part").writeText("partial")
        val manifest = File(directory, "outputs.jsonl").apply { writeText(listOf(first, second).joinToString("\n") { JSONObject.quote(it.absolutePath) }) }
        val outputs = DownloadOutputSet.read(directory, manifest, CaptionMode.BOTH)
        assertEquals(listOf(first.canonicalFile, caption.canonicalFile, second.canonicalFile), outputs.artifacts.map { it.file })
        assertEquals(".en", outputs.artifacts[1].suffix)
        assertTrue(outputs.artifacts[1].caption)
        assertEquals(2, DownloadOutputSet.read(directory, manifest, CaptionMode.EMBED).artifacts.size)
    }
    @Test fun `manifest rejects files outside its task and empty outputs`() {
        val directory = temporary.newFolder("task")
        val outside = temporary.newFile("outside.mp4").apply { writeText("media") }
        val manifest = File(directory, "outputs.jsonl").apply { writeText(JSONObject.quote(outside.absolutePath)) }
        assertTrue(runCatching { DownloadOutputSet.read(directory, manifest, CaptionMode.NONE) }.isFailure)
        manifest.writeText("")
        assertTrue(runCatching { DownloadOutputSet.read(directory, manifest, CaptionMode.NONE) }.isFailure)
    }
    @Test fun `publication identity survives source title changes`() {
        val directory = temporary.newFolder("task")
        val first = File(directory, "Original title [42].mp4").apply { writeText("video") }
        val manifest = File(directory, "outputs.jsonl")
        manifest.writeText(org.json.JSONArray().put("42").put(first.absolutePath).toString())
        val original = DownloadOutputSet.read(directory, manifest, CaptionMode.NONE).artifacts.single().checkpointKey
        val renamed = File(directory, "Changed title [42].mp4").apply { writeText("video") }
        manifest.writeText(org.json.JSONArray().put("42").put(renamed.absolutePath).toString())
        assertEquals(original, DownloadOutputSet.read(directory, manifest, CaptionMode.NONE).artifacts.single().checkpointKey)
    }
}
