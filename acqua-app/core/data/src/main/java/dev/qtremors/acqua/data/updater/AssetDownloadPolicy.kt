package dev.qtremors.acqua.data.updater

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor

internal object AssetDownloadPolicy {
    private val assetPath = Regex("/repos/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/releases/assets/[1-9][0-9]*")
    fun isTrusted(url: HttpUrl): Boolean = url.scheme == "https" && url.host == "api.github.com" &&
        url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
        url.query == null && url.fragment == null && assetPath.matches(url.encodedPath)
    fun requireTrusted(url: String) {
        require(url.toHttpUrlOrNull()?.let(::isTrusted) == true) { "The authenticated asset endpoint is not trusted." }
    }
    val stripUntrustedCredentials = Interceptor { chain ->
        val request = chain.request()
        chain.proceed(if (request.header("Authorization") != null && !isTrusted(request.url)) {
            request.newBuilder().removeHeader("Authorization").build()
        } else request)
    }
}
