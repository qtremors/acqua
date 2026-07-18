package dev.qtremors.acqua.downloader

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FilenameFormatter {
    const val DEFAULT_PATTERN = "acqua_{username}_{resolution}_{date}_{time}_{index}"

    val variables = listOf("{username}", "{resolution}", "{date}", "{time}", "{index}")

    fun format(
        pattern: String,
        username: String?,
        width: Int,
        height: Int,
        index: Int,
        fileExtension: String,
        now: Date = Date()
    ): String {
        val date = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(now)
        val time = SimpleDateFormat("HHmmss", Locale.ROOT).format(now)
        val safeExtension = fileExtension.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), "")
            .ifBlank { "bin" }
        val resolution = if (width > 0 && height > 0) "${width}x${height}" else ""

        var name = pattern
            .replace("{username}", username.orEmpty())
            .replace("{resolution}", resolution)
            .replace("{date}", date)
            .replace("{time}", time)
            .replace("{index}", (index + 1).toString())
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")

        while (name.contains("__")) name = name.replace("__", "_")
        name = name.trim('_', ' ', '.')
        if (name.isEmpty()) name = "acqua_${date}_${time}_${index + 1}"

        return "$name.$safeExtension"
    }

    fun toggleVariable(pattern: String, variable: String): String {
        if (variable !in variables) return pattern
        if (!pattern.contains(variable)) {
            return if (pattern.isBlank()) variable else "${pattern.trimEnd('_')}_$variable"
        }

        var updated = pattern.replace(variable, "")
        while (updated.contains("__")) updated = updated.replace("__", "_")
        return updated.trim('_')
    }
}
