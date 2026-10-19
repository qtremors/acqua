package dev.qtremors.acqua.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.downloader.SegmentCategory

@Composable
fun SegmentRemovalButton(selected: Set<SegmentCategory>, onChange: (Set<SegmentCategory>) -> Unit) =
    SegmentRemovalButton(selected, onChange, settingsRow = false)

@Composable
fun SegmentRemovalButton(selected: Set<SegmentCategory>, onChange: (Set<SegmentCategory>) -> Unit, settingsRow: Boolean, index: Int = 0, count: Int = 1,
    trigger: (@Composable (onClick: () -> Unit, summary: String) -> Unit)? = null) {
    var open by rememberSaveable { mutableStateOf(false) }
    if (trigger != null) trigger({ open = true }, if (selected.isEmpty()) stringResource(R.string.segment_none)
        else pluralStringResource(R.plurals.settings_segment_categories, selected.size, selected.size))
    else if (settingsRow) SettingsActionRow(stringResource(R.string.segment_removal), { open = true }, index = index, count = count,
        description = pluralStringResource(R.plurals.segment_removal_count, selected.size, selected.size), leadingIcon = Icons.Filled.ContentCut,
        trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
    else TextButton(onClick = { open = true }) { Text(pluralStringResource(R.plurals.segment_removal_count, selected.size, selected.size)) }
    if (open) {
        var draft by rememberSaveable { mutableStateOf(ArrayList(selected.map { it.name })) }
        SettingsEditorDialog(stringResource(R.string.segment_removal), { open = false }, stringResource(R.string.done),
            { onChange(draft.map(SegmentCategory::valueOf).toSet()); open = false }, tag = "segment-removal-dialog") {
                Text(stringResource(R.string.settings_segments_brief), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val preset = when (draft.toSet()) {
                    emptySet<String>() -> 0
                    setOf(SegmentCategory.SPONSOR.name) -> 1
                    SegmentCategory.entries.map { it.name }.toSet() -> 2
                    else -> -1
                }
                SettingsEditorOptions(listOf(stringResource(R.string.segment_none), stringResource(R.string.segment_sponsors),
                    stringResource(R.string.select_all)), preset, { choice ->
                    draft = when (choice) {
                        0 -> arrayListOf()
                        1 -> arrayListOf(SegmentCategory.SPONSOR.name)
                        else -> ArrayList(SegmentCategory.entries.map { it.name })
                    }
                }, columns = 3)
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)) {
                    SegmentCategory.entries.forEachIndexed { index, category ->
                        SettingsSwitchRow(stringResource(category.label()), category.name in draft, { enabled ->
                            draft = ArrayList(if (enabled) draft + category.name else draft - category.name)
                        }, index = index, count = SegmentCategory.entries.size)
                    }
                }
                SettingsEditorInfo(stringResource(R.string.segment_removal_hint))
        }
    }
}

private fun SegmentCategory.label(): Int = when (this) {
    SegmentCategory.SPONSOR -> R.string.segment_sponsors
    SegmentCategory.INTRO -> R.string.segment_intros
    SegmentCategory.OUTRO -> R.string.segment_outros
    SegmentCategory.SELF_PROMOTION -> R.string.segment_self_promotion
    SegmentCategory.INTERACTION -> R.string.segment_interaction
    SegmentCategory.PREVIEW -> R.string.segment_preview
    SegmentCategory.NON_MUSIC -> R.string.segment_non_music
    SegmentCategory.FILLER -> R.string.segment_filler
}
