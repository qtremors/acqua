package dev.qtremors.acqua.feature.downloader

import dev.qtremors.acqua.ui.components.EmptyState
import dev.qtremors.acqua.ui.components.EmptyStateVariant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.RangeSliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.domain.MediaChapter
import dev.qtremors.acqua.downloader.ClipRange
import dev.qtremors.acqua.downloader.MediaTimeline
import dev.qtremors.acqua.downloader.DownloadCaptions
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions
import java.util.Locale
import kotlin.math.round

@Composable
internal fun ClipPickerDialog(state: DownloaderUiState, onResolve: () -> Unit, onInspect: (Int) -> Unit,
    onDismiss: () -> Unit, onApply: (YtDlpDownloadOptions, Set<Int>?) -> Unit) {
    val original = state.advancedOptions
    var index by rememberSaveable { mutableStateOf(state.selectedIndices().singleOrNull()) }
    var mode by rememberSaveable { mutableStateOf(if (original.splitChapters) 2 else if (MediaTimeline.hasSections(original)) 1 else 0) }
    val source = state.media?.getOrNull(index ?: -1)
    val details = index != null && state.hasSourceDetails(index!!)
    val duration = source?.let(MediaTimeline::duration) ?: 0.0
    val defaultName = stringResource(R.string.clip_default_name)
    var draft by rememberSaveable(index, duration) { mutableStateOf(ArrayList((original.clipRanges.ifEmpty {
        listOf(ClipRange(original.clipStartSeconds ?: 0.0, original.clipEndSeconds ?: duration, "$defaultName 1"))
    }).flatMap { listOf(it.name, formatClipTime(it.startSeconds), formatClipTime(it.endSeconds)) })) }
    val ranges = draft.chunked(3).mapNotNull { row ->
        val from = parseClipTime(row[1]); val to = parseClipTime(row[2])
        if (from != null && to != null) ClipRange(from, to, row[0]) else null
    }
    val validRanges = ranges.size == draft.size / 3 && ranges.isNotEmpty() && duration > 0 &&
        runCatching { MediaTimeline.validateRanges(ranges, duration) }.isSuccess
    val valid = mode == 0 || (details && if (mode == 1) validRanges else source?.chapters?.isNotEmpty() == true)
    DownloadContentDialog(stringResource(R.string.download_section), "download-clip-dialog", valid, onDismiss, {
        onApply(DownloadCaptions.normalized(original.copy(clipStartSeconds = null, clipEndSeconds = null,
            clipRanges = if (mode == 1) ranges else emptyList(), splitChapters = mode == 2)),
            if (mode == 0) state.selectedCollectionItems else index?.let(::setOf))
    }, tabs = {
        DownloadDialogTabs(listOf(R.string.full_video, R.string.clip_range, R.string.chapters).map { stringResource(it) }, mode, { mode = it })
    }) { modifier ->
        LazyColumn(modifier.fillMaxWidth().testTag("clip-picker-list"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (mode == 0) item { Text(stringResource(R.string.whole_media_hint)) }
            else if (state.media == null) item { SourceFetchPrompt(state.isResolving, onResolve) }
            else if (index == null) {
                item { Text(stringResource(R.string.clip_choose_entry)) }
                itemsIndexed(state.media) { itemIndex, item ->
                    PlaylistEntryRow(item, itemIndex, false, item.unavailableReason == null) { index = itemIndex }
                }
            } else {
                item { Text(source?.title ?: stringResource(R.string.download_section), style = MaterialTheme.typography.titleSmall) }
                if (!details) item { SourceFetchPrompt(state.inspectingCollectionIndex != null, { onInspect(index!!) },
                    stringResource(if (state.collectionInspectionFailed) R.string.entry_inspection_retry else R.string.clip_inspect_hint)) }
                else if (mode == 1) {
                    item { Text(stringResource(R.string.clip_processing_hint), style = MaterialTheme.typography.bodySmall) }
                    items(draft.size / 3) { rangeIndex ->
                        val row = draft.chunked(3)[rangeIndex]
                        ClipDraftControls(rangeIndex, row, duration, validRanges, onChange = { part, value ->
                            draft = ArrayList(draft).also { it[rangeIndex * 3 + part] = value }
                        }, onRemove = { draft = ArrayList(draft.filterIndexed { position, _ -> position / 3 != rangeIndex }) })
                    }
                    item { androidx.compose.material3.TextButton(enabled = draft.size / 3 < MediaTimeline.MAX_CLIPS, onClick = {
                        val nextStart = ranges.lastOrNull()?.endSeconds ?: 0.0
                        draft = ArrayList(draft + listOf("$defaultName ${draft.size / 3 + 1}", formatClipTime(nextStart), formatClipTime(duration)))
                    }) { Text(stringResource(R.string.clip_add_range)) } }
                } else item { ChapterChoices(source?.chapters.orEmpty()) { chapter ->
                    mode = 1; draft = arrayListOf(chapter.title.take(80), formatClipTime(chapter.startSeconds), formatClipTime(chapter.endSeconds))
                } }

            }
        }
    }
}

@Composable
private fun ClipDraftControls(index: Int, row: List<String>, duration: Double, valid: Boolean,
    onChange: (Int, String) -> Unit, onRemove: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("clip-range-$index")) {
        DownloadTextField(row[0], { if (it.length <= 80) onChange(0, it) }, stringResource(R.string.clip_range_name))
        ClipRangeControls(row[1], row[2], duration, valid, { onChange(1, it) }, { onChange(2, it) })
        androidx.compose.material3.TextButton(onClick = onRemove) { Text(stringResource(R.string.clip_remove_range)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClipRangeControls(start: String, end: String, duration: Double, valid: Boolean,
    onStart: (String) -> Unit, onEnd: (String) -> Unit) {
    val from = parseClipTime(start)?.coerceIn(0.0, duration) ?: 0.0
    val to = parseClipTime(end)?.coerceIn(from, duration) ?: duration
    val sliderState = remember(duration) { RangeSliderState(from.toFloat(), to.toFloat(), trackRange = 0f..duration.toFloat()) }
    sliderState.endValue = maxOf(sliderState.startValue, to.toFloat())
    sliderState.startValue = from.toFloat()
    sliderState.endValue = to.toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DownloadTextField(start, onStart, stringResource(R.string.clip_start), invalid = !valid,
                modifier = Modifier.weight(1f).testTag("clip-start"))
            DownloadTextField(end, onEnd, stringResource(R.string.clip_end), invalid = !valid,
                modifier = Modifier.weight(1f).testTag("clip-end"))
        }
        RangeSlider(state = sliderState, onValueChange = {
            onStart(formatClipTime(round(it.start.toDouble() * 1000) / 1000))
            onEnd(formatClipTime(round(it.endInclusive.toDouble() * 1000) / 1000))
        }, modifier = Modifier.fillMaxWidth().testTag("clip-range-slider"))
        Text(stringResource(R.string.clip_time_hint, formatClipTime(duration)), style = MaterialTheme.typography.bodySmall)
        if (!valid) Text(stringResource(R.string.clip_range_error), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error)
        if (valid) Text(stringResource(R.string.clip_duration, formatClipTime(to - from)), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ChapterChoices(chapters: List<MediaChapter>, onRange: (MediaChapter) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (chapters.isEmpty()) EmptyState(variant = EmptyStateVariant.Chapters, compact = true)
        else {
            Text(stringResource(R.string.split_download_chapters), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.chapter_range_hint), style = MaterialTheme.typography.bodySmall)
            chapters.forEach { chapter ->
                ContentChoiceRow(chapter.title, false, { onRange(chapter) },
                    detail = "${formatClipTime(chapter.startSeconds)} · ${formatClipTime(chapter.endSeconds)}")
            }
        }
    }
}

internal fun formatClipTime(seconds: Double): String {
    val millis = (seconds.coerceAtLeast(0.0) * 1000).toLong()
    val whole = millis / 1000
    val time = if (whole >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", whole / 3600, whole / 60 % 60, whole % 60)
        else String.format(Locale.ROOT, "%d:%02d", whole / 60, whole % 60)
    return if (millis % 1000 == 0L) time else time + String.format(Locale.ROOT, ".%03d", millis % 1000).trimEnd('0')
}

internal fun parseClipTime(input: String): Double? {
    val parts = input.trim().split(':')
    if (parts.size !in 1..3) return null
    val numbers = parts.map { it.toDoubleOrNull()?.takeIf { n -> n.isFinite() && n >= 0 } ?: return null }
    if (numbers.dropLast(1).any { it % 1.0 != 0.0 } || (parts.size > 1 && numbers.last() >= 60) ||
        (parts.size == 3 && numbers[1] >= 60)) return null
    return numbers.fold(0.0) { time, part -> time * 60 + part }.takeIf(Double::isFinite)
}
