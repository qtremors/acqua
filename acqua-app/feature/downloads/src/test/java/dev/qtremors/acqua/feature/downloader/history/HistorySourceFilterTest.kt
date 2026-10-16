package dev.qtremors.acqua.feature.downloader.history

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.MainDispatcherRule
import dev.qtremors.acqua.data.history.HistoryEntry
import dev.qtremors.acqua.data.history.HistoryRepository
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistorySourceFilterTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun `source and metadata searches survive recreation and combine with unavailable files`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = HistoryRepository(context)
        val file = File(context.cacheDir, "history-source-test.mp4").apply { writeText("fixture") }
        repeat(6) { index -> repository.add(HistoryEntry(index.toString(), index.toLong(),
            "https://${if (index % 2 == 0) "one" else "two"}.example/post", "Renamed.mp4", Uri.fromFile(file).toString(),
            true, "video/mp4", file.length(), true, title = "Blue sky", author = "Aster", sourceIdentity = "fixture:$index")) }
        val saved = SavedStateHandle()
        val model = HistoryViewModel(repository, saved, mainDispatcherRule.dispatcher)
        advanceUntilIdle()
        assertEquals(listOf("one.example", "two.example"), model.state.value.sources)
        model.selectSource("one.example")
        model.setQuery("Aster Blue")
        advanceUntilIdle()
        assertEquals(setOf("0", "2", "4"), model.state.value.filteredEntries.map { it.id }.toSet())

        file.delete()
        model.selectFilter(HistoryFilter.UNAVAILABLE)
        advanceUntilIdle()
        assertEquals(3, model.state.value.filteredEntries.size)
        val recreated = HistoryViewModel(repository, saved, mainDispatcherRule.dispatcher)
        advanceUntilIdle()
        assertEquals("one.example", recreated.state.value.sourceHost)
        assertEquals(3, recreated.state.value.filteredEntries.size)
        assertTrue(recreated.state.value.filteredEntries.all { it.author == "Aster" })
        recreated.resetFilters()
        advanceUntilIdle()
        assertNull(recreated.state.value.sourceHost)
        assertEquals(6, recreated.state.value.filteredEntries.size)
    }
}
