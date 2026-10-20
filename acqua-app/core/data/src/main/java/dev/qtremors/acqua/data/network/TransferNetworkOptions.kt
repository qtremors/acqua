package dev.qtremors.acqua.data.network

import dev.qtremors.acqua.domain.TransferNetworkOptions
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.io.IOException
import java.io.InterruptedIOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.Socket
import java.net.SocketAddress
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.locks.LockSupport
import javax.net.SocketFactory

internal fun TransferNetworkOptions.configure(client: OkHttpClient): OkHttpClient {
    validated()
    if (rateLimitKib == 0 && proxy.isEmpty() && !forceIpv4) return client
    return client.newBuilder().apply {
        if (proxy.isNotEmpty()) {
            val uri = URI(proxy)
            proxy(Proxy(if (uri.scheme == "http") Proxy.Type.HTTP else Proxy.Type.SOCKS,
                InetSocketAddress.createUnresolved(uri.host, uri.port)))
        }
        if (forceIpv4) {
            require(proxy.isNotEmpty() || client.proxy?.type() != Proxy.Type.SOCKS) { "IPv4-only mode is unavailable with SOCKS proxies." }
            dns(ipv4Dns(client.dns))
            socketFactory(Ipv4SocketFactory())
            proxySelector(ipv4ProxySelector(client.proxySelector))
        }
        if (rateLimitKib > 0) addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val body = response.body ?: return@addNetworkInterceptor response
            val limiter = TransferRateLimiter(rateLimitKib * 1024L)
            val source = object : ForwardingSource(body.source()) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    if (chain.call().isCanceled()) throw IOException("Media request cancelled")
                    val read = super.read(sink, minOf(byteCount, 8192))
                    if (read > 0) limiter.account(read) { chain.call().isCanceled() }
                    return read
                }
            }.buffer()
            response.newBuilder().body(object : ResponseBody() {
                override fun contentType() = body.contentType()
                override fun contentLength() = body.contentLength()
                override fun source(): BufferedSource = source
            }).build()
        }
    }.build()
}

internal fun ipv4Dns(delegate: Dns): Dns = Dns { host ->
    delegate.lookup(host).filterIsInstance<Inet4Address>().ifEmpty { throw UnknownHostException("No IPv4 address is available") }
}

internal fun ipv4ProxySelector(delegate: ProxySelector): ProxySelector = object : ProxySelector() {
    override fun select(uri: URI): List<Proxy> = delegate.select(uri).also { proxies ->
        if (proxies.any { it.type() == Proxy.Type.SOCKS }) throw IOException("IPv4-only mode is unavailable with SOCKS proxies")
    }
    override fun connectFailed(uri: URI, address: SocketAddress, error: IOException) = delegate.connectFailed(uri, address, error)
}

/** DNS filtering alone does not cover literal IPs, including redirect and proxy addresses. */
internal class Ipv4SocketFactory : SocketFactory() {
    override fun createSocket(): Socket = object : Socket() {
        override fun connect(endpoint: SocketAddress?, timeout: Int) {
            if (endpoint !is InetSocketAddress || endpoint.address !is Inet4Address) {
                throw UnknownHostException("IPv4-only mode cannot connect to this address")
            }
            super.connect(endpoint, timeout)
        }
        override fun connect(endpoint: SocketAddress?) = connect(endpoint, 0)
    }
    override fun createSocket(host: String, port: Int): Socket = createSocket().apply { connect(InetSocketAddress(host, port)) }
    override fun createSocket(host: java.net.InetAddress, port: Int): Socket = createSocket().apply { connect(InetSocketAddress(host, port)) }
    override fun createSocket(host: String, port: Int, local: java.net.InetAddress, localPort: Int): Socket = createSocket().apply {
        bind(InetSocketAddress(local, localPort)); connect(InetSocketAddress(host, port))
    }
    override fun createSocket(host: java.net.InetAddress, port: Int, local: java.net.InetAddress, localPort: Int): Socket = createSocket().apply {
        bind(InetSocketAddress(local, localPort)); connect(InetSocketAddress(host, port))
    }
}

internal class TransferRateLimiter(private val bytesPerSecond: Long,
    private val now: () -> Long = System::nanoTime,
    private val wait: (Long) -> Unit = LockSupport::parkNanos
) {
    private var deadline = now()
    fun account(bytes: Long, cancelled: () -> Boolean = { false }) {
        require(bytes in 1..8192 && bytesPerSecond > 0)
        deadline = maxOf(deadline, now()) + bytes * 1_000_000_000L / bytesPerSecond
        while (true) {
            if (cancelled()) throw IOException("Media request cancelled")
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Media request interrupted")
            val remaining = deadline - now()
            if (remaining <= 0) return
            wait(minOf(remaining, 100_000_000L))
        }
    }
}
