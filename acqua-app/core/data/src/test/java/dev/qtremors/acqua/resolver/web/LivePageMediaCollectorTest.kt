package dev.qtremors.acqua.resolver.web

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LivePageMediaCollectorTest {
    @Test
    fun `callbacks from a previous page cannot replace current page media`() {
        val collector = LivePageMediaCollector(requiredStablePasses = 1)
        val oldGeneration = collector.reset()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val lateCapture = executor.submit {
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                collector.captureNetworkVideo("https://cdn.test/old.mp4", oldGeneration)
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val currentGeneration = collector.reset()
            collector.captureNetworkVideo("https://cdn.test/current.mp4", currentGeneration)
            release.countDown()
            lateCapture.get(5, TimeUnit.SECONDS)
            assertEquals(LivePageExtractionOutcome.Continue,
                collector.consume(payload("https://cdn.test/old.jpg", 1080), SOURCE_URL, oldGeneration))
            val outcome = collector.consume(JSONObject().put("expectsVideo", true).put("structuredCarousel", true),
                SOURCE_URL, currentGeneration) as LivePageExtractionOutcome.Complete
            assertEquals(listOf("https://cdn.test/current.mp4"), outcome.media.map { it.url })
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `concurrent network capture consumption and resets leave a clean current page`() {
        val collector = LivePageMediaCollector(maximumAttempts = 1000)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(3)
        try {
            val jobs = listOf(
                executor.submit {
                    check(start.await(5, TimeUnit.SECONDS))
                    repeat(150) { collector.captureNetworkVideo("https://cdn.test/$it.mp4") }
                },
                executor.submit {
                    check(start.await(5, TimeUnit.SECONDS))
                    repeat(150) { collector.consume(JSONObject().put("expectsVideo", true), SOURCE_URL) }
                },
                executor.submit {
                    check(start.await(5, TimeUnit.SECONDS))
                    repeat(50) { collector.reset() }
                }
            )
            start.countDown()
            jobs.forEach { it.get(5, TimeUnit.SECONDS) }
            val generation = collector.reset()
            collector.captureNetworkVideo("https://cdn.test/final.mp4", generation)
            val outcome = collector.consume(JSONObject().put("expectsVideo", true).put("structuredCarousel", true),
                SOURCE_URL, generation) as LivePageExtractionOutcome.Complete
            assertEquals(listOf("https://cdn.test/final.mp4"), outcome.media.map { it.url })
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `signed variants of one Instagram image produce one preview`() {
        val collector = LivePageMediaCollector(maximumAttempts = 3, requiredStablePasses = 1)
        val first = payload(
            "https://scontent.cdninstagram.com/v/photo.jpg?stp=dst-jpg_s640x640&token=one",
            width = 640
        )
        val second = payload(
            "https://scontent.cdninstagram.com/v/photo.jpg?stp=dst-jpg_s1080x1080&token=two",
            width = 1080
        )

        assertTrue(collector.consume(first, SOURCE_URL) is LivePageExtractionOutcome.Continue)
        val outcome = collector.consume(second, SOURCE_URL) as LivePageExtractionOutcome.Complete

        assertEquals(1, outcome.media.size)
        assertEquals(1080, outcome.media.single().width)
        assertEquals("token=two", outcome.media.single().url.substringAfterLast('&'))
    }

    @Test
    fun `visible next control prevents completion while carousel is still advancing`() {
        val collector = LivePageMediaCollector(maximumAttempts = 5, requiredStablePasses = 1)
        val first = payload("https://cdn.test/first.jpg", width = 1080).put("hasNext", true)

        assertTrue(collector.consume(first, SOURCE_URL) is LivePageExtractionOutcome.Continue)
        assertTrue(collector.consume(first, SOURCE_URL) is LivePageExtractionOutcome.Continue)

        val final = payload("https://cdn.test/second.jpg", width = 1080)
        assertTrue(collector.consume(final, SOURCE_URL) is LivePageExtractionOutcome.Continue)
        val outcome = collector.consume(final, SOURCE_URL) as LivePageExtractionOutcome.Complete

        assertEquals(2, outcome.media.size)
    }

    @Test
    fun `complete structured carousel returns without simulated navigation`() {
        val collector = LivePageMediaCollector(maximumAttempts = 5, requiredStablePasses = 4)
        val items = JSONArray()
            .put(payloadItem("https://cdn.test/first.jpg"))
            .put(
                payloadItem("https://cdn.test/second.mp4")
                    .put("isVideo", true)
                    .put("thumbnail", "https://cdn.test/second-poster.jpg")
            )
        val payload = JSONObject()
            .put("items", items)
            .put("structuredCarousel", true)

        val outcome = collector.consume(payload, SOURCE_URL) as LivePageExtractionOutcome.Complete

        assertEquals(2, outcome.media.size)
        assertEquals(false, outcome.media.first().isVideo)
        assertEquals(true, outcome.media.last().isVideo)
    }

    private fun payload(url: String, width: Int) = JSONObject()
        .put(
            "items",
            JSONArray().put(
                payloadItem(url, width)
            )
        )
        .put("clickedNext", false)

    private fun payloadItem(url: String, width: Int = 1080) = JSONObject()
        .put("url", url)
        .put("isVideo", false)
        .put("thumbnail", "")
        .put("width", width)
        .put("height", width)

    private companion object {
        const val SOURCE_URL = "https://www.instagram.com/p/example/"
    }
}
