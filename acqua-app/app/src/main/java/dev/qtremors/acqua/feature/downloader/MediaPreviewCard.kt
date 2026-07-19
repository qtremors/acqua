package dev.qtremors.acqua.feature.downloader

import android.graphics.BitmapFactory
import android.webkit.CookieManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.qtremors.acqua.R
import dev.qtremors.acqua.data.network.MediaDownloader
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.WebLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MediaPreviewCard(
    item: ResolvedMedia,
    index: Int,
    useBrowserSessions: Boolean,
    mediaDownloader: MediaDownloader,
    isSaving: Boolean,
    isSaved: Boolean,
    downloadsEnabled: Boolean,
    onDownload: (width: Int, height: Int) -> Unit
) {
    val previewUrl = item.previewUrl
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
    val bitmap by produceState<ImageBitmap?>(null, previewUrl, cookies) {
        value = if (previewUrl == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val bytes = mediaDownloader.fetchBytes(item.copy(url = previewUrl, requestCookies = cookies))
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.onFailure { failed = true }.getOrNull()
        }
    }
    Column(Modifier.width(150.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val aspectRatio = when {
            bitmap != null && bitmap!!.height > 0 -> bitmap!!.width.toFloat() / bitmap!!.height
            item.width > 0 && item.height > 0 -> item.width.toFloat() / item.height
            else -> 0.75f
        }
        Box(
            Modifier.width(150.dp).aspectRatio(aspectRatio).clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            when {
                bitmap != null -> Image(
                    bitmap!!,
                    stringResource(if (item.isVideo) R.string.video_preview else R.string.image_preview),
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                previewUrl == null || failed -> Icon(
                    if (item.isVideo) Icons.Filled.Movie else Icons.Filled.Image,
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
                ) { Icon(Icons.Filled.PlayArrow, stringResource(R.string.video), tint = Color.White) }
            }
            Text(
                stringResource(
                    R.string.media_index_type,
                    index + 1,
                    stringResource(if (item.isVideo) R.string.video else R.string.photo)
                ),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
            IconButton(
                onClick = { onDownload(bitmap?.width ?: item.width, bitmap?.height ?: item.height) },
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
        val width = bitmap?.width ?: item.width
        val height = bitmap?.height ?: item.height
        val resolution = if (width > 0 && height > 0) "${width}x$height" else ""
        val locale = LocalConfiguration.current.locales[0]
        val size = item.fileSize?.let { String.format(locale, "%.1f MB", it / (1024.0 * 1024.0)) }.orEmpty()
        if (resolution.isNotEmpty() || size.isNotEmpty()) {
            Text(
                listOf(resolution, size).filter(String::isNotEmpty).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}
