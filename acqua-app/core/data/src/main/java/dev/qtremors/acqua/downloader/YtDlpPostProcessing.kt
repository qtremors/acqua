package dev.qtremors.acqua.downloader

import java.io.File

internal object YtDlpPostProcessing {
    fun options(options: YtDlpDownloadOptions): List<Pair<String, String?>> = buildList {
        val audio = options.contentType == DownloadContentType.AUDIO
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
        if (audio && options.embedThumbnail) {
            add("--embed-thumbnail" to null)
            // Keep cropped artwork lossless.
            val losslessArtwork = options.cropArtwork
            add("--convert-thumbnails" to if (losslessArtwork) "png" else "jpg")
            if (!losslessArtwork) {
                add("--postprocessor-args" to "ThumbnailsConvertor+ffmpeg_o:-q:v 1")
            }
            // ID3v2.3 stores only the year when artwork is attached. Keep the full timestamp.
            add("--postprocessor-args" to "EmbedThumbnail+ffmpeg_o:-id3v2_version 4")
        }
    }

    fun cropCommand(ffmpeg: File, temporaryCover: File): String {
        val executable = shellQuote(ffmpeg.absolutePath)
        val output = shellQuote(temporaryCover.absolutePath)
        // The converter runs first. Iterate actual files so thumbnail fallback works and
        // already-PNG artwork is cropped too. Stop on failure rather than embed a bad crop.
        return "before_dl:for cover in %(thumbnails.:.filepath|)#q; do " +
            "if [ -f \"\$cover\" ]; then " +
            "$executable -nostdin -loglevel error -y -i \"\$cover\" " +
            "-vf \"crop='min(iw,ih)':'min(iw,ih)':exact=1,setsar=1\" " +
            "-frames:v 1 -c:v png -update 1 $output && mv $output \"\$cover\" || exit 1; fi; done"
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"
}
