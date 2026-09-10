package dev.qtremors.acqua.downloader

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FilenameFormatter {
    const val DEFAULT_PATTERN = "{title}_{username}_{resolution}_{date}_{time}_{index}"
    const val LEGACY_DEFAULT_PATTERN = "acqua_{username}_{resolution}_{date}_{time}_{index}"
    const val DEFAULT_AUDIO_PATTERN = "{title}"

    val variables = listOf("{title}", "{username}", "{resolution}", "{date}", "{time}", "{index}")
    val audioVariables = listOf("{title}", "{artist}", "{album}", "{username}", "{date}", "{time}", "{index}")

    fun format(
        pattern: String,
        username: String?,
        width: Int,
        height: Int,
        index: Int,
        fileExtension: String,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        now: Date = Date()
    ): String {
        val date = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(now)
        val time = SimpleDateFormat("HHmmss", Locale.ROOT).format(now)
        val safeExtension = fileExtension.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), "")
            .ifBlank { "bin" }
        val resolution = if (width > 0 && height > 0) "${width}x${height}" else ""
        val effectiveArtist = artist ?: username

        var name = pattern
            .replace("{title}", title.orEmpty())
            .replace("{artist}", effectiveArtist.orEmpty())
            .replace("{album}", album.orEmpty())
            .replace("{username}", username.orEmpty())
            .replace("{resolution}", resolution)
            .replace("{date}", date)
            .replace("{time}", time)
            .replace("{index}", (index + 1).toString())
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("[\\p{Cc}\\p{Cf}]"), "")
            .replace(Regex("\\s+"), " ")

        while (name.contains("__")) name = name.replace("__", "_")
        name = name.trim('_', ' ', '.')
        if (name.isEmpty()) {
            name = title?.takeIf(String::isNotBlank)?.let(::sanitizeSegment)
                ?: effectiveArtist?.takeIf(String::isNotBlank)?.let(::sanitizeSegment)
                ?: "download_${date}_${time}_${index + 1}"
        }

        return "$name.$safeExtension"
    }

    private fun sanitizeSegment(value: String): String = value
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("[\\p{Cc}\\p{Cf}]"), "")
        .replace(Regex("\\s+"), " ")
        .trim('_', ' ', '.')

    fun toggleVariable(pattern: String, variable: String): String {
        if (variable !in variables) return pattern
        if (!pattern.contains(variable)) {
            return if (pattern.isBlank()) variable else "${pattern.trimEnd('_')}_$variable"
        }

        var updated = pattern.replace(variable, "")
        while (updated.contains("__")) updated = updated.replace("__", "_")
        return updated.trim('_')
    }

    fun toggleAudioVariable(pattern: String, variable: String): String {
        if (variable !in audioVariables) return pattern
        if (!pattern.contains(variable)) {
            return if (pattern.isBlank()) variable else "${pattern.trimEnd('_')}_$variable"
        }

        var updated = pattern.replace(variable, "")
        while (updated.contains("__")) updated = updated.replace("__", "_")
        return updated.trim('_')
    }

    fun preview(pattern: String): String = format(
        pattern = pattern,
        username = "creator",
        width = 1080,
        height = 1920,
        index = 0,
        fileExtension = "mp4",
        title = "Sample video",
        now = Date(1_704_067_200_000L)
    )

    fun previewAudio(pattern: String): String = format(
        pattern = pattern,
        username = "artist_channel",
        width = 0,
        height = 0,
        index = 0,
        fileExtension = "mp3",
        title = "Sample song",
        artist = "Sample artist",
        album = "Sample album",
        now = Date(1_704_067_200_000L)
    )

    fun withCollisionSuffix(fileName: String, sequence: Int): String {
        if (sequence <= 0) return fileName
        val extensionIndex = fileName.lastIndexOf('.').takeIf { it > 0 } ?: fileName.length
        return "${fileName.substring(0, extensionIndex)} ($sequence)${fileName.substring(extensionIndex)}"
    }
}
