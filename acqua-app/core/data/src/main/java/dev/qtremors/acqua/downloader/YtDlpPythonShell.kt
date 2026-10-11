package dev.qtremors.acqua.downloader

import android.os.Build
import android.util.AtomicFile
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal object YtDlpPythonShell {
    // The bundled Python build hardcodes Termux's shell, which Acqua cannot access.
    private const val TERMUX_SHELL = "/data/data/com.termux/files/usr/bin/sh"
    private const val ANDROID_SHELL = "/system/bin/sh"

    fun repair(libraryDirectory: File) {
        libraryDirectory.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("python3.") }
            .map { File(it, "subprocess.py") }
            .filter { it.isFile || File("${it.path}.bak").isFile }
            .forEach { source ->
                val file = AtomicFile(source)
                val original = file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
                val corrected = original.replace(TERMUX_SHELL, ANDROID_SHELL)
                if (corrected != original) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val temporary = File.createTempFile("subprocess-", ".tmp", source.parentFile)
                        try {
                            temporary.outputStream().use { stream ->
                                stream.write(corrected.toByteArray(Charsets.UTF_8))
                                stream.fd.sync()
                            }
                            Files.move(temporary.toPath(), source.toPath(),
                                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                        } finally {
                            temporary.delete()
                        }
                    } else {
                        val stream = file.startWrite()
                        try {
                            stream.write(corrected.toByteArray(Charsets.UTF_8))
                            file.finishWrite(stream)
                        } catch (error: Throwable) {
                            file.failWrite(stream)
                            throw error
                        }
                    }
                }
            }
    }
}
