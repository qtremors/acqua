package dev.qtremors.acqua.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.domain.TransferNetworkOptions

@Composable
fun TransferNetworkOptionsButton(options: TransferNetworkOptions, onChange: (TransferNetworkOptions) -> Unit) =
    TransferNetworkOptionsButton(options, onChange, settingsRow = false)

@Composable
fun TransferNetworkOptionsButton(options: TransferNetworkOptions, onChange: (TransferNetworkOptions) -> Unit, settingsRow: Boolean, index: Int = 0, count: Int = 1,
    trigger: (@Composable (onClick: () -> Unit, summary: String) -> Unit)? = null) {
    var open by rememberSaveable { mutableStateOf(false) }
    if (trigger != null) {
        val summary = buildList {
            if (options.rateLimitKib > 0) add(stringResource(R.string.settings_network_rate, options.rateLimitKib))
            if (options.proxy.isNotBlank()) add(stringResource(R.string.settings_network_proxy))
            if (options.forceIpv4) add(stringResource(R.string.settings_network_ipv4))
            if (options.concurrentFragments > 1) add(stringResource(R.string.settings_network_fragments, options.concurrentFragments))
        }.joinToString(" · ").ifEmpty { stringResource(R.string.settings_network_default) }
        trigger({ open = true }, summary)
    } else if (settingsRow) SettingsActionRow(stringResource(R.string.transfer_network_options), { open = true }, index = index, count = count,
        description = stringResource(R.string.settings_network_summary), leadingIcon = Icons.Filled.Wifi, trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
    else TextButton(onClick = { open = true }) { Text(stringResource(R.string.transfer_network_options)) }
    if (open) TransferNetworkOptionsDialog(options, onDismiss = { open = false }, onSave = { onChange(it); open = false })
}

@Composable
private fun TransferNetworkOptionsDialog(options: TransferNetworkOptions, onDismiss: () -> Unit, onSave: (TransferNetworkOptions) -> Unit) {
    var rate by rememberSaveable { mutableStateOf(options.rateLimitKib.toString()) }
    var proxy by rememberSaveable { mutableStateOf(options.proxy) }
    var fragments by rememberSaveable { mutableStateOf(options.concurrentFragments.toString()) }
    var ipv4 by rememberSaveable { mutableStateOf(options.forceIpv4) }
    val parsed = runCatching {
        TransferNetworkOptions(rate.toInt(), proxy.trim(), ipv4, fragments.toInt()).validated()
    }.getOrNull()
    val invalidRate = rate.toIntOrNull()?.let { it !in 0..1_048_576 } ?: true
    val invalidFragments = fragments.toIntOrNull()?.let { it !in 1..8 } ?: true
    val ipv4Conflict = ipv4 && proxy.trim().startsWith("socks5://")
    val invalidProxy = runCatching { TransferNetworkOptions(proxy = proxy.trim()).validated() }.isFailure
    SettingsEditorDialog(stringResource(R.string.transfer_network_options), onDismiss, stringResource(R.string.save),
        { parsed?.let(onSave) }, confirmEnabled = parsed != null, tag = "network-options-dialog") {
        Text(stringResource(R.string.settings_network_brief), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ExpressiveTextField(rate, { rate = it.take(8) }, label = { Text(stringResource(R.string.settings_rate_label)) },
                singleLine = true, isError = invalidRate, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                supportingText = { Text(stringResource(if (invalidRate) R.string.settings_rate_invalid else R.string.settings_rate_hint)) })
            ExpressiveTextField(fragments, { fragments = it.take(2) }, label = { Text(stringResource(R.string.settings_fragments_label)) },
                singleLine = true, isError = invalidFragments, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                supportingText = { Text(stringResource(R.string.settings_fragments_hint)) })
        }
        ExpressiveTextField(proxy, { proxy = it.take(513) }, label = { Text(stringResource(R.string.settings_proxy_label)) },
            singleLine = true, modifier = Modifier.fillMaxWidth(), isError = invalidProxy || ipv4Conflict,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            supportingText = { Text(stringResource(when {
                ipv4Conflict -> R.string.settings_ipv4_conflict
                invalidProxy -> R.string.settings_proxy_invalid
                else -> R.string.settings_proxy_hint
            })) })
        SettingsSwitchRow(stringResource(R.string.transfer_ipv4), ipv4, { ipv4 = it })
        SettingsEditorInfo(stringResource(R.string.transfer_network_support) + "\n\n" + stringResource(R.string.transfer_proxy_hint))
    }
}
