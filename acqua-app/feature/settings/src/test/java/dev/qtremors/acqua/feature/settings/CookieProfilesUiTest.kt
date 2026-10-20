package dev.qtremors.acqua.feature.settings

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.data.session.BrowserIdentityStore
import dev.qtremors.acqua.data.session.CookieProfile
import dev.qtremors.acqua.data.session.CookieProfileCipher
import dev.qtremors.acqua.data.session.CookieProfileStore
import dev.qtremors.acqua.data.session.SessionCookie
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CookieProfilesUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = CookieProfileStore(context, object : CookieProfileCipher {
        override fun encrypt(value: String) = value.reversed()
        override fun decrypt(value: String) = value.reversed()
        override fun erase() = Unit
    })
    private var capturedAt = System.currentTimeMillis()
    @Before fun reset() {
        context.getSharedPreferences("cookie_profiles_v1", Context.MODE_PRIVATE).edit().clear().commit()
        BrowserIdentityStore(context).record("https://example.com/private", "Observed Browser UA")
    }

    @Test fun `capture authorization and drafts restore before explicit save refresh and removal`() {
        var captures = 0
        val restore = StateRestorationTester(compose)
        restore.setContent { MaterialTheme { CookieProfilesSection(store) { name, url, id ->
            captures++; profile(name, url, id!!)
        } } }
        compose.onNodeWithText("Manage saved sessions").performClick()
        compose.onNodeWithText("Save browser session").performScrollTo().performClick()
        compose.onNodeWithTag("cookie-profile-name").performTextReplacement("Fixture account")
        compose.onNodeWithText("Save authorized session").assertIsNotEnabled()
        compose.onNodeWithText("I authorize these cookies for this website and its subdomains.").performScrollTo().performClick()
        restore.emulateSavedInstanceStateRestore()
        assertEquals(0, captures)
        compose.onNodeWithText("Save authorized session").assertIsEnabled().performClick()
        compose.waitUntil(5000) { store.all().size == 1 }
        assertEquals(1, captures)
        val saved = store.all().single()
        compose.onNodeWithText("Refresh from browser").performScrollTo().performClick()
        compose.onNodeWithText("Save authorized session").assertIsNotEnabled()
        compose.onNodeWithText("I authorize these cookies for this website and its subdomains.").performScrollTo().performClick()
        compose.onNodeWithText("Save authorized session").performClick()
        compose.waitUntil(5000) { store.get(saved.id!!).capturedAt > saved.capturedAt }
        assertEquals(saved.id, store.all().single().id)
        compose.onNodeWithText("Remove session").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, store.all().size)
        compose.onNodeWithText("Remove session").performScrollTo().performClick()
        compose.onNode(hasText("Remove session") and hasClickAction()).performClick()
        compose.waitUntil(5000) { store.all().isEmpty() }
    }

    @Test fun `export explains the credential document before opening the file picker`() {
        val profile = profile("Fixture", "https://example.com/private", java.util.UUID.randomUUID().toString())
        store.put(profile)
        compose.setContent { MaterialTheme { CookieProfilesSection(store) } }
        compose.onNodeWithText("Manage saved sessions").performClick()
        compose.onNodeWithText("Export session").performScrollTo().performClick()
        compose.onNodeWithText("This export contains sign-in cookies", substring = true).assertExists()
        compose.onNodeWithText("unencrypted JSON", substring = true).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(profile, store.get(profile.id!!))
    }

    private fun profile(name: String, url: String, id: String) = CookieProfile(id, name, url, "Observed Browser UA", ++capturedAt,
        listOf(SessionCookie("sid", "fixture", "example.com", true, "/", capturedAt + 86400000, true, true))).validated()
}
