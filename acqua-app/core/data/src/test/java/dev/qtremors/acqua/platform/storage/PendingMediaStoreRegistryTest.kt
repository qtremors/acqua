package dev.qtremors.acqua.platform.storage

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PendingMediaStoreRegistryTest {
    @Test
    fun `startup cleanup deletes pending rows but preserves finalized rows`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = RecordingMediaProvider()
        provider.attachInfo(context, null)
        ShadowContentResolver.registerProviderInternal("media", provider)
        val pending = Uri.parse("content://media/external/downloads/pending")
        val finalized = Uri.parse("content://media/external/downloads/finalized")
        val completedBeforeRestart = Uri.parse("content://media/external/downloads/completed")

        PendingMediaStoreRegistry.track(context, pending)
        PendingMediaStoreRegistry.track(context, finalized)
        PendingMediaStoreRegistry.track(context, completedBeforeRestart)
        PendingMediaStoreRegistry.complete(context, completedBeforeRestart)

        PendingMediaStoreRegistry.cleanup(context)
        PendingMediaStoreRegistry.cleanup(context)

        assertEquals(listOf(pending), provider.deleted)
    }

    private class RecordingMediaProvider : ContentProvider() {
        val deleted = mutableListOf<Uri>()

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor = cursor(uri)

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            queryArgs: Bundle?,
            cancellationSignal: CancellationSignal?
        ): Cursor = cursor(uri)

        private fun cursor(uri: Uri) = MatrixCursor(
            arrayOf(MediaStore.MediaColumns.IS_PENDING)
        ).apply {
            addRow(arrayOf(if (uri.lastPathSegment == "pending") 1 else 0))
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            deleted += uri
            return 1
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int = 0
    }
}
