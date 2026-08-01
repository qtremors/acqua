package dev.qtremors.acqua.downloader

import android.content.Context
import java.io.File

internal object YtDlpTemporaryFiles {
    private const val TASK_DIRECTORY = "yt-dlp"
    private const val COOKIE_DIRECTORY = "yt-dlp-cookies"
    private const val STALE_AFTER_MS = 24L * 60L * 60L * 1000L

    fun prepareTaskDirectory(context: Context, processId: String): File =
        File(context.cacheDir, "$TASK_DIRECTORY/$processId").apply {
            deleteRecursively()
            check(mkdirs() || isDirectory) { "Could not prepare temporary media storage." }
        }

    fun createCookieFile(context: Context, rows: List<String>): File {
        val directory = File(context.cacheDir, COOKIE_DIRECTORY).apply {
            check(mkdirs() || isDirectory) { "Could not prepare temporary session storage." }
        }
        return File.createTempFile("cookies-", ".txt", directory).apply {
            setReadable(false, false)
            setWritable(false, false)
            check(setReadable(true, true) && setWritable(true, true)) {
                "Could not secure the temporary session file."
            }
            writeText((listOf("# Netscape HTTP Cookie File") + rows).joinToString("\n"))
        }
    }

    fun cleanupAbandoned(context: Context, now: Long = System.currentTimeMillis()) {
        listOf(TASK_DIRECTORY, COOKIE_DIRECTORY).forEach { directoryName ->
            val directory = File(context.cacheDir, directoryName)
            directory.listFiles().orEmpty().forEach { file ->
                if (now - file.lastModified() >= STALE_AFTER_MS) file.deleteRecursively()
            }
            if (directory.listFiles().isNullOrEmpty()) directory.delete()
        }
    }
}
