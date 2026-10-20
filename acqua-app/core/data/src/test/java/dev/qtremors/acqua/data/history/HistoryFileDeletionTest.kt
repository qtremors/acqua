package dev.qtremors.acqua.data.history

import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.app.RemoteAction
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryFileDeletionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val provider = DeletionProvider()

    @Before fun setup() {
        provider.attachInfo(context, ProviderInfo().apply { authority = "deletion.test" })
        ShadowContentResolver.registerProviderInternal("deletion.test", provider)
        ShadowContentResolver.registerProviderInternal("media", provider)
    }

    @Test fun `owned provider files delete without removing their History records`() {
        val repository = HistoryRepository(context)
        repository.clear()
        val entry = entry("content://deletion.test/files/1")
        repository.add(entry)
        assertEquals(HistoryFileDeletionStatus.DELETED, status(HistoryFileDeleter(context).delete(entry)))
        assertFalse(provider.exists)
        assertEquals(entry.fileUri, repository.find(entry.id)!!.fileUri)
    }

    @Test fun `missing denied failed and directory outcomes stay distinct`() {
        val deleter = HistoryFileDeleter(context)
        val entry = entry("content://deletion.test/files/1")
        provider.exists = false
        assertEquals(HistoryFileDeletionStatus.MISSING, status(deleter.delete(entry)))
        provider.exists = true
        provider.ignoreDelete = true
        assertEquals(HistoryFileDeletionStatus.FAILED, status(deleter.delete(entry)))
        provider.error = SecurityException("Permission denied")
        assertEquals(HistoryFileDeletionStatus.DENIED, status(deleter.delete(entry)))
        provider.error = FileNotFoundException("Missing")
        assertEquals(HistoryFileDeletionStatus.MISSING, status(deleter.delete(entry)))
        provider.error = null
        provider.directory = true
        provider.deletes = 0
        assertEquals(HistoryFileDeletionStatus.DENIED, status(deleter.delete(entry)))
        assertEquals(0, provider.deletes)
        provider.directory = false
        provider.rows = 2
        assertEquals(HistoryFileDeletionStatus.DENIED, status(deleter.delete(entry)))
        assertEquals(0, provider.deletes)
    }

    @Test fun `Android media permission requests cover one item and approval is verified without another delete`() {
        val entry = entry("content://media/external/video/media/123")
        val requested = mutableListOf<Uri>()
        val sender = pending().intentSender
        val deleter = HistoryFileDeleter(context) { uri -> requested += uri; sender }
        provider.error = SecurityException("Not owned")
        val consent = deleter.delete(entry) as HistoryFileDeletionAttempt.Consent
        assertEquals(sender, consent.sender)
        assertTrue(consent.deletesOnApproval)
        assertEquals(listOf(Uri.parse(entry.fileUri)), requested)
        assertEquals(HistoryFileDeletionStatus.DENIED, status(deleter.delete(entry, allowConsent = false)))
        assertEquals(1, requested.size)
        assertEquals(HistoryFileDeletionStatus.DENIED, deleter.verifyApprovedDeletion(entry))
        provider.error = null
        assertEquals(HistoryFileDeletionStatus.FAILED, deleter.verifyApprovedDeletion(entry))
        provider.exists = false
        assertEquals(HistoryFileDeletionStatus.DELETED, deleter.verifyApprovedDeletion(entry))
        val deletesBefore = provider.deletes
        assertEquals(HistoryFileDeletionStatus.DENIED, status(deleter.delete(entry.copy(fileUri = "content://media/external/video/media"))))
        assertEquals(deletesBefore, provider.deletes)
    }

    @Test fun `Android 10 recoverable permission requires retry after approval`() {
        val previous = Build.VERSION.SDK_INT
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 29)
        try {
            val action = RemoteAction(Icon.createWithResource(context, android.R.drawable.ic_menu_delete), "Delete", "Allow deletion", pending())
            provider.error = RecoverableSecurityException(SecurityException("Not owned"), "Allow deletion", action)
            val entry = entry("content://media/external/video/media/123")
            val deleter = HistoryFileDeleter(context)
            val consent = deleter.delete(entry) as HistoryFileDeletionAttempt.Consent
            assertEquals(action.actionIntent.intentSender, consent.sender)
            assertFalse(consent.deletesOnApproval)
            provider.error = null
            assertEquals(HistoryFileDeletionStatus.DELETED, status(deleter.delete(entry, allowConsent = false)))
        } finally { ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", previous) }
    }

    @Test fun `legacy deletion is restricted to disposable files inside Downloads`() {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val folder = File(downloads, "acqua-deletion-${UUID.randomUUID()}").apply { mkdirs() }
        val file = File(folder, "fixture.mp4").apply { writeText("disposable") }
        val outside = File(context.cacheDir, "keep-${UUID.randomUUID()}").apply { writeText("keep") }
        try {
            val deleter = HistoryFileDeleter(context)
            assertEquals(HistoryFileDeletionStatus.DENIED, status(deleter.delete(entry(Uri.fromFile(outside).toString()))))
            assertTrue(outside.isFile)
            assertEquals(HistoryFileDeletionStatus.DENIED, status(deleter.delete(entry(Uri.fromFile(folder).toString()))))
            assertEquals(HistoryFileDeletionStatus.DELETED, status(deleter.delete(entry(Uri.fromFile(file).toString()))))
            assertEquals(HistoryFileDeletionStatus.MISSING, status(deleter.delete(entry(Uri.fromFile(file).toString()))))
            assertEquals(HistoryFileDeletionStatus.SKIPPED, status(deleter.delete(entry("").copy(isDownloaded = false))))
        } finally { file.delete(); folder.delete(); outside.delete() }
    }

    private fun pending() = PendingIntent.getActivity(context, 0, Intent("deletion.permission"), PendingIntent.FLAG_IMMUTABLE)
    private fun status(attempt: HistoryFileDeletionAttempt) = (attempt as HistoryFileDeletionAttempt.Finished).status
    private fun entry(uri: String) = HistoryEntry(UUID.randomUUID().toString(), 1, "https://example.com/post", "fixture.mp4", uri, true, "video/mp4", 10, true)

    class DeletionProvider : ContentProvider() {
        var exists = true
        var ignoreDelete = false
        var directory = false
        var error: Exception? = null
        var deletes = 0
        var rows = 1
        override fun onCreate() = true
        override fun getType(uri: Uri) = if (directory) DocumentsContract.Document.MIME_TYPE_DIR else "video/mp4"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
            error?.let { throw it }
            return MatrixCursor(arrayOf("_id")).apply { if (exists) repeat(rows) { addRow(arrayOf(it)) } }
        }
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int {
            deletes++
            error?.let { throw it }
            if (!exists || ignoreDelete) return 0
            exists = false
            return 1
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    }
}
