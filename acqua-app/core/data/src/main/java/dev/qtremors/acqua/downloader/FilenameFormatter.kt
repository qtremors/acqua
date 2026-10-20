package dev.qtremors.acqua.downloader

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import dev.qtremors.acqua.domain.ResolvedMedia

data class FilenameContext(
    val sourceId: String? = null,
    val extractor: String? = null,
    val collection: String? = null,
    val collectionIndex: Int = 0,
    val collectionCount: Int = 0,
    val chapter: String? = null,
    val uploadTimestampMillis: Long? = null
) {
    companion object {
        fun fromMedia(media: ResolvedMedia, chapter: String? = null) = FilenameContext(
            sourceId = media.sourceIdentity?.substringAfterLast(':'),
            extractor = media.sourceIdentity?.takeIf { ':' in it }?.substringBefore(':'),
            collection = media.collectionTitle, collectionIndex = media.collectionIndex, collectionCount = media.collectionSize,
            chapter = chapter, uploadTimestampMillis = media.sourceTimestampMillis
        )
    }
}

object FilenameFormatter {
    const val DEFAULT_PATTERN = "{title}_{username}_{resolution}_{date}_{time}_{index}"
    const val LEGACY_DEFAULT_PATTERN = "acqua_{username}_{resolution}_{date}_{time}_{index}"
    const val DEFAULT_AUDIO_PATTERN = "{title}"

    const val MAX_FILENAME_BYTES = 240
    private const val COLLISION_RESERVE = 13
    private val contextVariables = listOf("{source_id}", "{extractor}", "{collection}", "{collection_index}", "{collection_count}", "{chapter}", "{upload_date}")
    val variables = listOf("{title}", "{username}", "{resolution}", "{date}", "{time}", "{index}") + contextVariables
    val audioVariables = listOf("{title}", "{artist}", "{album}", "{username}", "{date}", "{time}", "{index}") + contextVariables

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
        now: Date = Date(),
        itemCount: Int = 1,
        context: FilenameContext = FilenameContext()
    ): String {
        val date = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(now)
        val time = SimpleDateFormat("HHmmss", Locale.ROOT).format(now)
        val safeExtension = fileExtension.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), "")
            .take(12)
            .ifBlank { "bin" }
        val resolution = if (width > 0 && height > 0) "${width}x${height}" else ""
        val effectiveArtist = artist ?: username

        val uploadDate = context.uploadTimestampMillis?.let {
            SimpleDateFormat("yyyyMMdd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(it))
        }.orEmpty()
        var name = expand(pattern, mapOf("title" to title.orEmpty(), "artist" to effectiveArtist.orEmpty(),
            "album" to album.orEmpty(), "username" to username.orEmpty(), "resolution" to resolution,
            "date" to date, "time" to time, "index" to if (itemCount > 1) (index + 1).toString() else "",
            "source_id" to context.sourceId.orEmpty(), "extractor" to context.extractor.orEmpty(),
            "collection" to context.collection.orEmpty(), "collection_index" to context.collectionIndex.takeIf { it > 0 }?.toString().orEmpty(),
            "collection_count" to context.collectionCount.takeIf { it > 0 }?.toString().orEmpty(),
            "chapter" to context.chapter.orEmpty(), "upload_date" to uploadDate))
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("[\\p{Cc}\\p{Cf}]"), "")
            .replace(Regex("\\s+"), " ")

        while (name.contains("__")) name = name.replace("__", "_")
        name = name.trim('_', ' ', '.')
        if (name.isEmpty()) {
            name = sequenceOf(title, artist, username)
                .filterNotNull()
                .map { sanitizeSegment(it.take(8192)) }
                .firstOrNull(String::isNotBlank)
                ?: "download_${date}_${time}" + if (itemCount > 1) "_${index + 1}" else ""
        }

        return boundedName(name, ".$safeExtension", COLLISION_RESERVE + 48 + 12 - safeExtension.length)
    }

    private fun expand(pattern: String, values: Map<String, String>): String = buildString {
        var index = 0
        fun appendBounded(value: String) { append(value, 0, minOf(value.length, 8192 - length)) }
        while (index < pattern.length && length < 8192) {
            when {
                pattern.startsWith("{{", index) -> { append('{'); index += 2 }
                pattern.startsWith("}}", index) -> { append('}'); index += 2 }
                pattern[index] == '{' -> {
                    val end = pattern.indexOf('}', index + 1)
                    if (end > index && end - index <= 256) {
                        val expression = pattern.substring(index + 1, end)
                        val key = expression.substringBefore('|')
                        val value = values[key]
                        if (value != null) appendBounded(value.ifBlank { expression.substringAfter('|', "") })
                        else appendBounded(pattern.substring(index, end + 1))
                        index = end + 1
                    } else { append(pattern[index]); index++ }
                }
                else -> { append(pattern[index]); index++ }
            }
        }
    }

    private fun boundedName(base: String, tail: String, reserve: Int = 0): String =
        truncateUtf8(base, MAX_FILENAME_BYTES - tail.toByteArray(Charsets.UTF_8).size - reserve)
            .trimEnd(' ', '.').ifBlank { "download" } + tail

    private fun truncateUtf8(value: String, limit: Int): String {
        var end = 0
        var bytes = 0
        while (end < value.length) {
            val point = value.codePointAt(end)
            val size = when { point < 0x80 -> 1; point < 0x800 -> 2; point < 0x10000 -> 3; else -> 4 }
            if (bytes + size > limit) break
            bytes += size
            end += Character.charCount(point)
        }
        return value.substring(0, end)
    }

    fun withArtifactSuffix(fileName: String, suffix: String, extension: String): String {
        val safeExtension = extension.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "").take(12).ifBlank { "bin" }
        val safeSuffix = suffix.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(48)
        return boundedName(fileName.substringBeforeLast('.', fileName), "$safeSuffix.$safeExtension", COLLISION_RESERVE)
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
        itemCount = 2,
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
        itemCount = 2,
        fileExtension = "mp3",
        title = "Sample song",
        artist = "Sample artist",
        album = "Sample album",
        now = Date(1_704_067_200_000L)
    )

    fun withCollisionSuffix(fileName: String, sequence: Int): String {
        val extensionIndex = fileName.lastIndexOf('.').takeIf { it > 0 } ?: fileName.length
        val suffix = if (sequence > 0) " ($sequence)" else ""
        val extension = if (extensionIndex == fileName.length) "" else "." + fileName.substring(extensionIndex + 1)
            .lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "").take(12).ifBlank { "bin" }
        return boundedName(fileName.substring(0, extensionIndex), suffix + extension)
    }
}
