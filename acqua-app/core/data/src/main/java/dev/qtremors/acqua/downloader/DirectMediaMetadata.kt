package dev.qtremors.acqua.downloader

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Writes tags by copying streams; it never re-encodes audio or video. */
internal object DirectMediaMetadata {
    suspend fun write(context: Context, source: File, output: File, sourceUrl: String, timestamp: Long?) {
        FFmpeg.getInstance().init(context.applicationContext)
        val nativeDirectory = context.applicationInfo.nativeLibraryDir
        val command = listOf(File(nativeDirectory, "libffmpeg.so").absolutePath) + arguments(source, output, sourceUrl, timestamp)
        val builder = ProcessBuilder(command).redirectErrorStream(true)
        builder.environment()["LD_LIBRARY_PATH"] = "$nativeDirectory:${File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib").absolutePath}"
        val process = builder.start()
        suspendCancellableCoroutine<Unit> { continuation ->
            continuation.invokeOnCancellation { process.destroy() }
            thread(name = "acqua-media-metadata") {
                try {
                    // Drain output without retaining remote paths or unbounded FFmpeg logs.
                    process.inputStream.use { stream ->
                        val buffer = ByteArray(4_096)
                        while (stream.read(buffer) >= 0) Unit
                    }
                    check(process.waitFor() == 0 && output.length() > 0L) { "Could not embed download metadata." }
                    if (continuation.isActive) continuation.resume(Unit)
                } catch (error: Throwable) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                } finally {
                    process.destroy()
                }
            }
        }
    }

    fun arguments(source: File, output: File, sourceUrl: String, timestamp: Long?): List<String> = buildList {
        addAll(listOf("-nostdin", "-hide_banner", "-loglevel", "error", "-y", "-i", source.absolutePath,
            "-map", "0", "-c", "copy", "-map_metadata", "0", "-metadata", "comment=$sourceUrl"))
        timestamp?.let {
            val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(it))
            addAll(listOf("-metadata", "date=$date", "-metadata", "creation_time=$date"))
        }
        if (output.extension.equals("mp3", ignoreCase = true)) addAll(listOf("-id3v2_version", "4"))
        add(output.absolutePath)
    }
}
