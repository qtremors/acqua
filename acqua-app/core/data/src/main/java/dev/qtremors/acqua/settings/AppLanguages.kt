package dev.qtremors.acqua.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object AppLanguages {
    val supportedTags: List<String> = listOf("", "en", "es", "hi", "ar")
    private const val PREFERENCES = "app_language"
    private const val KEY = "tag"

    fun selected(context: Context): String {
        val tag = if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java)?.applicationLocales?.toLanguageTags().orEmpty().substringBefore(',')
        else context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        return tag.takeIf { it in supportedTags }.orEmpty()
    }

    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) {
            val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            if (!preferences.getBoolean("migrated", false)) {
                val manager = context.getSystemService(LocaleManager::class.java)
                val previous = preferences.getString(KEY, "").orEmpty()
                if (manager != null && manager.applicationLocales.isEmpty && previous.isNotEmpty() && previous in supportedTags) {
                    manager.applicationLocales = LocaleList.forLanguageTags(previous)
                }
                check(preferences.edit().putBoolean("migrated", true).commit())
            }
            return context
        }
        val tag = selected(context)
        if (tag.isEmpty()) return context
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(locale)); setLayoutDirection(locale)
        }
        return context.createConfigurationContext(configuration)
    }

    @Suppress("DEPRECATION")
    fun set(context: Context, tag: String) {
        require(tag in supportedTags) { "Unsupported app language." }
        if (Build.VERSION.SDK_INT >= 33) {
            requireNotNull(context.getSystemService(LocaleManager::class.java)).applicationLocales = LocaleList.forLanguageTags(tag)
        } else {
            check(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(KEY, tag).commit())
            val locales = if (tag.isEmpty()) Resources.getSystem().configuration.locales else LocaleList.forLanguageTags(tag)
            Locale.setDefault(locales[0])
            val resources = context.applicationContext.resources
            val configuration = Configuration(resources.configuration).apply { setLocales(locales); setLayoutDirection(locales[0]) }
            resources.updateConfiguration(configuration, resources.displayMetrics)
        }
    }

    fun select(activity: Activity, tag: String) {
        if (tag == selected(activity)) return
        set(activity, tag)
        if (Build.VERSION.SDK_INT < 33) activity.recreate()
    }
}
