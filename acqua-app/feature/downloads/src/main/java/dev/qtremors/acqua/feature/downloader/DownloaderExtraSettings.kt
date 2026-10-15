package dev.qtremors.acqua.feature.downloader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.ui.components.SettingsSection
import dev.qtremors.acqua.ui.components.SettingsSwitchRow

@Composable
fun DownloaderExtraSettings(viewModel: DownloaderViewModel) {
    val state by viewModel.state.collectAsState()
    DownloaderExtraSettingsContent(state, viewModel::setEmbedMetadata, viewModel::setEmbedThumbnail, viewModel::setCropArtwork,
        viewModel::setPreserveOriginalMetadata, onRecordHistory = { enabled ->
            viewModel.setDefaultDownloadOptions(state.advancedOptions.copy(recordHistory = enabled))
        }, onNetworkChange = { network -> viewModel.updateDownloadDefaults { it.copy(network = network) } },
        onSegmentsChange = { categories -> viewModel.updateDownloadDefaults { it.copy(removeSegments = categories) } },
        onFormatPolicyChange = { policy -> viewModel.updateDownloadDefaults { it.copy(formatPolicy = policy) } }) { enabled ->
        viewModel.setDefaultDownloadOptions(state.advancedOptions.copy(skipArchived = enabled))
    }
}

@Composable
internal fun DownloaderExtraSettingsContent(state: DownloaderUiState, onEmbedMetadata: (Boolean) -> Unit,
    onEmbedThumbnail: (Boolean) -> Unit, onCropArtwork: (Boolean) -> Unit,
    onPreserveOriginalMetadata: (Boolean) -> Unit, onRecordHistory: (Boolean) -> Unit = {},
    onNetworkChange: (dev.qtremors.acqua.data.network.TransferNetworkOptions) -> Unit = {},
    onSegmentsChange: (Set<dev.qtremors.acqua.downloader.SegmentCategory>) -> Unit = {},
    onFormatPolicyChange: (dev.qtremors.acqua.downloader.DownloadFormatPolicy) -> Unit = {}, onSkipArchived: (Boolean) -> Unit) {
    val kind = state.selectionKind()
    val supportsArtwork = kind == MediaKind.AUDIO && state.downloadEngine == DownloadEngine.YT_DLP &&
        state.media.orEmpty().all { it.backend == MediaBackend.YT_DLP }
    val count = if (supportsArtwork) 6 else 4
    val mediaLabel = stringResource(when (kind) {
        MediaKind.IMAGE -> R.string.photo
        MediaKind.AUDIO -> R.string.audio
        else -> R.string.video
    })
    SettingsSection(title = stringResource(R.string.download_options_for_media, mediaLabel)) {
        SettingsSwitchRow(index = 0, count = count, title = stringResource(R.string.embed_metadata),
            description = stringResource(R.string.embed_metadata_explanation), checked = state.embedMetadata,
            onCheckedChange = onEmbedMetadata)
        if (supportsArtwork) {
            SettingsSwitchRow(index = 1, count = count, title = stringResource(R.string.embed_thumbnail),
                description = stringResource(R.string.embed_thumbnail_explanation), checked = state.embedThumbnail,
                onCheckedChange = onEmbedThumbnail)
            SettingsSwitchRow(index = 2, count = count, title = stringResource(R.string.crop_artwork),
                description = stringResource(R.string.crop_artwork_description), checked = state.cropArtwork,
                enabled = state.embedThumbnail, onCheckedChange = onCropArtwork)
        }
        SettingsSwitchRow(index = count - 3, count = count, title = stringResource(R.string.original_metadata),
            description = stringResource(R.string.original_metadata_description), checked = state.preserveOriginalMetadata,
            onCheckedChange = onPreserveOriginalMetadata)
        SettingsSwitchRow(index = count - 2, count = count, title = stringResource(R.string.skip_archived_downloads),
            checked = state.advancedOptions.skipArchived,
            onCheckedChange = onSkipArchived)
        SettingsSwitchRow(index = count - 1, count = count, title = stringResource(R.string.record_download_history),
            description = stringResource(R.string.record_download_history_hint), checked = state.advancedOptions.recordHistory,
            onCheckedChange = onRecordHistory)
    }
    dev.qtremors.acqua.ui.components.TransferNetworkOptionsButton(state.advancedOptions.network, onNetworkChange)
    if (state.downloadEngine == DownloadEngine.YT_DLP) dev.qtremors.acqua.ui.components.SegmentRemovalButton(state.advancedOptions.removeSegments, onSegmentsChange)
    if (state.downloadEngine == DownloadEngine.YT_DLP) dev.qtremors.acqua.ui.components.FormatPolicyButton(state.advancedOptions.formatPolicy, onFormatPolicyChange)
}
