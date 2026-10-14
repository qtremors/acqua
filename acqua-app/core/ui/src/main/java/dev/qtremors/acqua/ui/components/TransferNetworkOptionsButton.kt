package dev.qtremors.acqua.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.qtremors.acqua.data.network.TransferNetworkOptions

@Composable
fun TransferNetworkOptionsButton(options: TransferNetworkOptions, onChange: (TransferNetworkOptions) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text(stringResource(R.string.transfer_network_options)) }
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.transfer_network_options)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.transfer_network_support))
                OutlinedTextField(rate, { rate = it.take(8) }, label = { Text(stringResource(R.string.transfer_rate_limit)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(proxy, { proxy = it.take(513) }, label = { Text(stringResource(R.string.transfer_proxy)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.transfer_proxy_hint))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.transfer_ipv4), Modifier.weight(1f))
                    Switch(ipv4, { ipv4 = it })
                }
                OutlinedTextField(fragments, { fragments = it.take(2) }, label = { Text(stringResource(R.string.transfer_fragments)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                if (parsed == null) Text(stringResource(R.string.transfer_network_invalid))
            }
        }, confirmButton = { TextButton(onClick = { parsed?.let(onSave) }, enabled = parsed != null) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
