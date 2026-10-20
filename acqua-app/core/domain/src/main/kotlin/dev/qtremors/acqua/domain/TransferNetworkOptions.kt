package dev.qtremors.acqua.domain

import java.net.URI

data class TransferNetworkOptions(
    val rateLimitKib: Int = 0,
    val proxy: String = "",
    val forceIpv4: Boolean = false,
    val concurrentFragments: Int = 1
) {
    fun validated(): TransferNetworkOptions {
        require(rateLimitKib in 0..1_048_576) { "Invalid transfer speed limit." }
        require(concurrentFragments in 1..8) { "Invalid fragment count." }
        require(proxy.length <= 512 && proxy.none(Char::isWhitespace)) { "Invalid transfer proxy." }
        if (proxy.isNotEmpty()) {
            val uri = runCatching { URI(proxy) }.getOrNull()
            require(uri != null && uri.scheme in setOf("http", "socks5") && !uri.host.isNullOrBlank() &&
                uri.port in 1..65535 && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
                uri.rawPath.isNullOrEmpty()) { "Use an HTTP or SOCKS5 proxy with a port and no credentials or path." }
            require(!forceIpv4 || uri.scheme != "socks5") { "IPv4-only mode is unavailable with SOCKS5 remote DNS." }
        }
        return this
    }

    fun ytDlpArguments(inspection: Boolean = false): List<Pair<String, String?>> {
        validated()
        return buildList {
            if (proxy.isNotEmpty()) add("--proxy" to proxy)
            if (forceIpv4) add("--force-ipv4" to null)
            if (!inspection) {
                if (rateLimitKib > 0) add("--limit-rate" to "${rateLimitKib}K")
                add("--concurrent-fragments" to concurrentFragments.toString())
            }
        }
    }
}
