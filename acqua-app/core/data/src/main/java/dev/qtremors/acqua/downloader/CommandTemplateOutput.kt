package dev.qtremors.acqua.downloader

import org.json.JSONArray

internal object CommandTemplateOutput {
    const val MARKER = "ACQUA_TEMPLATE:"
    fun arguments(options: YtDlpDownloadOptions): List<Pair<String, String?>> = if (options.formatSelectorOverride == null) emptyList()
        else listOf("--print" to "after_move:$MARKER[%(vcodec)j,%(acodec)j]")

    fun verify(output: String, options: YtDlpDownloadOptions) {
        if (options.formatSelectorOverride == null) return
        val rows = output.lineSequence().filter { it.startsWith(MARKER) }.toList()
        if (rows.size != 1) throw InvalidCommandTemplateException()
        val raw = rows.single().removePrefix(MARKER)
        if (raw.length > 16384) throw InvalidCommandTemplateException()
        val row = runCatching { JSONArray(raw) }.getOrElse { throw InvalidCommandTemplateException() }
        if (row.length() != 2) throw InvalidCommandTemplateException()
        if (options.contentType == DownloadContentType.VIDEO && row.optString(0) == "none") throw InvalidCommandTemplateException()
        if (options.contentType == DownloadContentType.AUDIO && row.optString(1) == "none") throw InvalidCommandTemplateException()
    }
}
