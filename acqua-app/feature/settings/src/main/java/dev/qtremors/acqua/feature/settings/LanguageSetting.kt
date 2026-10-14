package dev.qtremors.acqua.feature.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.settings.AppLanguages
import dev.qtremors.acqua.ui.components.SettingsCardContainer

@Composable
internal fun LanguageSetting() {
    val context = LocalContext.current
    SettingsCardContainer(index = 4, count = 5) {
        AppLanguageButton(AppLanguages.selected(context)) { tag -> activity(context)?.let { AppLanguages.select(it, tag) } }
    }
}

@Composable
internal fun AppLanguageButton(selected: String, onSelect: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text(stringResource(R.string.app_language_summary, stringResource(languageLabel(selected)))) }
    if (open) {
        var draft by rememberSaveable { mutableStateOf(selected) }
        AlertDialog(onDismissRequest = { open = false }, title = { Text(stringResource(R.string.app_language)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                Text(stringResource(R.string.app_language_review_hint))
                AppLanguages.supportedTags.forEach { tag ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(draft == tag, role = Role.RadioButton, onClick = { draft = tag }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(draft == tag, onClick = null)
                        Text(stringResource(languageLabel(tag)))
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { onSelect(draft); open = false }) { Text(stringResource(R.string.done)) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } })
    }
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
