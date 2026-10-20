package dev.qtremors.acqua.auth

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import dev.qtremors.acqua.core.data.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrowserActivityTest {
    @Before fun disableAutomaticNetworkUpdates() {
        dev.qtremors.acqua.data.settings.AppSettingsRepository(
            androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        ).setAutoUpdateYtDlp(false)
    }

    @Test
    fun `renderer crash closes the browser and cleans up the dead view`() {
        verifyRendererTermination(didCrash = true)
    }

    @Test
    fun `renderer memory reclamation closes the browser and cleans up the dead view`() {
        verifyRendererTermination(didCrash = false)
    }

    @Test
    fun `page extraction callback is ignored after renderer termination`() {
        val controller = Robolectric.buildActivity(BrowserActivity::class.java).setup()
        val activity = controller.get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val browser = requireNotNull(findView(root) { it is WebView }) as WebView
        val browserShadow = shadowOf(browser)
        val download = requireNotNull(findView(root) {
            it.contentDescription == activity.getString(R.string.download)
        })
        download.performClick()
        val callback = requireNotNull(browserShadow.lastEvaluatedJavascriptCallback)

        assertTrue(browser.webViewClient.onRenderProcessGone(browser, termination(didCrash = true)))
        callback.onReceiveValue("\"https://example.com/media\"")

        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(activity.getString(R.string.browser_renderer_stopped), ShadowToast.getTextOfLatestToast())
        controller.pause().stop().destroy()
    }

    @Test
    fun `normal browser teardown removes and destroys the view`() {
        val controller = Robolectric.buildActivity(BrowserActivity::class.java).setup()
        val browser = requireNotNull(findView(controller.get().findViewById(android.R.id.content)) {
            it is WebView
        }) as WebView

        controller.pause().stop().destroy()

        assertNull(browser.parent)
        assertTrue(shadowOf(browser).wasDestroyCalled())
    }

    private fun verifyRendererTermination(didCrash: Boolean) {
        val controller = Robolectric.buildActivity(BrowserActivity::class.java).setup()
        val activity = controller.get()
        val browser = requireNotNull(findView(activity.findViewById(android.R.id.content)) {
            it is WebView
        }) as WebView

        assertTrue(browser.webViewClient.onRenderProcessGone(browser, termination(didCrash)))

        assertTrue(activity.isFinishing)
        assertNull(browser.parent)
        assertTrue(shadowOf(browser).wasDestroyCalled())
        assertEquals(activity.getString(R.string.browser_renderer_stopped), ShadowToast.getTextOfLatestToast())
        controller.saveInstanceState(Bundle()).pause().stop().destroy()
    }

    @Suppress("DEPRECATION")
    private fun termination(didCrash: Boolean) = object : RenderProcessGoneDetail() {
        override fun didCrash(): Boolean = didCrash
        override fun rendererPriorityAtExit(): Int = WebView.RENDERER_PRIORITY_IMPORTANT
    }

    private fun findView(root: View, predicate: (View) -> Boolean): View? {
        if (predicate(root)) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findView(root.getChildAt(index), predicate)?.let { return it }
            }
        }
        return null
    }
}
