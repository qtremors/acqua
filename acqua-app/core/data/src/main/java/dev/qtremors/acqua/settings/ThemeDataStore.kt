package dev.qtremors.acqua.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "acqua_theme_prefs")
