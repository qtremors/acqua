package dev.qtremors.acqua.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.downloader.AudioCodecPreference
import dev.qtremors.acqua.downloader.DownloadFormatPolicy
import dev.qtremors.acqua.downloader.FormatPolicyMode
import dev.qtremors.acqua.downloader.VideoCodecPreference

@Composable
fun FormatPolicyButton(policy: DownloadFormatPolicy, onChange: (DownloadFormatPolicy) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text(stringResource(R.string.format_policy_summary,
        stringResource(policy.mode.label()), policy.videoCodec.label(), policy.audioCodec.name)) }
    if (open) FormatPolicyDialog(policy, onDismiss = { open = false }, onSave = { onChange(it); open = false })
}

@Composable
private fun FormatPolicyDialog(policy: DownloadFormatPolicy, onDismiss: () -> Unit, onSave: (DownloadFormatPolicy) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(policy.mode.name) }
    var video by rememberSaveable { mutableStateOf(policy.videoCodec.name) }
    var audio by rememberSaveable { mutableStateOf(policy.audioCodec.name) }
    var sort by rememberSaveable { mutableStateOf(policy.customSort) }
    val parsed = runCatching { DownloadFormatPolicy(FormatPolicyMode.valueOf(mode), VideoCodecPreference.valueOf(video),
        AudioCodecPreference.valueOf(audio), sort).validated() }.getOrNull()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.format_policy)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.format_policy_hint))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                FormatPolicyMode.entries.forEach { choice -> FilterChip(mode == choice.name, { mode = choice.name }, label = { Text(stringResource(choice.label())) }) }
            }
            Text(stringResource(R.string.video_codec))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                VideoCodecPreference.entries.forEach { choice -> FilterChip(video == choice.name, { video = choice.name }, label = { Text(choice.label()) }) }
            }
            Text(stringResource(R.string.audio_codec))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                AudioCodecPreference.entries.forEach { choice -> FilterChip(audio == choice.name, { audio = choice.name }, label = { Text(choice.name) }) }
            }
            if (mode == FormatPolicyMode.CUSTOM.name) {
                OutlinedTextField(sort, { sort = it.take(161) }, singleLine = true, isError = parsed == null,
                    label = { Text(stringResource(R.string.format_sort)) })
                Text(stringResource(R.string.format_sort_hint))
                if (parsed == null) Text(stringResource(R.string.format_sort_invalid))
            }
        }
    }, confirmButton = { TextButton(onClick = { parsed?.let(onSave) }, enabled = parsed != null) { Text(stringResource(R.string.done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

private fun FormatPolicyMode.label(): Int = when (this) {
    FormatPolicyMode.AUTO -> R.string.format_policy_auto
    FormatPolicyMode.COMPATIBLE -> R.string.format_policy_compatible
    FormatPolicyMode.DEVICE -> R.string.format_policy_device
    FormatPolicyMode.LOWEST -> R.string.format_policy_lowest
    FormatPolicyMode.CUSTOM -> R.string.format_policy_custom
}

private fun VideoCodecPreference.label(): String = when (this) {
    VideoCodecPreference.H264 -> "H.264"
    VideoCodecPreference.HEVC -> "HEVC"
    else -> name
}
