package dev.qtremors.acqua.feature.downloader

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.domain.MediaFormatOption
import dev.qtremors.acqua.domain.videoQualityChoices
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.downloader.VideoContainer
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions
import dev.qtremors.acqua.downloader.YtDlpFormatSelector.qualityDimension

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DownloadPickerDialog(
    state: DownloaderUiState, initialField: DownloadPickerField,
    onDismiss: () -> Unit, onApply: (YtDlpDownloadOptions) -> Unit
) {
    var field by rememberSaveable { mutableStateOf(initialField) }
    var audioTracksExpanded by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable(stateSaver = Saver<YtDlpDownloadOptions, String>(save = { dev.qtremors.acqua.downloader.DownloadOptionsCodec.encode(it) },
        restore = { dev.qtremors.acqua.downloader.DownloadOptionsCodec.decode(it, YtDlpDownloadOptions()) })) { mutableStateOf(state.configurationOptions()) }
    val video = state.downloadContentType == DownloadContentType.VIDEO
    val tabs = if (initialField.isStream) {
        if (video) listOf(DownloadPickerField.VIDEO_STREAM, DownloadPickerField.AUDIO_STREAM) else listOf(DownloadPickerField.AUDIO_STREAM)
    } else listOf(DownloadPickerField.QUALITY, DownloadPickerField.CONTAINER)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).testTag("download-picker-dialog")) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(if (initialField.isStream) R.string.download_streams else R.string.download_output),
                        Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
                }
                if (tabs.size > 1) DownloadDialogTabs(tabs.map { pickerFieldLabel(it, video) }, tabs.indexOf(field), { field = tabs[it] })
                else Text(pickerFieldLabel(field, video), style = MaterialTheme.typography.titleSmall)
                key(field) {
                    DownloadPickerList(state, draft, field, Modifier.weight(1f, fill = false), audioTracksExpanded,
                        { audioTracksExpanded = !audioTracksExpanded }) { draft = it }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("download-picker-cancel")) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = { onApply(draft) }, modifier = Modifier.testTag("download-picker-done"),
                        shapes = ButtonDefaults.shapes(), colors = ButtonDefaults.textButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                        Text(stringResource(R.string.done))
                    }
                }
            }
        }
    }
}

private data class PickerOption(val value: String?, val title: String, val detail: String? = null)

