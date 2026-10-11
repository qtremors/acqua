package dev.qtremors.acqua.downloader

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class YtDlpPythonShellTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `bundled subprocess uses Android shell instead of the inaccessible Termux shell`() {
        val library = temporary.newFolder("lib")
        val source = File(library, "python3.12/subprocess.py").apply {
            parentFile!!.mkdirs()
            writeText("if shell:\n    unix_shell = ('/data/data/com.termux/files/usr/bin/sh')\n    args = [unix_shell, '-c'] + args\n")
        }
        YtDlpPythonShell.repair(library)
        assertEquals("if shell:\n    unix_shell = ('/system/bin/sh')\n    args = [unix_shell, '-c'] + args\n", source.readText())
        source.setLastModified(1_000L)
        YtDlpPythonShell.repair(library)
        assertEquals(1_000L, source.lastModified())
    }

    @Test
    fun `an interrupted shell repair recovers the saved Python source`() {
        val library = temporary.newFolder("lib")
        val source = File(library, "python3.12/subprocess.py")
        source.parentFile!!.mkdirs()
        val backup = File("${source.path}.bak").apply {
            writeText("unix_shell = '/data/data/com.termux/files/usr/bin/sh'\n")
        }
        YtDlpPythonShell.repair(library)
        assertEquals("unix_shell = '/system/bin/sh'\n", source.readText())
        assertFalse(backup.exists())
    }

    @Test
    fun `runtime with an already supported shell is left unchanged`() {
        val library = temporary.newFolder("lib")
        val source = File(library, "python3.13/subprocess.py").apply {
            parentFile!!.mkdirs()
            writeText("unix_shell = '/system/bin/sh'\n")
            setLastModified(1_000L)
        }
        YtDlpPythonShell.repair(library)
        assertEquals("unix_shell = '/system/bin/sh'\n", source.readText())
        assertEquals(1_000L, source.lastModified())
    }
}
