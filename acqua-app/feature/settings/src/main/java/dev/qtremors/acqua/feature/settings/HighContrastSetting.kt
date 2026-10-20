package dev.qtremors.acqua.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.ui.components.SettingsSwitchRow

@Composable
internal fun HighContrastSetting(enabled: Boolean, count: Int = 4, onChange: (Boolean) -> Unit) {
    SettingsSwitchRow(stringResource(R.string.high_contrast), enabled, onChange, index = 3, count = count,
        description = stringResource(R.string.settings_contrast_summary), leadingIcon = Icons.Filled.Contrast)
}
