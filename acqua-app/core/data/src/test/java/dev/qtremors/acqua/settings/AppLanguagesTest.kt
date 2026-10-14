package dev.qtremors.acqua.settings

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.view.View
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.core.data.R
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppLanguagesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val originalLocale = Locale.getDefault()
    private val originalConfiguration = Configuration(context.resources.configuration)

    @Before fun reset() { context.getSharedPreferences("app_language", Context.MODE_PRIVATE).edit().clear().commit() }
    @After fun restore() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 35)
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.getEmptyLocaleList()
        context.getSharedPreferences("app_language", Context.MODE_PRIVATE).edit().clear().commit()
        Locale.setDefault(originalLocale)
        @Suppress("DEPRECATION") context.resources.updateConfiguration(originalConfiguration, context.resources.displayMetrics)
    }

    @Test fun `platform choice and reset follow the Android language manager`() {
        for (tag in AppLanguages.supportedTags) {
            AppLanguages.set(context, tag)
            assertEquals(tag, AppLanguages.selected(context))
            assertEquals(tag, context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags())
        }
        assertThrows(IllegalArgumentException::class.java) { AppLanguages.set(context, "--exec") }
    }

    @Test fun `legacy selections persist across new contexts with RTL and English fallback`() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 28)
        for ((tag, expected) in listOf("es" to "Descargar", "hi" to "डाउनलोड करें", "ar" to "تنزيل", "en" to "Download")) {
            AppLanguages.set(context, tag)
            val wrapped = AppLanguages.wrap(context.createConfigurationContext(Configuration(originalConfiguration)))
            assertEquals(tag, AppLanguages.selected(wrapped))
            assertEquals(expected, wrapped.getString(R.string.download))
            assertEquals(if (tag == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR, wrapped.resources.configuration.layoutDirection)
            assertEquals("Failure details", wrapped.getString(R.string.download_diagnostics))
            for (count in listOf(0, 1, 2, 3, 11, 100)) assertTrue(wrapped.resources.getQuantityString(R.plurals.active_downloads, count, count)
                .contains(String.format(Locale.forLanguageTag(tag), "%d", count)))
        }
        AppLanguages.set(context, "")
        assertEquals("", AppLanguages.selected(context))
    }

    @Test fun `legacy language migrates once and never replaces an Android choice or later reset`() {
        context.getSharedPreferences("app_language", Context.MODE_PRIVATE).edit().putString("tag", "hi").commit()
        AppLanguages.wrap(context)
        assertEquals("hi", AppLanguages.selected(context))
        AppLanguages.set(context, "")
        AppLanguages.wrap(context)
        assertEquals("", AppLanguages.selected(context))
        reset()
        AppLanguages.set(context, "es")
        context.getSharedPreferences("app_language", Context.MODE_PRIVATE).edit().putString("tag", "ar").commit()
        AppLanguages.wrap(context)
        assertEquals("es", AppLanguages.selected(context))
    }
}
