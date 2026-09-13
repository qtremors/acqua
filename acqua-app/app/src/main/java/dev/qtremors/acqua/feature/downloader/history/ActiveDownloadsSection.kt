package dev.qtremors.acqua.feature.downloader.history

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.qtremors.acqua.R
import dev.qtremors.acqua.downloader.DownloadQueueItem
import dev.qtremors.acqua.downloader.DownloadQueueSnapshot
import dev.qtremors.acqua.downloader.DownloadQueueState
import dev.qtremors.acqua.platform.HapticSignal
import dev.qtremors.acqua.platform.performHaptic

@Composable
fun ActiveDownloadsSection(
    queue: DownloadQueueSnapshot,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeItems = queue.activeItems

    AnimatedVisibility(
        visible = activeItems.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.active_downloads_header),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.active_downloads,
                        activeItems.size,
                        activeItems.size
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            activeItems.forEach { item ->
                ActiveDownloadCard(
                    item = item,
                    onCancel = { onCancel(item.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActiveDownloadCard(
    item: DownloadQueueItem,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val progress = (item.progress / 100f).coerceIn(0f, 1f)

    val status = stringResource(
        when (item.state) {
            DownloadQueueState.QUEUED -> R.string.download_queued
            DownloadQueueState.RETRYING -> R.string.download_retrying
            else -> R.string.download_running
        }
    )

    val progressText = formatDownloadDetails(
        context = context,
        downloadedBytes = item.downloadedBytes,
        totalBytes = item.totalBytes,
        etaSeconds = item.etaSeconds
    )

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = colors.secondaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(38.dp),
                    color = colors.primary,
                    trackColor = colors.onSecondaryContainer.copy(alpha = 0.16f)
                )
                Text(
                    "${item.progress.coerceIn(0f, 100f).toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 8.sp),
                    color = colors.onSecondaryContainer
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSecondaryContainer
                    )
                }

                if (progressText.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSecondaryContainer.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = {
                    context.performHaptic(HapticSignal.CLICK)
                    onCancel()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cancel_download),
                    tint = colors.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatDownloadDetails(
    context: android.content.Context,
    downloadedBytes: Long,
    totalBytes: Long,
    etaSeconds: Long
): String {
    val size = when {
        downloadedBytes > 0L && totalBytes > 0L ->
            "${Formatter.formatFileSize(context, downloadedBytes)} / ${Formatter.formatFileSize(context, totalBytes)}"
        downloadedBytes > 0L ->
            Formatter.formatFileSize(context, downloadedBytes)
        else -> null
    }

    val eta = if (etaSeconds > 0L) {
        val seconds = etaSeconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        context.resources.getQuantityString(R.plurals.download_eta, seconds, etaSeconds)
    } else null

    return listOfNotNull(size, eta).joinToString(" · ")
}
