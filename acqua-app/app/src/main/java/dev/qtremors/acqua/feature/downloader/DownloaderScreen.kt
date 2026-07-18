package dev.qtremors.acqua.feature.downloader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.R
import dev.qtremors.acqua.data.network.MediaDownloader
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.platform.HapticSignal
import dev.qtremors.acqua.platform.performHaptic

@Composable
fun DownloaderScreen(
    viewModel: DownloaderViewModel,
    mediaDownloader: MediaDownloader,
    useBrowserSessions: Boolean,
    sessionsInitialized: Boolean,
    browserRequestRevision: Int,
    resolveInBrowser: suspend (String) -> List<ResolvedMedia>,
    requestStorageAccess: (() -> Unit) -> Unit,
    onOpenBrowser: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(state.url, sessionsInitialized, useBrowserSessions, browserRequestRevision) {
        if (sessionsInitialized) {
            viewModel.resolve(useBrowserSessions, browserRequestRevision, resolveInBrowser)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                DownloaderEvent.ResolutionComplete -> context.performHaptic(HapticSignal.CLICK)
                DownloaderEvent.DownloadComplete -> context.performHaptic(HapticSignal.COMPLETE)
                DownloaderEvent.ItemSaved -> {
                    context.performHaptic(HapticSignal.COMPLETE)
                    Toast.makeText(context, R.string.saved_to_downloads, Toast.LENGTH_SHORT).show()
                }
                is DownloaderEvent.ItemSaveFailed -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Card(
            Modifier.fillMaxWidth(), RoundedCornerShape(24.dp),
            CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh)
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    stringResource(R.string.paste_media_link),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = state.url,
                    onValueChange = viewModel::updateUrl,
                    label = { Text(stringResource(R.string.media_link)) },
                    placeholder = { Text(stringResource(R.string.media_link_hint)) },
                    trailingIcon = {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                                ?.takeIf(String::isNotEmpty)?.let(viewModel::updateUrl)
                        }) { Icon(Icons.Filled.ContentPaste, stringResource(R.string.paste), tint = colors.primary) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    enabled = !state.isSaving,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        focusedLabelColor = colors.primary,
                        cursorColor = colors.primary
                    )
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        when (state.validity) {
            LinkValidity.EMPTY -> LinkMessageCard(
                icon = { Icon(Icons.Filled.Download, stringResource(R.string.ready), Modifier.size(30.dp)) },
                title = stringResource(R.string.ready_to_download),
                message = stringResource(R.string.supported_link_guidance)
            )
            LinkValidity.INVALID -> LinkMessageCard(
                error = true,
                icon = { Icon(Icons.Filled.Warning, stringResource(R.string.invalid), Modifier.size(30.dp)) },
                title = stringResource(R.string.invalid_link),
                message = stringResource(R.string.valid_link_guidance)
            )
            LinkValidity.VALID -> ValidLinkContent(
                state = state,
                mediaDownloader = mediaDownloader,
                useBrowserSessions = useBrowserSessions,
                onDownloadAll = {
                    requestStorageAccess { viewModel.downloadAll(useBrowserSessions, resolveInBrowser) }
                },
                onDownloadOne = { item, index, width, height ->
                    requestStorageAccess { viewModel.downloadOne(item, index, width, height) }
                },
                onDismissError = viewModel::dismissError,
                onCopyError = { error ->
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("AcquaError", error))
                },
                onOpenBrowser = { onOpenBrowser(state.url) }
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LinkMessageCard(
    error: Boolean = false,
    icon: @Composable () -> Unit,
    title: String,
    message: String
) {
    val colors = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth(), RoundedCornerShape(24.dp),
        CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(60.dp).clip(CircleShape)
                    .background(if (error) colors.errorContainer else colors.primaryContainer),
                contentAlignment = Alignment.Center
            ) { icon() }
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(6.dp))
            Text(message, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ValidLinkContent(
    state: DownloaderUiState,
    mediaDownloader: MediaDownloader,
    useBrowserSessions: Boolean,
    onDownloadAll: () -> Unit,
    onDownloadOne: (ResolvedMedia, Int, Int, Int) -> Unit,
    onDismissError: () -> Unit,
    onCopyError: (String) -> Unit,
    onOpenBrowser: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    AnimatedVisibility(state.isResolving, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(Modifier.fillMaxWidth().height(8.dp))
    }
    Card(
        Modifier.fillMaxWidth().padding(top = 12.dp), RoundedCornerShape(24.dp),
        CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh)
    ) {
        Button(
            onClick = onDownloadAll,
            modifier = Modifier.fillMaxWidth().padding(24.dp).height(52.dp),
            enabled = !state.isSaving,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
        ) {
            when {
                state.isSaving -> {
                    CircularProgressIndicator(Modifier.size(20.dp), color = colors.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.size(12.dp))
                    val total = state.media?.size ?: 0
                    Text(
                        if (total > 1) pluralStringResource(
                            R.plurals.saving_progress,
                            total,
                            state.savingIndex,
                            total
                        ) else stringResource(R.string.saving)
                    )
                }
                state.saved -> Text(stringResource(R.string.saved_to_downloads), fontWeight = FontWeight.Bold)
                else -> {
                    Icon(Icons.Filled.Download, stringResource(R.string.download))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.download_media), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    state.media?.let { media ->
        Card(
            Modifier.fillMaxWidth().padding(top = 16.dp), RoundedCornerShape(24.dp),
            CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    if (media.size > 1) pluralStringResource(
                        R.plurals.files_found,
                        media.size,
                        media.size
                    ) else stringResource(R.string.preview),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    media.forEachIndexed { index, item ->
                        MediaPreviewCard(
                            item, index, useBrowserSessions, mediaDownloader,
                            isSaving = state.savingItemIndex == index,
                            isSaved = state.savedItemIndex == index,
                            onDownload = { width, height -> onDownloadOne(item, index, width, height) }
                        )
                    }
                }
            }
        }
    }
    state.error?.let { error ->
        Card(
            Modifier.fillMaxWidth().padding(top = 16.dp), RoundedCornerShape(18.dp),
            CardDefaults.cardColors(containerColor = colors.errorContainer)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(stringResource(R.string.could_not_resolve_media), fontWeight = FontWeight.Bold)
                    Row {
                        IconButton({ onCopyError(error) }) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.copy_error)) }
                        TextButton(onDismissError) { Text(stringResource(R.string.dismiss)) }
                    }
                }
                SelectionContainer { Text(error, style = MaterialTheme.typography.bodySmall) }
                if (state.showBrowserAction) {
                    Button(onOpenBrowser, Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Icon(Icons.Filled.Lock, stringResource(R.string.open_browser))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.open_in_browser))
                    }
                }
            }
        }
    }
}
