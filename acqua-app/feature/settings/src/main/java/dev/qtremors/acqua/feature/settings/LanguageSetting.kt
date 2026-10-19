package dev.qtremors.acqua.feature.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.settings.AppLanguages
import dev.qtremors.acqua.ui.components.SettingsChoiceRow

@Composable
internal fun LanguageSetting() {
    val context = LocalContext.current
    AppLanguageButton(AppLanguages.selected(context)) { tag -> activity(context)?.let { AppLanguages.select(it, tag) } }
}

@Composable
internal fun AppLanguageButton(selected: String, onSelect: (String) -> Unit) {
    val tags = AppLanguages.supportedTags.toList()
    SettingsChoiceRow(stringResource(R.string.app_language), tags.map { stringResource(languageLabel(it)) },
        tags.indexOf(selected).coerceAtLeast(0), { onSelect(tags[it]) }, index = 4, count = 5)
}

private fun languageLabel(tag: String): Int = when (tag) {
    "en" -> R.string.app_language_en
    "es" -> R.string.app_language_es
    "hi" -> R.string.app_language_hi
    "ar" -> R.string.app_language_ar
    else -> R.string.app_language_system
}

private fun activity(context: Context): Activity? {
    var current = context
    repeat(16) {
        if (current is Activity) return current as Activity
        val wrapper = current as? ContextWrapper ?: return null
        if (wrapper.baseContext === current) return null
        current = wrapper.baseContext
    }
    return null
}
