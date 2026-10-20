package dev.qtremors.acqua.feature.downloader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R

@Composable
internal fun CaptionTimelineControls(offset: String, scale: String, valid: Boolean, onOffset: (String) -> Unit, onScale: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.caption_timing_hint))
        DownloadTextField(offset, onOffset, stringResource(R.string.caption_offset), invalid = !valid, modifier = Modifier.testTag("caption-offset"))
        DownloadTextField(scale, onScale, stringResource(R.string.caption_scale), invalid = !valid, modifier = Modifier.testTag("caption-scale"))
        if (!valid) Text(stringResource(R.string.caption_timing_error), color = MaterialTheme.colorScheme.error)
    }
}
