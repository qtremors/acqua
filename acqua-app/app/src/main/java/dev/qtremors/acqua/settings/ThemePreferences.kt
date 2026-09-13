package dev.qtremors.acqua.settings

import dev.qtremors.acqua.ui.theme.AccentColor
import dev.qtremors.acqua.ui.theme.ThemeMode
import dev.qtremors.acqua.ui.theme.ThemePreset
import dev.qtremors.acqua.ui.theme.ThemeState

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "acqua_theme_prefs")

class ThemePreferences(private val context: Context) {

    companion object {
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val ACCENT_COLOR_KEY = stringPreferencesKey("accent_color")
        val HARMONIZE_COLORS_KEY = booleanPreferencesKey("harmonize_colors")
        val VIBRATIONS_ENABLED_KEY = booleanPreferencesKey("vibrations_enabled")
        val THEME_PRESET_KEY = stringPreferencesKey("theme_preset")
        val CUSTOM_PRIMARY_KEY = stringPreferencesKey("custom_primary_hex")
        val CUSTOM_BACKGROUND_KEY = stringPreferencesKey("custom_bg_hex")
    }

    val themeState: Flow<ThemeState> = context.themeDataStore.data.map { preferences ->
        val themeModeStr = preferences[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name
        val accentColorStr = preferences[ACCENT_COLOR_KEY] ?: AccentColor.DYNAMIC.name
        val harmonize = preferences[HARMONIZE_COLORS_KEY] ?: true
        val vibrations = preferences[VIBRATIONS_ENABLED_KEY] ?: true
        val themePresetStr = preferences[THEME_PRESET_KEY] ?: ThemePreset.NONE.name
        val customPrimary = preferences[CUSTOM_PRIMARY_KEY] ?: "#BD93F9"
        val customBackground = preferences[CUSTOM_BACKGROUND_KEY] ?: "#282A36"

        ThemeState(
            themeMode = ThemeMode.entries.find { it.name == themeModeStr } ?: ThemeMode.SYSTEM,
            accentColor = AccentColor.entries.find { it.name == accentColorStr } ?: AccentColor.DYNAMIC,
            harmonizeColors = harmonize,
            vibrationsEnabled = vibrations,
            themePreset = ThemePreset.entries.find { it.name == themePresetStr } ?: ThemePreset.NONE,
            customPrimaryColorHex = customPrimary,
            customBackgroundColorHex = customBackground
        )
    }

    suspend fun saveThemeState(state: ThemeState) {
        context.themeDataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = state.themeMode.name
            preferences[ACCENT_COLOR_KEY] = state.accentColor.name
            preferences[HARMONIZE_COLORS_KEY] = state.harmonizeColors
            preferences[VIBRATIONS_ENABLED_KEY] = state.vibrationsEnabled
            preferences[THEME_PRESET_KEY] = state.themePreset.name
            preferences[CUSTOM_PRIMARY_KEY] = state.customPrimaryColorHex
            preferences[CUSTOM_BACKGROUND_KEY] = state.customBackgroundColorHex
        }
    }
}
