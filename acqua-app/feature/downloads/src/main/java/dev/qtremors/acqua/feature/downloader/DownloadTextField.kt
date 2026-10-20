package dev.qtremors.acqua.feature.downloader

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.qtremors.acqua.ui.components.ExpressiveTextField

@Composable
internal fun DownloadTextField(value: String, onChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, supportingText: String? = null, invalid: Boolean = false, enabled: Boolean = true) {
    ExpressiveTextField(value, onChange, modifier = modifier, singleLine = true, enabled = enabled,
        isError = invalid, label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } })
}
