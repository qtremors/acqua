package dev.qtremors.acqua.data.session

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedWebsiteRepositoryTest {
    private val repository = SavedWebsiteRepository(ApplicationProvider.getApplicationContext())

    @Before fun setUp() = repository.clear()
    @After fun tearDown() = repository.clear()

    @Test
    fun websitePersistenceIsSeparateFromBrowserData() {
        repository.save("Example", "https://www.example.com/path", null)
        assertEquals("example.com", repository.load().single().host)
        assertEquals("https://www.example.com", repository.lastOrigin())
        repository.remove(listOf("https://example.com"))
        assertEquals(emptyList<SavedWebsite>(), repository.load())
    }
}
