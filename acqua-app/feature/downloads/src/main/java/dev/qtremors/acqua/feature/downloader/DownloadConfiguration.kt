package dev.qtremors.acqua.feature.downloader

import android.text.format.Formatter
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.downloader.DownloadQueueState
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DownloadConfigBottomSheet(
    state: DownloaderUiState,
    canResolve: Boolean,
    onDismiss: () -> Unit,
    onResolve: () -> Unit,
    onEngineChange: (DownloadEngine) -> Unit,
    onContentTypeChange: (DownloadContentType) -> Unit,
    onConfigurationChange: (YtDlpDownloadOptions) -> Unit,
    onDownload: () -> Unit,
    onPlaylistSelection: (Boolean, Set<Int>?) -> Unit = { _, _ -> },
    onContentSelection: (YtDlpDownloadOptions, Set<Int>?) -> Unit = { _, _ -> },
    onInspectEntry: (Int) -> Unit = {},
    showSource: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val readyToDownload = state.media != null || WebLink.extractAll(state.url).size > 1

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        ),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = colors.surfaceContainerLow
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).imePadding().testTag("download-config-sheet")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.download_options),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp).testTag("download-config-controls"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showSource) Text(state.url, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall)
                DownloadOptionChips(stringResource(R.string.download_engine), state.downloadEngine,
                    DownloadEngine.entries.map { it to stringResource(
                        if (it == DownloadEngine.ACQUA) R.string.acqua_engine else R.string.ytdlp_engine) }, onEngineChange)
                if (state.downloadEngine == DownloadEngine.YT_DLP) {
                    DownloadOptionChips(stringResource(R.string.download_type), state.downloadContentType,
                        listOf(DownloadContentType.AUDIO to stringResource(R.string.audio),
                            DownloadContentType.VIDEO to stringResource(R.string.video)), onContentTypeChange)
                    DownloadPickerControls(state, onConfigurationChange)
                }
                DownloadExtraControls(state, onResolve, onInspectEntry, onPlaylistSelection, onContentSelection)
                dev.qtremors.acqua.feature.downloader.CookieProfileSelector(state.configurationOptions(), onApply = onConfigurationChange)
                if (state.downloadEngine == DownloadEngine.YT_DLP) dev.qtremors.acqua.ui.components.CommandTemplateSelector(state.configurationOptions()) {
                    if (it.contentType != state.downloadContentType) onContentTypeChange(it.contentType)
                    onConfigurationChange(it)
                }
                if (state.downloadEngine == DownloadEngine.YT_DLP) dev.qtremors.acqua.ui.components.FormatPolicyButton(state.advancedOptions.formatPolicy) {
                    onConfigurationChange(state.configurationOptions().copy(formatPolicy = it, formatSelectorOverride = null, templateAudioMultistreams = false))
                }
                dev.qtremors.acqua.ui.components.SettingsSwitchRow(index = 0, count = 1,
                    title = stringResource(R.string.record_download_history),
                    description = stringResource(R.string.record_download_history_hint),
                    checked = state.advancedOptions.recordHistory,
                    onCheckedChange = { onConfigurationChange(state.configurationOptions().copy(recordHistory = it)) })
                dev.qtremors.acqua.ui.components.TransferNetworkOptionsButton(state.advancedOptions.network) {
                    onConfigurationChange(state.configurationOptions().copy(network = it))
                }
                if (state.downloadEngine == DownloadEngine.YT_DLP) dev.qtremors.acqua.ui.components.SegmentRemovalButton(state.advancedOptions.removeSegments) {
                    onConfigurationChange(state.configurationOptions().copy(removeSegments = it))
                }
            }
            HorizontalDivider()
            Button(
                onClick = { onDismiss(); if (readyToDownload) onDownload() else onResolve() },
                enabled = canResolve && state.configurationActionEnabled(),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    .fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight).testTag("download-config-action"),
                shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)
            ) {
                if (state.isResolving) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = colors.onPrimary, strokeWidth = 2.dp)
                } else Icon(Icons.Filled.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(if (readyToDownload && state.selectedCollectionItems != null && !state.isResolving)
                    stringResource(R.string.download_selected_count, state.selectedIndices().size) else if (
                    readyToDownload && state.media?.any { it.collectionUrl != null } == true && !state.isResolving
                ) stringResource(R.string.download_selected_count, state.selectedIndices().size) else stringResource(when {
                    state.isResolving -> R.string.resolving_media
                    readyToDownload -> R.string.download
                    else -> R.string.fetch
                }),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

internal fun DownloaderUiState.configurationActionEnabled(): Boolean {
    if (isResolving || isSaving || savingItemIndex != null || isBatchRunning) return false
    if (WebLink.extractAll(url).size > 1) return true
    val items = media ?: return validity == LinkValidity.VALID
    return items.withIndex().any { (index, item) ->
        item.unavailableReason == null && (selectedCollectionItems == null || index in selectedCollectionItems)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <T> DownloadOptionChips(label: String, selected: T, choices: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
        key(label, choices.map { it.first }) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                choices.forEachIndexed { index, (value, text) ->
                    key(value) {
                        val isSelected = value == selected
                        val shapes = when {
                            choices.size == 1 -> ToggleButtonDefaults.shapesFor(48.dp)
                            index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            index == choices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        }
                        ToggleButton(
                            checked = isSelected,
                            onCheckedChange = { if (!isSelected) onSelect(value) },
                            modifier = Modifier.heightIn(min = 48.dp).semantics {
                                role = Role.RadioButton
                                this.selected = isSelected
                            },
                            shapes = shapes,
                            colors = ToggleButtonDefaults.colors(containerColor = colors.surfaceContainerHigh,
                                contentColor = colors.onSurface, checkedContainerColor = colors.primary,
                                checkedContentColor = colors.onPrimary),
                            icon = if (isSelected) ({ Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }) else null
                        ) {
                            Text(text, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun downloadProgressDetails(
    downloadedBytes: Long,
    totalBytes: Long,
    etaSeconds: Long
): String {
    val context = LocalContext.current
    val size = when {
        downloadedBytes > 0L && totalBytes > 0L -> stringResource(
            R.string.download_size_progress,
            Formatter.formatShortFileSize(context, downloadedBytes),
            Formatter.formatShortFileSize(context, totalBytes)
        )
        downloadedBytes > 0L -> stringResource(
            R.string.download_size_downloaded,
            Formatter.formatShortFileSize(context, downloadedBytes)
        )
        else -> null
    }
    val eta = if (etaSeconds > 0L) pluralStringResource(
        R.plurals.download_eta,
        etaSeconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        etaSeconds
    ) else null
    return listOfNotNull(size, eta).joinToString(" · ")
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CompactDownloadStatus(
    state: DownloaderUiState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val activeItem = state.downloadQueue.activeItems.firstOrNull()
    val status = activeItem?.let {
        stringResource(
            when (it.state) {
                DownloadQueueState.QUEUED -> R.string.download_queued
                DownloadQueueState.RETRYING -> R.string.download_retrying
                else -> R.string.download_running
            }
        )
    } ?: stringResource(R.string.download_running)
    val details = downloadProgressDetails(
        state.downloadedBytes,
        state.totalBytes,
        state.downloadEtaSeconds
    )
    val progress = (state.downloadProgress / 100f).coerceIn(0f, 1f)

    ElevatedCard(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth().semantics {
            liveRegion = LiveRegionMode.Polite
            stateDescription = status
        },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = colors.secondaryContainer)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(38.dp).semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f, 0)
                    },
                    color = colors.primary,
                    trackColor = colors.onSecondaryContainer.copy(alpha = 0.16f)
                )
                Text(
                    "${state.downloadProgress.coerceIn(0f, 100f).toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 8.sp),
                    color = colors.onSecondaryContainer
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        pluralStringResource(
                            R.plurals.active_downloads,
                            state.activeDownloadCount,
                            state.activeDownloadCount
                        ),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSecondaryContainer
                    )
                    if (status.isNotEmpty()) {
                        Text(
                            "· $status",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
                if (details.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        details,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSecondaryContainer.copy(alpha = 0.85f),
                        maxLines = 2
                    )
                }
            }
            IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Close,
                    stringResource(R.string.cancel_download),
                    tint = colors.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
