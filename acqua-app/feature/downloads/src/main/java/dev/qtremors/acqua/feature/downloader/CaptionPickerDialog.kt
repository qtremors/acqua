package dev.qtremors.acqua.feature.downloader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.domain.CaptionTrack
import dev.qtremors.acqua.downloader.CaptionFormat
import dev.qtremors.acqua.downloader.CaptionMode
import dev.qtremors.acqua.downloader.DownloadCaptions
import dev.qtremors.acqua.downloader.DownloadOptionsCodec
import dev.qtremors.acqua.downloader.VideoContainer
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions
import java.util.Locale

@Composable
internal fun CaptionPickerDialog(state: DownloaderUiState, onResolve: () -> Unit, onInspect: (Int) -> Unit,
    onDismiss: () -> Unit, onApply: (YtDlpDownloadOptions) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var draft by rememberSaveable(stateSaver = Saver<YtDlpDownloadOptions, String>(save = { DownloadOptionsCodec.encode(it) },
        restore = { DownloadOptionsCodec.decode(it, YtDlpDownloadOptions()) })) { mutableStateOf(
        DownloadCaptions.normalized(state.configurationOptions().let { if (it.captionMode == CaptionMode.NONE) it.copy(captionMode = CaptionMode.EMBED) else it })) }
    var offset by rememberSaveable { mutableStateOf(draft.captionTiming.offsetSeconds.toString()) }
    var scale by rememberSaveable { mutableStateOf((draft.captionTiming.scale * 100).toString()) }
    val timing = runCatching { dev.qtremors.acqua.downloader.CaptionTiming(offset.toDouble(), scale.toDouble() / 100).validated() }.getOrNull()
    val index = state.selectedIndices().firstOrNull()
    val details = index != null && state.hasSourceDetails(index)
    val tracks = state.media?.getOrNull(index ?: -1)?.captions.orEmpty()
        .filter { it.language.matches(Regex("[A-Za-z0-9_-]{1,35}")) }
        .groupBy { it.language }.map { (_, group) -> group.firstOrNull { !it.automatic } ?: group.first() }
        .sortedBy { captionLanguageName(it).lowercase() }
    LaunchedEffect(details, tracks) {
        if (details) {
            val chosen = draft.captionLanguages.split(',').filter { code -> code == "all" || tracks.any { it.language == code } }
            val languages = chosen.ifEmpty { tracks.firstOrNull { !it.automatic }?.let { listOf(it.language) }
                ?: tracks.firstOrNull()?.let { listOf(it.language) }.orEmpty() }
            draft = draft.copy(captionLanguages = languages.joinToString(","))
        }
    }
    val enabled = draft.captionMode == CaptionMode.NONE || (timing != null && details && tracks.isNotEmpty() && draft.captionLanguages.isNotBlank())
    DownloadContentDialog(stringResource(R.string.caption_output), "download-captions-dialog", enabled, onDismiss, {
        val embedding = draft.captionMode in listOf(CaptionMode.EMBED, CaptionMode.BOTH)
        val automatic = state.media?.getOrNull(index ?: -1)?.captions.orEmpty().any {
            it.automatic && (draft.captionLanguages == "all" || it.language in draft.captionLanguages.split(',')) &&
                tracks.any { available -> available.language == it.language && (draft.captionLanguages == "all" || available.automatic) }
        }
        onApply(DownloadCaptions.normalized(draft.copy(automaticCaptions = automatic, captionTiming = timing ?: dev.qtremors.acqua.downloader.CaptionTiming(),
            captionFormat = if (embedding && draft.videoContainer == VideoContainer.WEBM) CaptionFormat.VTT else draft.captionFormat)))
    }, tabs = {
        DownloadDialogTabs(listOf(R.string.download_output, R.string.language, R.string.download_format, R.string.caption_timing).map { stringResource(it) }, tab, { tab = it })
    }) { modifier ->
        LazyColumn(modifier.fillMaxWidth().selectableGroup().testTag("caption-picker-list"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(stringResource(R.string.caption_container_support), style = MaterialTheme.typography.bodySmall)
                if (state.media == null) SourceFetchPrompt(state.isResolving, onResolve)
                else if (!details && index != null) SourceFetchPrompt(state.inspectingCollectionIndex != null, { onInspect(index) },
                    stringResource(if (state.collectionInspectionFailed) R.string.entry_inspection_retry else R.string.caption_inspect_hint))
            }
            if (tab == 3) item { CaptionTimelineControls(offset, scale, timing != null, { offset = it }, { scale = it }) }
            else captionPickerItems(tab, draft, tracks, details) { draft = it }
            if (state.media?.any { it.collectionUrl != null } == true) item {
                Text(stringResource(R.string.playlist_caption_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun LazyListScope.captionPickerItems(tab: Int, draft: YtDlpDownloadOptions, tracks: List<CaptionTrack>, details: Boolean,
    onChange: (YtDlpDownloadOptions) -> Unit) {
        when (tab) {
            0 -> items(if (DownloadCaptions.canEmbed(draft)) CaptionMode.entries else listOf(CaptionMode.NONE, CaptionMode.SEPARATE)) { mode ->
                ContentChoiceRow(stringResource(captionModeLabel(mode)), mode == draft.captionMode,
                    { onChange(DownloadCaptions.normalized(draft.copy(captionMode = mode))) }, tag = "caption-mode-${mode.name}")
            }
            1 -> {
                if (details && tracks.isEmpty()) item { Text(stringResource(R.string.no_captions_available)) }
                if (tracks.isNotEmpty()) item {
                    ContentChoiceRow(stringResource(R.string.caption_all_languages), draft.captionLanguages == "all",
                        { onChange(draft.copy(captionLanguages = if (draft.captionLanguages == "all") "" else "all")) },
                        multiple = true, tag = "caption-language-all")
                }
                items(tracks, key = { it.language }) { track ->
                    val selected = draft.captionLanguages == "all" || track.language in draft.captionLanguages.split(',')
                    ContentChoiceRow(captionLanguageName(track), selected, {
                        val current = if (draft.captionLanguages == "all") tracks.map { it.language }.toSet()
                            else draft.captionLanguages.split(',').filter(String::isNotBlank).toSet()
                        onChange(draft.copy(captionLanguages = (if (selected) current - track.language else current + track.language).joinToString(",")))
                    }, detail = stringResource(when {
                        track.translated -> R.string.caption_translated_source
                        track.automatic -> R.string.caption_automatic_source
                        else -> R.string.caption_original_source
                    }),
                        multiple = true, tag = "caption-language-${track.language}")
                }
            }
            2 -> {
                val webm = draft.videoContainer == VideoContainer.WEBM && draft.captionMode in listOf(CaptionMode.EMBED, CaptionMode.BOTH)
                items(CaptionFormat.entries.filter { DownloadCaptions.normalized(draft.copy(captionFormat = it)).captionFormat == it }) { format ->
                    ContentChoiceRow(if (format == CaptionFormat.ORIGINAL) stringResource(R.string.original_audio) else format.name,
                        format == draft.captionFormat || webm, { onChange(draft.copy(captionFormat = format)) }, tag = "caption-format-${format.name}")
                }
                if (webm) item { Text(stringResource(R.string.webm_caption_hint), style = MaterialTheme.typography.bodySmall) }
            }
        }
}

private fun captionLanguageName(track: CaptionTrack): String = track.name?.takeIf(String::isNotBlank)
    ?: Locale.forLanguageTag(track.language).getDisplayName(Locale.getDefault()).takeIf(String::isNotBlank) ?: track.language

private fun captionModeLabel(mode: CaptionMode): Int = when (mode) {
    CaptionMode.NONE -> R.string.caption_none
    CaptionMode.EMBED -> R.string.caption_embed
    CaptionMode.SEPARATE -> R.string.caption_separate
    CaptionMode.BOTH -> R.string.caption_both
}
