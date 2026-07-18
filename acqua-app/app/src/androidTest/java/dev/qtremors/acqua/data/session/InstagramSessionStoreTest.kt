package dev.qtremors.acqua.data.session

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstagramSessionStoreTest {
    private val store = InstagramSessionStore(ApplicationProvider.getApplicationContext())

    @Before fun setUp() = store.clear()
    @After fun tearDown() = store.clear()

    @Test
    fun encryptedSessionRoundTrips() {
        val session = SavedInstagramSession("sessionid=secret; csrftoken=token", "AcquaTest")
        store.save(session)
        assertEquals(session, store.load())
    }
}
