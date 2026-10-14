package dev.qtremors.acqua.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.downloader.SegmentCategory

@Composable
fun SegmentRemovalButton(selected: Set<SegmentCategory>, onChange: (Set<SegmentCategory>) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text(pluralStringResource(R.plurals.segment_removal_count, selected.size, selected.size)) }
    if (open) {
        var draft by rememberSaveable { mutableStateOf(ArrayList(selected.map { it.name })) }
        AlertDialog(onDismissRequest = { open = false }, title = { Text(stringResource(R.string.segment_removal)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.segment_removal_hint))
                Row {
                    TextButton(onClick = { draft = arrayListOf() }) { Text(stringResource(R.string.segment_none)) }
                    TextButton(onClick = { draft = arrayListOf(SegmentCategory.SPONSOR.name) }) { Text(stringResource(R.string.segment_sponsors)) }
                    TextButton(onClick = { draft = ArrayList(SegmentCategory.entries.map { it.name }) }) { Text(stringResource(R.string.select_all)) }
                }
                SegmentCategory.entries.forEach { category ->
                    Row(Modifier.fillMaxWidth().toggleable(category.name in draft, role = Role.Checkbox, onValueChange = { enabled ->
                        draft = ArrayList(if (enabled) draft + category.name else draft - category.name)
                    }), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(category.name in draft, null)
                        Text(stringResource(category.label()))
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { onChange(draft.map(SegmentCategory::valueOf).toSet()); open = false }) { Text(stringResource(R.string.done)) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } })
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
