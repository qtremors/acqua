package dev.qtremors.acqua.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.ui.components.SettingsChoiceRow

@Composable
internal fun ConcurrentDownloadsSetting(maximum: Int, index: Int = 4, count: Int = 5, onChange: (Int) -> Unit) {
    SettingsChoiceRow(stringResource(R.string.concurrent_downloads),
        listOf(stringResource(R.string.one_download), stringResource(R.string.two_downloads)),
        maximum - 1, { onChange(it + 1) }, index = index, count = count,
        hint = stringResource(R.string.concurrent_downloads_hint))
}
