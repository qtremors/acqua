package dev.qtremors.acqua.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
fun FormatPolicyButton(policy: DownloadFormatPolicy, onChange: (DownloadFormatPolicy) -> Unit) =
    FormatPolicyButton(policy, onChange, settingsRow = false)

@Composable
fun FormatPolicyButton(policy: DownloadFormatPolicy, onChange: (DownloadFormatPolicy) -> Unit, settingsRow: Boolean, index: Int = 0, count: Int = 1,
    trigger: (@Composable (onClick: () -> Unit, summary: String) -> Unit)? = null) {
    var open by rememberSaveable { mutableStateOf(false) }
    if (trigger != null) {
        val summary = buildList {
            add(stringResource(policy.mode.label()))
            if (policy.videoCodec != VideoCodecPreference.AUTO) add(policy.videoCodec.label())
            if (policy.audioCodec != AudioCodecPreference.AUTO) add(policy.audioCodec.name)
        }.joinToString(" · ")
        trigger({ open = true }, summary)
    } else if (settingsRow) SettingsActionRow(stringResource(R.string.format_policy), { open = true }, index = index, count = count,
        description = stringResource(policy.mode.label()), leadingIcon = Icons.Filled.Tune, trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
    else TextButton(onClick = { open = true }) { Text(stringResource(R.string.format_policy_summary,
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
    SettingsEditorDialog(stringResource(R.string.format_policy), onDismiss, stringResource(R.string.done),
        { parsed?.let(onSave) }, confirmEnabled = parsed != null, tag = "format-policy-dialog") {
            SettingsSection(stringResource(R.string.settings_format_selection)) {
                SettingsEditorOptions(FormatPolicyMode.entries.map { stringResource(it.label()) },
                    FormatPolicyMode.entries.indexOf(FormatPolicyMode.valueOf(mode)), { mode = FormatPolicyMode.entries[it].name })
                Text(stringResource(when (FormatPolicyMode.valueOf(mode)) {
                    FormatPolicyMode.AUTO -> R.string.settings_format_auto_hint
                    FormatPolicyMode.COMPATIBLE -> R.string.settings_format_compatible_hint
                    FormatPolicyMode.DEVICE -> R.string.settings_format_device_hint
                    FormatPolicyMode.LOWEST -> R.string.settings_format_lowest_hint
                    FormatPolicyMode.CUSTOM -> R.string.settings_format_custom_hint
                }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingsEditorChoicePair(
                SettingsChoice(stringResource(R.string.video_codec), VideoCodecPreference.entries.map {
                    if (it == VideoCodecPreference.AUTO) stringResource(R.string.format_automatic) else it.label()
                }, VideoCodecPreference.entries.indexOf(VideoCodecPreference.valueOf(video)), { video = VideoCodecPreference.entries[it].name }),
                SettingsChoice(stringResource(R.string.audio_codec), AudioCodecPreference.entries.map {
                    if (it == AudioCodecPreference.AUTO) stringResource(R.string.format_automatic) else it.name
                }, AudioCodecPreference.entries.indexOf(AudioCodecPreference.valueOf(audio)), { audio = AudioCodecPreference.entries[it].name })
            )
            Text(stringResource(R.string.settings_codecs_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (mode == FormatPolicyMode.CUSTOM.name) {
                ExpressiveTextField(sort, { sort = it.take(161) }, singleLine = true, isError = parsed == null, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.format_sort)) },
                    supportingText = { if (parsed == null) Text(stringResource(R.string.format_sort_invalid)) })
            }
            SettingsEditorInfo(stringResource(R.string.format_policy_hint) + if (mode == FormatPolicyMode.CUSTOM.name) "\n\n" + stringResource(R.string.format_sort_hint) else "")
    }
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
