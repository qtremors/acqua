package dev.qtremors.acqua.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.ui.components.SettingsCardContainer

@Composable
internal fun ConcurrentDownloadsSetting(maximum: Int, onChange: (Int) -> Unit) {
    SettingsCardContainer(index = 4, count = 5) {
        Column {
            Text(stringResource(R.string.concurrent_downloads))
            Text(stringResource(R.string.concurrent_downloads_hint))
            FlowRow(Modifier.selectableGroup()) {
                listOf(1 to R.string.one_download, 2 to R.string.two_downloads).forEach { (value, label) ->
                    FilterChip(selected = maximum == value, onClick = { onChange(value) },
                        modifier = Modifier.semantics { role = Role.RadioButton }, label = { Text(stringResource(label)) })
                }
            }
        }
    }
}
