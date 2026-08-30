package dev.qtremors.acqua.feature.downloader

import android.webkit.CookieManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.qtremors.acqua.R
import dev.qtremors.acqua.data.network.MediaPreviewCache
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.DownloadContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MediaPreviewCard(
    item: ResolvedMedia,
    index: Int,
    useBrowserSessions: Boolean,
    previewCache: MediaPreviewCache,
    contentType: DownloadContentType,
    audioFormat: AudioOutputFormat,
    isSaving: Boolean,
    isSaved: Boolean,
    downloadsEnabled: Boolean,
    onDownload: (width: Int, height: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val audioPreview = item.isAudioPreview(contentType)
    val previewUrl = item.previewUrl
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val previewWidthPixels = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val previewHeightPixels = with(density) { 560.dp.roundToPx() }
    var failed by remember(previewUrl) { mutableStateOf(false) }
    val cookies = remember(
        previewUrl,
        item.requestCookies,
        item.explicitBrowserSessionAuthorized,
        useBrowserSessions
    ) {
        val sessionAccessAllowed = useBrowserSessions || item.explicitBrowserSessionAuthorized
        when {
            !sessionAccessAllowed || previewUrl == null -> null
            WebLink.host(previewUrl) == WebLink.host(item.url) -> item.requestCookies
            else -> CookieManager.getInstance().getCookie(previewUrl)?.takeIf(String::isNotBlank)
        }
    }
    val bitmap by produceState<ImageBitmap?>(null, previewUrl, cookies, previewWidthPixels, previewHeightPixels) {
        val loaded = if (previewUrl == null) null else withContext(Dispatchers.IO) {
            previewCache.loadBitmap(
                item.copy(url = previewUrl, requestCookies = cookies),
                previewWidthPixels,
                previewHeightPixels
            )?.asImageBitmap()
        }
        failed = previewUrl != null && loaded == null
        value = loaded
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val aspectRatio = when {
            bitmap != null && bitmap!!.height > 0 -> bitmap!!.width.toFloat() / bitmap!!.height
            item.width > 0 && item.height > 0 -> item.width.toFloat() / item.height
            else -> 0.75f
        }
        Box(
            Modifier.fillMaxWidth().heightIn(max = 560.dp).aspectRatio(aspectRatio, matchHeightConstraintsFirst = false)
                .clip(RoundedCornerShape(0.dp))
                .background(Color.Black.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            when {
                bitmap != null -> Image(
                    bitmap!!,
                    stringResource(
                        if (audioPreview) R.string.audio else if (item.isVideo) R.string.video_preview else R.string.image_preview
                    ),
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                previewUrl == null || failed -> Icon(
                    if (audioPreview) Icons.Filled.MusicNote else if (item.isVideo) Icons.Filled.Movie else Icons.Filled.Image,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(36.dp)
                )
                else -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            }
            if (item.isVideo && bitmap != null) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (audioPreview) Icons.Filled.MusicNote else Icons.Filled.PlayArrow,
                        stringResource(if (audioPreview) R.string.audio else R.string.video),
                        tint = Color.White
                    )
                }
            }
            Text(
                stringResource(
                    R.string.media_index_type,
                    index + 1,
                    stringResource(if (audioPreview) R.string.audio else if (item.isVideo) R.string.video else R.string.photo)
                ),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
            IconButton(
                onClick = {
                    onDownload(
                        if (item.isVideo) item.width else bitmap?.width ?: item.width,
                        if (item.isVideo) item.height else bitmap?.height ?: item.height
                    )
                },
                enabled = downloadsEnabled && !isSaved,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(32.dp)
                    .clip(CircleShape).background(
                        if (isSaved) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                        else Color.Black.copy(alpha = 0.5f)
                    )
            ) {
                when {
                    isSaving -> CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    isSaved -> Icon(Icons.Filled.Check, stringResource(R.string.saved))
                    else -> Icon(Icons.Filled.Download, stringResource(R.string.download_item), tint = Color.White)
                }
            }
        }
        Spacer(Modifier.size(6.dp))
        val width = if (item.isVideo) item.width else bitmap?.width ?: item.width
        val height = if (item.isVideo) item.height else bitmap?.height ?: item.height
        val resolution = if (width > 0 && height > 0) "${width}x$height" else ""
        val locale = configuration.locales[0]
        val size = item.fileSize?.let { String.format(locale, "%.1f MB", it / (1024.0 * 1024.0)) }.orEmpty()
        val details = if (audioPreview) {
            listOf(when (audioFormat) {
                AudioOutputFormat.ORIGINAL -> stringResource(R.string.original_audio)
                AudioOutputFormat.M4A -> "M4A"
                AudioOutputFormat.MP3 -> "MP3"
            })
        } else listOf(resolution, size).filter(String::isNotEmpty)
        if (details.isNotEmpty()) {
            Text(
                details.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

internal fun ResolvedMedia.isAudioPreview(contentType: DownloadContentType): Boolean =
    backend == MediaBackend.YT_DLP && contentType == DownloadContentType.AUDIO
