package dev.qtremors.acqua.feature.downloader

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.domain.MediaFormatOption
import dev.qtremors.acqua.downloader.VideoContainer
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions

@Composable
internal fun AudioTracksButton(options: YtDlpDownloadOptions, formats: List<MediaFormatOption>, expanded: Boolean,
    onExpand: () -> Unit, onChange: (YtDlpDownloadOptions) -> Unit) {
    val included = options.videoFormatId != null && options.videoFormatIncludesAudio
    val chosen = (listOfNotNull(options.audioFormatId.takeUnless { included }) + options.additionalAudioFormatIds).toSet()
    TextButton(onClick = onExpand, enabled = !options.silentVideo && formats.any { it.hasAudio && !it.hasVideo }) {
        Text(pluralStringResource(R.plurals.audio_tracks_count, chosen.size, chosen.size))
    }
    if (expanded && !options.silentVideo) Column {
        Text(stringResource(R.string.multiple_audio_tracks_hint))
        formats.filter { it.hasAudio && !it.hasVideo }.forEach { format ->
            ContentChoiceRow(streamTitle(format), format.id in chosen, {
                val selected = (if (format.id in chosen) chosen - format.id else chosen + format.id).sorted()
                onChange(options.copy(audioFormatId = if (included) null else selected.firstOrNull(),
                    formatSelectorOverride = null, templateAudioMultistreams = false,
                    additionalAudioFormatIds = (if (included) selected else selected.drop(1)).toSet(),
                    videoContainer = if ((included && selected.isNotEmpty()) || selected.size > 1) VideoContainer.MKV else options.videoContainer,
                    acceptedStreams = emptyList()))
            }, detail = streamDetails(format), multiple = true, tag = "audio-track-${format.id}", enabled = chosen.size < 8 || format.id in chosen)
        }
    }
}

internal fun YtDlpDownloadOptions.withSilentVideo(enabled: Boolean): YtDlpDownloadOptions = if (!enabled) copy(silentVideo = false) else copy(
    silentVideo = true, audioFormatId = null, additionalAudioFormatIds = emptySet(), acceptedStreams = emptyList(),
    formatSelectorOverride = null, templateAudioMultistreams = false,
    videoFormatId = videoFormatId.takeUnless { videoFormatIncludesAudio }, videoFormatIncludesAudio = false)
