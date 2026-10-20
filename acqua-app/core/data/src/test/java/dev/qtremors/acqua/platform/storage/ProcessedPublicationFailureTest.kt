package dev.qtremors.acqua.platform.storage

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.data.history.HistoryRepository
import dev.qtremors.acqua.data.history.HistoryWriter
import dev.qtremors.acqua.data.network.HttpMediaClient
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.downloader.DownloadContentType
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [ProcessedPublicationFailureTest.TestFileProvider::class],
    instrumentedPackages = ["androidx.core.content"])
class ProcessedPublicationFailureTest {
    @get:Rule val temporary = TemporaryFolder()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val history = HistoryRepository(context)
    private val media = ResolvedMedia("https://example.com/audio", MediaKind.AUDIO, downloadName = "Saved")
    private val bytes = ByteArray(32_768) { (it % 127).toByte() }

    @Test fun `mid copy storage failure deletes only the new output on both storage backends`() = forBackends { fixture ->
        var written = 0
        val storage = fixture.storage(history) { _, output ->
            output.write(bytes, 0, 13)
            written = 13
            throw IOException("Injected storage full")
        }
        assertThrows(IOException::class.java) { runBlocking { fixture.save(storage) } }
        assertEquals(13, written)
        fixture.assertOnlyExistingFile()
        assertTrue(history.load().isEmpty())
    }

    @Test fun `failed history insertion removes the new file on both storage backends`() = forBackends { fixture ->
        val writer = HistoryWriter { throw SQLiteException("Injected History insertion failure") }
        assertThrows(SQLiteException::class.java) { runBlocking { fixture.save(fixture.storage(writer)) } }
        fixture.assertOnlyExistingFile()
        assertTrue(history.load().isEmpty())
    }

    @Test fun `successful publication preserves existing files and creates one History row`() = forBackends { fixture ->
        val uri = runBlocking { fixture.save(fixture.storage(history)) }
        val rows = history.load()
        assertEquals(1, rows.size)
        assertEquals(uri.toString(), rows.single().fileUri)
        assertEquals(bytes.size.toLong(), rows.single().sizeBytes)
        assertEquals(2, fixture.directory.listFiles()!!.size)
        assertEquals("previous download", fixture.existing.readText())
        val saved = fixture.directory.listFiles()!!.single { it != fixture.existing }
        assertArrayEquals(bytes, saved.readBytes())
    }

    @Test fun `concurrent legacy publication reserves unique names without overwriting completed files`() = forBackends { fixture ->
        if (!fixture.legacy) return@forBackends
        val uris = runBlocking {
            val storage = fixture.storage(HistoryWriter { 0L })
            (1..8).map { async(Dispatchers.IO) { fixture.save(storage) } }.awaitAll()
        }
        assertEquals(8, uris.distinct().size)
        val saved = fixture.directory.listFiles()!!.filter { it != fixture.existing }
        assertEquals(8, saved.size)
        saved.forEach { assertArrayEquals(bytes, it.readBytes()) }
        assertEquals("previous download", fixture.existing.readText())
    }

    private fun forBackends(verify: (Fixture) -> Unit) {
        for (legacy in listOf(false, true)) {
            val sdk = Build.VERSION.SDK_INT
            val fixture = Fixture(legacy)
            try {
                if (legacy) ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 28)
                history.clear()
                verify(fixture)
            } finally {
                ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk)
                fixture.directory.listFiles()?.forEach { it.delete() }
                fixture.directory.delete()
            }
        }
    }

    private inner class Fixture(val legacy: Boolean) {
        val folderName = "AcquaPublication_${UUID.randomUUID()}"
        val settings = AppSettingsRepository(context).apply { setBaseFolder(folderName); setCategorizeMedia(false) }
        val directory = if (legacy) File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), folderName)
            .apply { check(mkdirs() || isDirectory) } else temporary.newFolder()
        val existing = File(directory, "Saved.m4a").apply { writeText("previous download") }
        val source = temporary.newFile("${UUID.randomUUID()}.m4a").apply { writeBytes(bytes) }
        val provider = DownloadsProvider(directory, existing)

        init {
            if (!legacy) {
                provider.attachInfo(context, ProviderInfo().apply { authority = "media" })
                ShadowContentResolver.registerProviderInternal("media", provider)
            }
        }

        fun storage(writer: HistoryWriter, copy: ((File, OutputStream) -> Unit)? = null): MediaStorage =
            if (copy == null) MediaStorage(context, writer, settings, HttpMediaClient())
            else MediaStorage(context, writer, settings, HttpMediaClient(), copy)

        suspend fun save(storage: MediaStorage): Uri = storage.saveProcessedFile(source, media,
            DownloadContentType.AUDIO, "https://example.com/source")

        fun assertOnlyExistingFile() {
            assertEquals(listOf(existing.name), directory.listFiles()!!.map { it.name })
            assertEquals("previous download", existing.readText())
            if (!legacy) {
                assertEquals(1, provider.rows.size)
                PendingMediaStoreRegistry.cleanup(context)
                assertEquals(1, provider.rows.size)
            }
        }
    }

    // Keep URI publication independent of Android path-separator assumptions on the Windows JVM.
    @Implements(value = FileProvider::class, isInAndroidSdk = false)
    class TestFileProvider {
        companion object {
            @JvmStatic
            @Implementation
            fun getUriForFile(context: Context, authority: String, file: File): Uri {
                check(file.isFile && authority == "${context.packageName}.fileprovider")
                return Uri.Builder().scheme("content").authority(authority).appendPath(file.name).build()
            }
        }
    }

    private class DownloadsProvider(private val directory: File, existing: File) : ContentProvider() {
        val rows = linkedMapOf(Uri.parse("content://media/external/downloads/existing") to ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, existing.name)
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        })
        private val files = linkedMapOf(rows.keys.single() to existing)
        override fun onCreate() = true
        override fun insert(uri: Uri, values: ContentValues?): Uri {
            val result = Uri.withAppendedPath(uri, UUID.randomUUID().toString())
            rows[result] = ContentValues(values!!)
            val file = File(directory, "${result.lastPathSegment}.m4a")
            files[result] = file
            shadowOf(requireNotNull(context).contentResolver).registerOutputStream(result, file.outputStream())
            return result
        }
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
            rows.getValue(uri).putAll(values!!)
            return 1
        }
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            rows.remove(uri)
            files.remove(uri)?.delete()
            return 1
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            val columns = projection ?: arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
            return MatrixCursor(columns).apply { rows[uri]?.let { row -> addRow(columns.map { row.get(it) }.toTypedArray()) } }
        }
        override fun getType(uri: Uri) = "audio/mp4"
    }
}
