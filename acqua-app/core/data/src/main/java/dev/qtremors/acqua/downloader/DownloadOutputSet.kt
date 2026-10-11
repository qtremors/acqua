package dev.qtremors.acqua.downloader

import java.io.File
import org.json.JSONArray

data class DownloadArtifact(val file: File, val caption: Boolean = false, val suffix: String = "", val sourceId: String? = null,
    val title: String? = null) {
    val checkpointKey: String get() = sourceId?.let {
        "$it:${if (caption) "caption:$suffix" else "media"}:${file.extension}"
    } ?: file.name
}
data class DownloadOutputSet(val directory: File, val artifacts: List<DownloadArtifact>) {
    companion object {
        internal fun read(directory: File, manifest: File, captions: CaptionMode): DownloadOutputSet {
            val root = directory.canonicalFile
            val media = manifest.readLines().filter(String::isNotBlank).map { line ->
                val row = if (line.trimStart().startsWith('[')) JSONArray(line) else JSONArray("[$line]")
                val id = if (row.length() >= 2) row.getString(0).takeIf { it.isNotBlank() && it != "NA" } else null
                val file = File(row.getString(if (row.length() >= 2) 1 else 0)).canonicalFile
                require(file.canonicalPath.startsWith(root.canonicalPath + File.separator) && file.isFile && file.length() > 0L) {
                    "The output manifest contains an invalid media file."
                }
                DownloadArtifact(file, sourceId = id, title = if (row.length() > 2) row.optString(2).takeIf(String::isNotBlank) else null)
            }.distinct()
            require(media.isNotEmpty()) { "Processing completed without producing a media file." }
            val artifacts = media.flatMap { artifact ->
                val file = artifact.file
                val id = artifact.sourceId
                val sidecars = if (captions == CaptionMode.SEPARATE || captions == CaptionMode.BOTH) {
                    file.parentFile!!.listFiles().orEmpty().filter { candidate ->
                        candidate.isFile && candidate.length() > 0 &&
                            candidate.name.startsWith(file.nameWithoutExtension + ".") &&
                            candidate.extension.lowercase() in setOf("srt", "vtt", "ass", "lrc", "ttml", "srv1", "srv2", "srv3", "json3")
                    }.sortedBy(File::getName).map {
                        DownloadArtifact(it, caption = true,
                            suffix = it.nameWithoutExtension.removePrefix(file.nameWithoutExtension), sourceId = id)
                    }
                } else emptyList()
                listOf(artifact) + sidecars
            }
            return DownloadOutputSet(root, artifacts.distinctBy { it.file.canonicalPath })
        }
    }
}
