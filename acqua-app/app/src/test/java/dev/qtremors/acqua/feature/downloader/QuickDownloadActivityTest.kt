package dev.qtremors.acqua.feature.downloader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.MainActivity
import dev.qtremors.acqua.data.onboarding.onboardingDataStore
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.platform.permissions.DownloadPermissionGate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuickDownloadActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setup() { AppSettingsRepository(context).setAutoUpdateYtDlp(false) }

    @Test fun `intake accepts only HTTP media and webpage views and ignores authorization extras`() {
        for (type in listOf(null, "image/jpeg", "audio/mpeg", "video/mp4", "text/html", "application/vnd.apple.mpegurl")) {
            val intent = view("https://example.com/post", type).putExtra(DownloadActivity.EXTRA_URL, "https://attacker.invalid")
                .putExtra(DownloadActivity.EXTRA_MEDIA_JSON, "[{\"url\":\"https://attacker.invalid\",\"requestCookies\":\"private\"}]")
                .putExtra("automatic_download", true)
            assertEquals("https://example.com/post", ExternalDownloadIntake.parse(intent))
        }
        listOf("file:///private/file", "content://media/external/video/media/1", "javascript:alert(1)", "https://user:secret@example.com/file",
            "https://example.com/with space", "https://example.com:65536/file", "https://example.com:0/file",
            "https://example.com/" + "x".repeat(8192)).forEach { assertNull(ExternalDownloadIntake.parse(view(it))) }
        assertNull(ExternalDownloadIntake.parse(view("https://example.com/file", "application/vnd.android.package-archive")))
        assertNull(ExternalDownloadIntake.parse(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "https://example.com/post")))
    }

    @Test fun `malformed cold intake finishes without forwarding another intent`() {
        val controller = Robolectric.buildActivity(QuickDownloadActivity::class.java, view("content://media/1")).create()
        try {
            assertTrue(controller.get().isFinishing)
            assertNull(shadowOf(controller.get()).nextStartedActivity)
        } finally { controller.destroy() }
    }

    @Test fun `warm duplicates and invalid links preserve pending consent while a new source cancels it`() {
        val controller = Robolectric.buildActivity(QuickDownloadActivity::class.java, view("https://example.com/first")).create()
        try {
            val activity = controller.get()
            val gate = ViewModelProvider(activity)[DownloadPermissionGate::class.java]
            var accepted = 0
            gate.hold(true) { accepted++ }
            controller.newIntent(view("https://example.com/first").putExtra("automatic_download", true))
            assertTrue(gate.hasPendingAction)
            controller.newIntent(view("file:///private/file"))
            assertTrue(gate.hasPendingAction)
            controller.newIntent(view("https://example.com/second"))
            assertFalse(gate.hasPendingAction)
            gate.runPendingWithoutNotifications()
            assertEquals(0, accepted)
            val saved = Bundle()
            controller.saveInstanceState(saved)
            assertEquals("https://example.com/second", saved.getString("quick.source"))
            assertFalse(activity.isFinishing)
            assertNull(shadowOf(activity).nextStartedActivity)
        } finally { controller.destroy() }
    }

    @Test fun `latest warm source survives recreation without forwarding untrusted extras`() {
        val first = view("https://example.com/first").putExtra(DownloadActivity.EXTRA_MEDIA_JSON, "malformed")
        val original = Robolectric.buildActivity(QuickDownloadActivity::class.java, first).create()
        original.newIntent(view("https://example.com/second"))
        val saved = Bundle()
        original.saveInstanceState(saved)
        original.destroy()
        val restored = Robolectric.buildActivity(QuickDownloadActivity::class.java, first).create(saved)
        try {
            val reSaved = Bundle()
            restored.saveInstanceState(reSaved)
            assertEquals("https://example.com/second", reSaved.getString("quick.source"))
            assertFalse(reSaved.containsKey(DownloadActivity.EXTRA_MEDIA_JSON))
            assertFalse(reSaved.containsKey("automatic_download"))
            assertFalse(restored.get().isFinishing)
        } finally { restored.destroy() }
    }

    @Test fun `first-time setup receives only the validated link and keeps its ordinary navigation flags`() = runBlocking {
        context.onboardingDataStore.updateData { emptyPreferences() }
        val controller = Robolectric.buildActivity(QuickDownloadActivity::class.java,
            view("https://example.com/post").putExtra(DownloadActivity.EXTRA_MEDIA_JSON, "untrusted").putExtra("automatic_download", true)).setup()
        try {
            val activity = controller.get()
            var forwarded: Intent? = null
            val deadline = System.nanoTime() + 3_000_000_000L
            while (forwarded == null && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                forwarded = shadowOf(activity).nextStartedActivity
                if (forwarded == null) Thread.sleep(10)
            }
            assertEquals(MainActivity::class.java.name, forwarded?.component?.className)
            assertEquals(Intent.ACTION_SEND, forwarded?.action)
            assertEquals("https://example.com/post", forwarded?.getStringExtra(Intent.EXTRA_TEXT))
            assertEquals(setOf(Intent.EXTRA_TEXT), forwarded?.extras?.keySet())
            assertEquals(0, forwarded?.flags)
            assertTrue(activity.isFinishing)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun `manifest view filters are scoped to HTTP and keep the browser-authorized host private`() {
        val manager = context.packageManager
        for (type in listOf(null, "image/jpeg", "audio/mpeg", "video/mp4")) {
            val matches = manager.queryIntentActivities(view("https://example.com/file", type), 0)
            assertTrue(matches.any { it.activityInfo.name == QuickDownloadActivity::class.java.name && it.activityInfo.exported })
        }
        assertFalse(manager.queryIntentActivities(view("content://media/external/images/media/1", "image/jpeg"), 0)
            .any { it.activityInfo.name == QuickDownloadActivity::class.java.name })
        val internal = manager.getActivityInfo(android.content.ComponentName(context, DownloadActivity::class.java), 0)
        assertFalse(internal.exported)
    }

    private fun view(url: String, type: String? = null) = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(url), type)
        addCategory(Intent.CATEGORY_BROWSABLE)
    }
}
