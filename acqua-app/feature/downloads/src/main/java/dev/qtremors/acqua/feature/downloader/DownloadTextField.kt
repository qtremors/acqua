package dev.qtremors.acqua.feature.downloader

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun DownloadTextField(value: String, onChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, supportingText: String? = null, invalid: Boolean = false, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val corner by animateDpAsState(if (focused) 16.dp else 24.dp, label = "downloadFieldCorner")
    OutlinedTextField(value, onChange, modifier = modifier, singleLine = true, enabled = enabled,
        isError = invalid, label = { Text(label) }, interactionSource = interaction, shape = RoundedCornerShape(corner),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            errorContainerColor = MaterialTheme.colorScheme.errorContainer),
        supportingText = supportingText?.let { { Text(it) } })
}
