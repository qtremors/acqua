package dev.qtremors.acqua.feature.downloader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.domain.MediaFormatOption
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.downloader.VideoContainer
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions

internal enum class DownloadPickerField {
    QUALITY, CONTAINER, VIDEO_STREAM, AUDIO_STREAM;
    val isStream: Boolean get() = this == VIDEO_STREAM || this == AUDIO_STREAM
    val tag: String get() = name.lowercase().replace('_', '-')
}

@Composable
internal fun DownloadPickerControls(state: DownloaderUiState, onApply: (YtDlpDownloadOptions) -> Unit) {
    var field by rememberSaveable(state.url, state.downloadEngine, state.downloadContentType) { mutableStateOf<DownloadPickerField?>(null) }
    val options = state.configurationOptions()
    val video = state.downloadContentType == DownloadContentType.VIDEO
    val formats = state.streamFormats()
    val best = stringResource(R.string.best)
    val automatic = stringResource(R.string.format_automatic)
    val quality = if (video) {
        if (options.maximumVideoHeight == 0) best else "${options.maximumVideoHeight}p"
    } else if (options.audioFormat == AudioOutputFormat.ORIGINAL || options.audioBitrateKbps == 0) best else "${options.audioBitrateKbps} kbps"
    val container = if (video) {
        if (options.videoContainer == VideoContainer.AUTO) automatic else options.videoContainer.name
    } else if (options.audioFormat == AudioOutputFormat.ORIGINAL) stringResource(R.string.original_audio) else options.audioFormat.name
    DownloadSummaryPair(listOf(
        DownloadSummary(DownloadPickerField.QUALITY, if (options.formatSelectorOverride != null) stringResource(R.string.command_template_format) else quality),
        DownloadSummary(DownloadPickerField.CONTAINER, container)
    ), video) { field = it }
    val includesAudio = video && options.videoFormatId != null && options.videoFormatIncludesAudio
    val streams = buildList {
        if (video) add(DownloadSummary(DownloadPickerField.VIDEO_STREAM, if (options.formatSelectorOverride != null) stringResource(R.string.command_template_format) else streamSummary(options.videoFormatId, formats)))
        add(DownloadSummary(DownloadPickerField.AUDIO_STREAM,
            when {
                options.silentVideo -> stringResource(R.string.silent_video)
                options.formatSelectorOverride != null -> stringResource(R.string.command_template_format)
                options.additionalAudioFormatIds.isNotEmpty() -> pluralStringResource(R.plurals.audio_tracks_count,
                    options.additionalAudioFormatIds.size + if (includesAudio || options.audioFormatId != null) 1 else 0,
                    options.additionalAudioFormatIds.size + if (includesAudio || options.audioFormatId != null) 1 else 0)
                includesAudio -> stringResource(R.string.stream_audio_included)
                else -> streamSummary(options.audioFormatId, formats)
            }))
    }
    DownloadSummaryPair(streams, video) { field = it }
    field?.let { selected ->
        DownloadPickerDialog(state, selected, onDismiss = { field = null }, onApply = { onApply(it); field = null })
    }
}

private data class DownloadSummary(val field: DownloadPickerField, val value: String, val enabled: Boolean = true)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadSummaryPair(items: List<DownloadSummary>, video: Boolean, onClick: (DownloadPickerField) -> Unit) {
    val labels = items.map { pickerFieldLabel(it.field, video) }
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            labels.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant) }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEachIndexed { index, item ->
                val description = stringResource(R.string.download_selection_description, labels[index], item.value)
                FilledTonalButton(
                    onClick = { onClick(item.field) }, enabled = item.enabled,
                    modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 54.dp)
                        .testTag("download-summary-${item.field.tag}").semantics { contentDescription = description },
                    shapes = ButtonDefaults.shapes(RoundedCornerShape(22.dp), RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.surfaceContainerHigh, contentColor = colors.onSurface),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(item.value, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
internal fun pickerFieldLabel(field: DownloadPickerField, video: Boolean): String = stringResource(when (field) {
    DownloadPickerField.QUALITY -> R.string.quality
    DownloadPickerField.CONTAINER -> if (video) R.string.download_container else R.string.download_format
    DownloadPickerField.VIDEO_STREAM -> R.string.exact_video_stream
    DownloadPickerField.AUDIO_STREAM -> R.string.exact_audio_stream
})

@Composable
private fun streamSummary(id: String?, formats: List<MediaFormatOption>): String =
    formats.firstOrNull { it.id == id }?.let { streamTitle(it) } ?: id ?: stringResource(R.string.format_automatic)

internal fun DownloaderUiState.configurationOptions(): YtDlpDownloadOptions = advancedOptions.copy(
    contentType = downloadContentType, maximumVideoHeight = maximumVideoHeight, audioFormat = audioFormat,
    embedMetadata = embedMetadata, embedThumbnail = embedThumbnail, cropArtwork = cropArtwork, preserveOriginalMetadata = preserveOriginalMetadata
)

internal fun DownloaderUiState.streamFormats(): List<MediaFormatOption> =
    selectedSource()?.formats.orEmpty()