@Composable
private fun DownloadPickerList(
    state: DownloaderUiState, draft: YtDlpDownloadOptions, field: DownloadPickerField,
    modifier: Modifier, audioTracksExpanded: Boolean, onAudioTracksExpand: () -> Unit, onChange: (YtDlpDownloadOptions) -> Unit
) {
    val included = field == DownloadPickerField.AUDIO_STREAM && draft.contentType == DownloadContentType.VIDEO &&
        draft.videoFormatId != null && draft.videoFormatIncludesAudio
    val choices = pickerChoices(state, draft, field)
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (field == DownloadPickerField.AUDIO_STREAM && draft.contentType == DownloadContentType.VIDEO) {
            ContentChoiceRow(stringResource(R.string.silent_video), draft.silentVideo, { onChange(draft.withSilentVideo(!draft.silentVideo)) },
                detail = stringResource(R.string.silent_video_hint), multiple = true, tag = "silent-video")
            AudioTracksButton(draft, state.streamFormats(), audioTracksExpanded, onAudioTracksExpand, onChange)
        }
        if (included) Text(stringResource(R.string.stream_audio_included_hint), style = MaterialTheme.typography.bodyMedium)
        else if (field != DownloadPickerField.AUDIO_STREAM || !draft.silentVideo) choices.forEach { choice ->
            val selected = choice.value == draft.pickerValue(field)
            val optionId = if (choice.value == null) "automatic" else choice.value
            Surface(shape = MaterialTheme.shapes.medium,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("download-picker-option-$optionId")
                    .selectable(selected, role = Role.RadioButton, onClick = { onChange(draft.selectPickerValue(field, choice.value, state.streamFormats())) })
                    .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RadioButton(selected, onClick = null)
                    Column(Modifier.weight(1f)) {
                        Text(choice.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        choice.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        if (field.isStream && !included && state.streamFormats().isEmpty()) {
            Text(stringResource(if (state.media == null) R.string.stream_fetch_hint else R.string.exact_format_inspect_hint),
                Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun pickerChoices(state: DownloaderUiState, draft: YtDlpDownloadOptions, field: DownloadPickerField): List<PickerOption> {
    val video = draft.contentType == DownloadContentType.VIDEO
    val best = stringResource(R.string.best)
    val automatic = stringResource(R.string.format_automatic)
    val formats = state.streamFormats()
    return when (field) {
        DownloadPickerField.QUALITY -> {
            if (video) {
                videoQualityChoices(draft.maximumVideoHeight, formats.filter { it.hasVideo }.map { it.qualityDimension })
                    .map { PickerOption(it.toString(), if (it == 0) best else "${it}p") }
            } else {
                val values = if (draft.audioFormat == AudioOutputFormat.ORIGINAL) emptyList()
                    else listOf(320, 256, 192, 128, 96, 64, draft.audioBitrateKbps)
                listOf(PickerOption("0", best)) + values.filter { it > 0 }.distinct().sortedDescending()
                    .map { PickerOption(it.toString(), "$it kbps") }
            }
        }
        DownloadPickerField.CONTAINER -> if (video) VideoContainer.entries.map {
            PickerOption(it.name, if (it == VideoContainer.AUTO) automatic else it.name)
        } else AudioOutputFormat.entries.map { PickerOption(it.name, if (it == AudioOutputFormat.ORIGINAL) stringResource(R.string.original_audio) else it.name) }
        DownloadPickerField.VIDEO_STREAM -> listOf(PickerOption(null, automatic, stringResource(R.string.stream_automatic_quality))) +
            formats.filter { it.hasVideo }.sortedWith(compareByDescending<MediaFormatOption> { it.qualityDimension }
                .thenByDescending { it.fps }.thenByDescending { it.totalBitrateKbps }).map { PickerOption(it.id, streamTitle(it), streamDetails(it)) }
        DownloadPickerField.AUDIO_STREAM -> listOf(PickerOption(null, automatic, stringResource(R.string.stream_automatic_audio))) +
            formats.filter { it.hasAudio && !it.hasVideo }.sortedByDescending { it.audioBitrateKbps }
                .map { PickerOption(it.id, streamTitle(it), streamDetails(it)) }
    }
}

private fun YtDlpDownloadOptions.pickerValue(field: DownloadPickerField): String? = when (field) {
    DownloadPickerField.QUALITY -> if (contentType == DownloadContentType.VIDEO) maximumVideoHeight.toString()
        else if (audioFormat == AudioOutputFormat.ORIGINAL) "0" else audioBitrateKbps.toString()
    DownloadPickerField.CONTAINER -> if (contentType == DownloadContentType.VIDEO) videoContainer.name else audioFormat.name
    DownloadPickerField.VIDEO_STREAM -> videoFormatId
    DownloadPickerField.AUDIO_STREAM -> audioFormatId
}

internal fun YtDlpDownloadOptions.selectPickerValue(field: DownloadPickerField, value: String?, formats: List<MediaFormatOption>): YtDlpDownloadOptions = when (field) {
    DownloadPickerField.QUALITY -> if (contentType == DownloadContentType.VIDEO) copy(maximumVideoHeight = value?.toIntOrNull() ?: 0,
        videoFormatId = null, videoFormatIncludesAudio = true, formatSelectorOverride = null, templateAudioMultistreams = false)
        else copy(audioBitrateKbps = value?.toIntOrNull() ?: 0)
    DownloadPickerField.CONTAINER -> if (contentType == DownloadContentType.VIDEO) copy(videoContainer = VideoContainer.valueOf(requireNotNull(value)))
        else copy(audioFormat = AudioOutputFormat.valueOf(requireNotNull(value)))
    DownloadPickerField.VIDEO_STREAM -> {
        val format = formats.firstOrNull { it.id == value }
        copy(videoFormatId = value, videoFormatIncludesAudio = format?.hasAudio ?: true,
            formatSelectorOverride = null, templateAudioMultistreams = false,
            silentVideo = silentVideo && format?.hasAudio != true,
            additionalAudioFormatIds = if (format?.hasAudio == true && additionalAudioFormatIds.isNotEmpty()) additionalAudioFormatIds + listOfNotNull(audioFormatId) else additionalAudioFormatIds,
            maximumVideoHeight = format?.qualityDimension?.takeIf { it > 0 } ?: maximumVideoHeight,
            audioFormatId = if (format?.hasAudio == true) null else audioFormatId)
    }
    DownloadPickerField.AUDIO_STREAM -> copy(audioFormatId = value, additionalAudioFormatIds = emptySet(), silentVideo = false,
        formatSelectorOverride = null, templateAudioMultistreams = false)
}

@Composable
internal fun streamTitle(format: MediaFormatOption): String = if (format.hasVideo) {
    val quality = if (format.qualityDimension > 0) "${format.qualityDimension}p" else format.extension.uppercase()
    if (format.fps > 0) stringResource(R.string.stream_video_fps, quality, format.fps) else quality
} else {
    val codec = streamCodec(format.audioCodec).ifBlank { format.extension.uppercase() }
    if (format.audioBitrateKbps > 0) "$codec · ${format.audioBitrateKbps} kbps" else codec
}

@Composable
internal fun streamDetails(format: MediaFormatOption): String {
    val context = LocalContext.current
    val size = format.fileSize?.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }
    val id = stringResource(R.string.stream_format_id, format.id)
    return buildList {
        add(format.extension.uppercase())
        if (format.hasVideo) add(streamCodec(format.videoCodec))
        format.language?.let { add(it) }
        if (format.audioChannels > 0) add(stringResource(R.string.stream_audio_channels, format.audioChannels))
        if (size != null) add(size)
        add(id)
    }.filter { it.isNotBlank() }.joinToString(" · ")
}

private fun streamCodec(codec: String?): String = when {
    codec == null || codec == "none" -> ""
    codec.startsWith("avc") || codec.startsWith("h264") -> "H.264"
    codec.startsWith("hev") || codec.startsWith("hvc") || codec.startsWith("h265") -> "H.265"
    codec.startsWith("av01") -> "AV1"
    codec.startsWith("mp4a") -> "AAC"
    else -> codec.uppercase()
}
