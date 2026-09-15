package dev.qtremors.acqua.resolver.web

import android.content.Context
import org.json.JSONObject
import org.json.JSONTokener

object WebExtractionEngine {
    @Volatile
    private var cachedScript: String? = null

    fun script(context: Context): String = cachedScript ?: synchronized(this) {
        cachedScript ?: context.applicationContext.assets
            .open(SCRIPT_ASSET)
            .bufferedReader()
            .use { it.readText() }
            .also { script ->
                require("document.documentElement.innerHTML" !in script) {
                    "The extraction script must not copy full-page markup."
                }
                cachedScript = script
            }
    }

    fun decodeResult(value: String?): JSONObject? = runCatching {
        val decoded = JSONTokener(value.orEmpty()).nextValue() as? String
            ?: return@runCatching null
        JSONObject(decoded)
    }.getOrNull()

    internal const val SCRIPT_ASSET = "web_media_extractor.js"
}
