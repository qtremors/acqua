package dev.qtremors.acqua.feature.downloader

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.data.session.CookieProfile
import dev.qtremors.acqua.data.session.CookieProfileCipher
import dev.qtremors.acqua.data.session.CookieProfileStore
import dev.qtremors.acqua.data.session.SessionCookie
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions
import dev.qtremors.acqua.ui.components.CookieProfileSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CookieProfileSelectorTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = CookieProfileStore(context, object : CookieProfileCipher {
        override fun encrypt(value: String) = value.reversed()
        override fun decrypt(value: String) = value.reversed()
        override fun erase() = Unit
    })
    private val profile = CookieProfile(java.util.UUID.randomUUID().toString(), "Fixture", "https://example.com/private", "Observed Browser UA", System.currentTimeMillis(),
        listOf(SessionCookie("sid", "fixture", "example.com", true, "/", null, true, true)))
    @Before fun reset() {
        context.getSharedPreferences("cookie_profiles_v1", Context.MODE_PRIVATE).edit().clear().commit()
        AppSettingsRepository(context).setUseBrowserSessions(true)
        store.put(profile)
    }

    @Test fun `session choice restores and applies an identity and snapshot fingerprint only on Done`() {
        var accepted: YtDlpDownloadOptions? = null
        val restore = StateRestorationTester(compose)
        restore.setContent { MaterialTheme { CookieProfileSelector(YtDlpDownloadOptions(), store) { accepted = it } } }
        compose.onNodeWithText("Saved session: Current browser").performClick()
        compose.onNodeWithText("Fixture\nexample.com").performClick()
        restore.emulateSavedInstanceStateRestore()
        assertNull(accepted)
        compose.onNodeWithText("Done").performClick()
        assertEquals(profile.id, accepted!!.cookieProfileId)
        assertEquals(profile.fingerprint, accepted!!.cookieProfileFingerprint)
    }

    @Test fun `refreshed choices are identified and clearing removes both binding fields`() {
        val original = YtDlpDownloadOptions(cookieProfileId = profile.id, cookieProfileFingerprint = profile.fingerprint, embedMetadata = false)
        store.put(profile.copy(cookies = profile.cookies.map { it.copy(value = "refreshed") }))
        var accepted: YtDlpDownloadOptions? = null
        compose.setContent { MaterialTheme { CookieProfileSelector(original, store) { accepted = it } } }
        compose.onNodeWithText("Saved session: Missing or refreshed").performClick()
        compose.onNodeWithText("Current browser").performClick()
        compose.onNodeWithText("Done").performClick()
        assertNull(accepted!!.cookieProfileId)
        assertNull(accepted!!.cookieProfileFingerprint)
        assertEquals(false, accepted!!.embedMetadata)
    }
}
