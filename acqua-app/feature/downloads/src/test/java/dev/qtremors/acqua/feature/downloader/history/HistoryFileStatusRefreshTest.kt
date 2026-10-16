package dev.qtremors.acqua.feature.downloader.history

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.MainDispatcherRule
import dev.qtremors.acqua.data.history.HistoryCategory
import dev.qtremors.acqua.data.history.HistoryEntry
import dev.qtremors.acqua.data.history.HistoryFileStatus
import dev.qtremors.acqua.data.history.HistoryPageRequest
import dev.qtremors.acqua.data.history.HistoryRepository
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryFileStatusRefreshTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = HistoryRepository(context)
    private val models = ViewModelStore()

    @Before fun prepare() { repository.clear() }
    @After fun cleanUp() { models.clear(); repository.clear() }

    @Test fun `Unavailable discovers externally deleted known files and removes restored files`() = runTest {
        val file = File(context.cacheDir, "known-photo.jpg").apply { writeText("photo") }
        val entry = entry("known", Uri.fromFile(file).toString())
        repository.add(entry)
        val model = model()
        advanceUntilIdle()
        assertTrue(repository.loadPage(HistoryPageRequest(category = HistoryCategory.UNAVAILABLE)).entries.isEmpty())

        assertTrue(file.delete())
        model.selectFilter(HistoryFilter.UNAVAILABLE)
        advanceUntilIdle()
        assertEquals(listOf(entry), model.state.value.entries)
        assertEquals(1, model.state.value.summary.missing)

        file.writeText("restored")
        model.refresh()
        advanceUntilIdle()
        assertTrue(model.state.value.entries.isEmpty())
        assertFalse(model.state.value.hasMore)
        assertEquals(0, model.state.value.summary.missing)
    }

    @Test fun `status refresh scans equal timestamps across batches and paginates every missing entry`() = runTest {
        val entries = (1..125).map { entry("item%03d".format(it), "file://${context.cacheDir}/missing-$it.jpg") }
        repository.restore(entries)
        repository.updateFileStatuses(entries.associate { it.id to HistoryFileStatus.AVAILABLE })
        val model = model(HistoryFilter.UNAVAILABLE)
        advanceUntilIdle()
        assertEquals(125, model.state.value.summary.missing)
        assertEquals(100, model.state.value.entries.size)
        assertTrue(model.state.value.hasMore)

        model.loadNextPage()
        advanceUntilIdle()
        assertEquals(entries.map { it.id }.toSet(), model.state.value.entries.map { it.id }.toSet())
        assertEquals(125, model.state.value.entries.size)
        assertFalse(model.state.value.hasMore)
        assertFalse(model.state.value.loadFailed)
    }

    @Test fun `refresh removes unavailable entries when provider access returns`() = runTest {
        val file = File(context.cacheDir, "provider-photo.jpg").apply { writeText("photo") }
        val provider = ToggleProvider(file)
        provider.attachInfo(context, ProviderInfo().apply { authority = "history.test" })
        ShadowContentResolver.registerProviderInternal("history.test", provider)
        val entry = entry("provider", "content://history.test/photo")
        repository.add(entry)
        val model = model(HistoryFilter.UNAVAILABLE)
        advanceUntilIdle()
        assertEquals(listOf(entry), model.state.value.entries)
        assertEquals(HistoryFileStatus.UNAVAILABLE, model.state.value.fileStatuses[entry.id])

        provider.allowed = true
        context.contentResolver.openAssetFileDescriptor(Uri.parse(entry.fileUri), "r").use { check(it != null) }
        assertEquals(HistoryFileStatus.AVAILABLE, repository.fileStatus(entry))
        model.refresh()
        advanceUntilIdle()
        assertTrue(model.state.value.entries.isEmpty())
        assertEquals(0, model.state.value.summary.missing)
    }

    private fun model(filter: HistoryFilter = HistoryFilter.MEDIA): HistoryViewModel = HistoryViewModel(
        repository, SavedStateHandle(mapOf("history.filter" to filter.name)), mainDispatcherRule.dispatcher
    ).also { models.put("history", it) }

    private fun entry(id: String, uri: String) = HistoryEntry(
        id, 1L, "https://example.com/$id", "$id.jpg", uri, false, "image/jpeg", 5L, true, sourceHost = "example.com"
    )

    private class ToggleProvider(private val file: File) : ContentProvider() {
        var allowed = false
        override fun onCreate() = true
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (!allowed) throw SecurityException("Provider access revoked")
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun getType(uri: Uri) = "image/jpeg"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
