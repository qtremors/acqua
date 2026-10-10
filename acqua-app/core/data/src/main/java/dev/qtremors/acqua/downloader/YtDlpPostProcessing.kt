package dev.qtremors.acqua.downloader

import java.io.File

internal object YtDlpPostProcessing {
    fun options(options: YtDlpDownloadOptions): List<Pair<String, String?>> = buildList {
        if (options.embedMetadata) {
            if (!options.preserveOriginalMetadata) {
                // Evaluate the clock after transfer, immediately before metadata embedding.
                add("--parse-metadata" to "post_process:%(epoch>%Y-%m-%dT%H\\:%M\\:%SZ)s:%(meta_date)s")
                add("--parse-metadata" to "post_process:%(epoch>%Y-%m-%dT%H\\:%M\\:%S%z)s:%(meta_creation_time)s")
            }
            add("--parse-metadata" to "%(webpage_url,original_url)s:%(meta_comment)s")
            if (options.contentType == DownloadContentType.AUDIO) {
                add("--parse-metadata" to "%(album,playlist,title)s:%(meta_album)s")
                add("--parse-metadata" to "%(track_number,playlist_index)d:%(meta_track)s")
            }
            add("--embed-metadata" to null)
            add("--no-embed-info-json" to null)
            if (options.contentType == DownloadContentType.VIDEO) add("--embed-chapters" to null)
        }
        if (options.embedThumbnail && options.contentType == DownloadContentType.AUDIO) {
            add("--embed-thumbnail" to null)
            add("--convert-thumbnails" to "jpg")
            add("--postprocessor-args" to "ThumbnailsConvertor+ffmpeg_o:-q:v 1")
            // ID3v2.3 stores only the year when artwork is attached. Keep the full timestamp.
            add("--postprocessor-args" to "EmbedThumbnail+ffmpeg_o:-id3v2_version 4")
        }
    }

    fun cropCommand(ffmpeg: File, temporaryCover: File): String {
        // before_dl runs after thumbnails are written. Crop even already-JPEG artwork,
        // which yt-dlp's thumbnail converter otherwise skips. Template q quotes remote paths.
        val executable = shellQuote(ffmpeg.absolutePath)
        val output = shellQuote(temporaryCover.absolutePath)
        return "before_dl:cover=%(thumbnails.-1.filepath|)q; if [ -f \"\$cover\" ]; then " +
            "$executable -nostdin -y -i \"\$cover\" -vf \"crop='min(iw,ih)':'min(iw,ih)'\" " +
            "-frames:v 1 -q:v 1 $output && mv $output \"\$cover\"; fi"
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"
}
