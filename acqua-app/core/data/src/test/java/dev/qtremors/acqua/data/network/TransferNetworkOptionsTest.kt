package dev.qtremors.acqua.data.network

import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferNetworkOptionsTest {
    @Test fun `only bounded options and credential-free supported proxies are accepted`() {
        listOf("", "http://127.0.0.1:8080", "http://[::1]:8080", "socks5://localhost:1080").forEach {
            assertEquals(it, TransferNetworkOptions(proxy = it).validated().proxy)
        }
        listOf("http://user:pass@host:80", "https://host:80", "http://host", "http://host:0", "http://host:65536",
            "http://host:80/", "http://host:80?secret=value", "http://host:80#fragment", "http://host:80\n", "host:80").forEach {
            assertThrows(IllegalArgumentException::class.java) { TransferNetworkOptions(proxy = it).validated() }
        }
        listOf(TransferNetworkOptions(rateLimitKib = -1), TransferNetworkOptions(rateLimitKib = 1_048_577),
            TransferNetworkOptions(concurrentFragments = 0), TransferNetworkOptions(concurrentFragments = 9),
            TransferNetworkOptions(proxy = "socks5://localhost:1080", forceIpv4 = true)).forEach {
            assertThrows(IllegalArgumentException::class.java) { it.validated() }
        }
    }

    @Test fun `yt-dlp inspection uses proxy and address policy while downloads also use rate and fragments`() {
        val options = TransferNetworkOptions(2048, "http://127.0.0.1:8080", true, 4)
        assertEquals(listOf("--proxy" to options.proxy, "--force-ipv4" to null), options.ytDlpArguments(inspection = true))
        assertEquals(listOf("--proxy" to options.proxy, "--force-ipv4" to null, "--limit-rate" to "2048K", "--concurrent-fragments" to "4"),
            options.ytDlpArguments())
        assertEquals(listOf("--concurrent-fragments" to "1"), TransferNetworkOptions().ytDlpArguments())
    }

    @Test fun `IPv4 policy filters dual-stack DNS and fails without a matching address`() {
        val ipv4 = InetAddress.getByName("127.0.0.1")
        val ipv6 = InetAddress.getByName("::1")
        assertEquals(listOf(ipv4), ipv4Dns(Dns { listOf(ipv6, ipv4) }).lookup("fixture.invalid"))
        assertThrows(UnknownHostException::class.java) { ipv4Dns(Dns { listOf(ipv6) }).lookup("fixture.invalid") }
    }

    @Test fun `IPv4 policy rejects literal IPv6 and system SOCKS routes before connecting`() {
        val client = TransferNetworkOptions(forceIpv4 = true).configure(OkHttpClient())
        client.socketFactory.createSocket().use { socket ->
            assertThrows(UnknownHostException::class.java) {
                socket.connect(java.net.InetSocketAddress(InetAddress.getByName("::1"), 12345))
            }
            assertFalse(socket.isConnected)
        }
        val socks = java.net.Proxy(java.net.Proxy.Type.SOCKS, java.net.InetSocketAddress("127.0.0.1", 1080))
        val selector = object : java.net.ProxySelector() {
            override fun select(uri: java.net.URI) = listOf(socks)
            override fun connectFailed(uri: java.net.URI, address: java.net.SocketAddress, error: IOException) {}
        }
        assertThrows(IOException::class.java) { ipv4ProxySelector(selector).select(java.net.URI("http://fixture.invalid")) }
    }

    @Test fun `speed accounting includes every byte and checks cancellation within a short wait`() {
        var time = 0L
        val waits = mutableListOf<Long>()
        val limiter = TransferRateLimiter(8192, { time }) { waits += it; time += it }
        repeat(2) { limiter.account(8192) }
        assertEquals(2_000_000_000L, time)
        assertTrue(waits.all { it in 1..100_000_000L })
        var cancelled = false
        val stopped = TransferRateLimiter(1024, { time }) { time += it; cancelled = true }
        assertThrows(IOException::class.java) { stopped.account(8192) { cancelled } }
        assertEquals(2_100_000_000L, time)
    }

    @Test fun `HTTP proxy handles both inspection traffic and downloads without resolving the origin locally`() {
        MockWebServer().use { proxy ->
            val payload = jpeg(100)
            repeat(2) { proxy.enqueue(response(payload)) }
            val client = HttpMediaClient().withNetworkOptions(TransferNetworkOptions(proxy = proxyAddress(proxy)))
            val item = ResolvedMedia("http://unresolvable.invalid/photo.jpg", MediaKind.IMAGE)
            assertArrayEquals(payload, client.fetchBytes(item))
            val output = ByteArrayOutputStream()
            assertEquals(payload.size.toLong(), client.downloadToStream(item, output))
            assertArrayEquals(payload, output.toByteArray())
            repeat(2) {
                val request = proxy.takeRequest(2, TimeUnit.SECONDS)!!
                assertTrue(request.requestLine.startsWith("GET http://unresolvable.invalid/photo.jpg "))
                assertEquals("unresolvable.invalid", request.getHeader("Host"))
                assertEquals(null, request.getHeader("Proxy-Authorization"))
            }
        }
    }

    @Test fun `an accepted network snapshot keeps its proxy when the inspection default changes`() {
        MockWebServer().use { first -> MockWebServer().use { second ->
            val payload = jpeg(100)
            first.enqueue(response(payload)); second.enqueue(response(payload))
            var defaults = TransferNetworkOptions(proxy = proxyAddress(first))
            val shared = HttpMediaClient(networkOptions = { defaults })
            val accepted = shared.withNetworkOptions(defaults)
            defaults = TransferNetworkOptions(proxy = proxyAddress(second))
            val item = ResolvedMedia("http://fixture.invalid/photo.jpg", MediaKind.IMAGE)
            accepted.fetchBytes(item)
            shared.fetchBytes(item)
            assertNotNull(first.takeRequest(2, TimeUnit.SECONDS))
            assertNotNull(second.takeRequest(2, TimeUnit.SECONDS))
        } }
    }

    @Test fun `a limited local transfer paces the signature prefix too`() {
        MockWebServer().use { server ->
            val payload = jpeg(16_384)
            server.enqueue(response(payload))
            val client = HttpMediaClient().withNetworkOptions(TransferNetworkOptions(rateLimitKib = 64))
            val output = ByteArrayOutputStream()
            val started = System.nanoTime()
            client.downloadToStream(ResolvedMedia(server.url("photo.jpg").toString(), MediaKind.IMAGE), output)
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) >= 200)
            assertArrayEquals(payload, output.toByteArray())
        }
    }

    @Test fun `cancelling a rate-limited transfer releases the HTTP call promptly`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(response(jpeg(65_536)))
            val http = OkHttpClient.Builder().build()
            val client = HttpMediaClient(http).withNetworkOptions(TransferNetworkOptions(rateLimitKib = 1))
            val job = launch(Dispatchers.IO) {
                client.downloadToStreamCancellable(ResolvedMedia(server.url("photo.jpg").toString(), MediaKind.IMAGE), ByteArrayOutputStream())
            }
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            job.cancelAndJoin()
            withTimeout(2_000) { while (http.dispatcher.runningCallsCount() > 0) delay(10) }
            assertFalse(job.isActive)
        }
    }

    @Test fun `proxy failure does not fall back to a direct origin request`() {
        val closedProxy = MockWebServer()
        closedProxy.start()
        val port = closedProxy.port
        closedProxy.close()
        MockWebServer().use { origin ->
            val client = HttpMediaClient(OkHttpClient.Builder().connectTimeout(100, TimeUnit.MILLISECONDS).build())
                .withNetworkOptions(TransferNetworkOptions(proxy = "http://127.0.0.1:$port"))
            assertThrows(IOException::class.java) { client.fetchBytes(ResolvedMedia(origin.url("photo.jpg").toString(), MediaKind.IMAGE)) }
            assertEquals(0, origin.requestCount)
        }
    }

    @Test fun `SOCKS5 proxy receives the unresolved origin and transfers media`() {
        val payload = jpeg(100)
        java.net.ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { socket ->
            val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
            try {
                val handled = executor.submit<String> {
                    socket.accept().use { connection ->
                        connection.soTimeout = 2_000
                        val input = java.io.DataInputStream(connection.getInputStream())
                        val output = connection.getOutputStream()
                        assertEquals(5, input.readUnsignedByte())
                        input.readNBytes(input.readUnsignedByte())
                        output.write(byteArrayOf(5, 0)); output.flush()
                        assertEquals(5, input.readUnsignedByte())
                        assertEquals(1, input.readUnsignedByte())
                        assertEquals(0, input.readUnsignedByte())
                        assertEquals(3, input.readUnsignedByte())
                        val host = input.readNBytes(input.readUnsignedByte()).toString(Charsets.UTF_8)
                        assertEquals(80, input.readUnsignedShort())
                        output.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0, 0)); output.flush()
                        val reader = input.bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) { /* Consume request headers. */ }
                        output.write("HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        output.write(payload); output.flush()
                        host
                    }
                }
                val client = HttpMediaClient().withNetworkOptions(TransferNetworkOptions(proxy = "socks5://127.0.0.1:${socket.localPort}"))
                val result = client.fetchBytes(ResolvedMedia("http://socks-origin.invalid/photo.jpg", MediaKind.IMAGE))
                assertArrayEquals(payload, result)
                assertEquals("socks-origin.invalid", handled.get(2, TimeUnit.SECONDS))
            } finally { executor.shutdownNow() }
        }
    }

    private fun jpeg(size: Int) = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(size)
    private fun response(bytes: ByteArray) = MockResponse().setHeader("Content-Type", "image/jpeg").setBody(Buffer().write(bytes))
    private fun proxyAddress(server: MockWebServer) = "http://127.0.0.1:${server.url("/").port}"
}
