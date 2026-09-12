package dev.qtremors.acqua.feature.downloader

import android.webkit.CookieManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import dev.qtremors.acqua.R
import dev.qtremors.acqua.data.network.MediaPreviewCache
import dev.qtremors.acqua.domain.MediaBackend
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.WebLink
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.downloader.FilenameFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun computeCardDimensions(
    aspectRatio: Float,
    maxHeight: Dp,
    maxWidth: Dp
): Pair<Dp, Dp> {
    val widthAtMaxHeight = maxHeight * aspectRatio
    return if (widthAtMaxHeight <= maxWidth) {
        Pair(widthAtMaxHeight, maxHeight)
    } else {
        val heightAtMaxWidth = maxWidth / aspectRatio
        Pair(maxWidth, heightAtMaxWidth)
    }
}

internal data class ParallelMarqueeTextItem(
    val text: String,
    val style: TextStyle,
    val color: Color,
    val topSpacing: Dp = 0.dp
)

@Composable
internal fun ParallelMarqueeTextColumn(
    items: List<ParallelMarqueeTextItem>,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val containerWidth = maxWidth
        val containerWidthPx = with(density) { containerWidth.toPx() }

        val itemWidths = remember(items, density) {
            items.map { item ->
                with(density) {
                    textMeasurer.measure(
                        text = item.text,
                        style = item.style
                    ).size.width.toDp()
                }
            }
        }

        val maxContentWidth = itemWidths.maxOrNull() ?: 0.dp
        val maxContentWidthPx = with(density) { maxContentWidth.toPx() }
        val maxOverflowPx = (maxContentWidthPx - containerWidthPx).coerceAtLeast(0f)

        val gap = 48.dp
        val gapPx = with(density) { gap.toPx() }
        val lapDistancePx = maxContentWidthPx + gapPx

        val scrollOffset = remember { Animatable(0f) }

        if (maxOverflowPx > 0f && lapDistancePx > 0f) {
            val velocityPxPerSec = with(density) { 26.dp.toPx() }
            val durationMs = ((lapDistancePx / velocityPxPerSec) * 1000f).roundToInt().coerceAtLeast(1000)

            LaunchedEffect(lapDistancePx, durationMs) {
                scrollOffset.snapTo(0f)
                delay(1000)
                while (isActive) {
                    scrollOffset.snapTo(0f)
                    scrollOffset.animateTo(
                        targetValue = lapDistancePx,
                        animationSpec = tween(
                            durationMillis = durationMs,
                            easing = LinearEasing
                        )
                    )
                }
            }
        } else {
            LaunchedEffect(Unit) {
                scrollOffset.snapTo(0f)
            }
        }

        val currentScroll = if (lapDistancePx > 0f) scrollOffset.value % lapDistancePx else 0f

        Column(modifier = Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, item ->
                if (index > 0 && item.topSpacing > 0.dp) {
                    Spacer(Modifier.height(item.topSpacing))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                ) {
                    Text(
                        text = item.text,
                        modifier = Modifier
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(
                                    constraints.copy(
                                        minWidth = 0,
                                        maxWidth = Constraints.Infinity
                                    )
                                )
                                layout(constraints.maxWidth, placeable.height) {
                                    placeable.placeRelative(0, 0)
                                }
                            }
                            .graphicsLayer {
                                translationX = -currentScroll
                            },
                        style = item.style,
                        color = item.color,
                        maxLines = 1,
                        softWrap = false
                    )

                    if (maxOverflowPx > 0f) {
                        Text(
                            text = item.text,
                            modifier = Modifier
                                .layout { measurable, constraints ->
                                    val placeable = measurable.measure(
                                        constraints.copy(
                                            minWidth = 0,
                                            maxWidth = Constraints.Infinity
                                        )
                                    )
                                    layout(constraints.maxWidth, placeable.height) {
                                        placeable.placeRelative(0, 0)
                                    }
                                }
                                .graphicsLayer {
                                    translationX = -currentScroll + lapDistancePx
                                },
                            style = item.style,
                            color = item.color,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MediaPreviewCard(
    item: ResolvedMedia,
    index: Int,
    useBrowserSessions: Boolean,
    previewCache: MediaPreviewCache,
    contentType: DownloadContentType,
    cardWidth: Dp,
    cardHeight: Dp,
    modifier: Modifier = Modifier,
    onBitmapDimensions: (width: Int, height: Int) -> Unit = { _, _ -> }
) {
    val audioPreview = item.isAudioPreview(contentType)
    val previewUrl = item.previewUrl
    val density = LocalDensity.current
    val previewWidthPixels = with(density) { cardWidth.roundToPx() }
    val previewHeightPixels = with(density) { cardHeight.roundToPx() }
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
        if (loaded != null) {
            onBitmapDimensions(loaded.width, loaded.height)
        }
    }
    val isVideo = item.isVideo && !audioPreview

    Card(
        modifier = modifier.size(cardWidth, cardHeight),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                bitmap != null -> {
                    Image(
                        bitmap = bitmap!!,
                        contentDescription = stringResource(
                            if (audioPreview) R.string.audio else if (item.isVideo) R.string.video_preview else R.string.image_preview
                        ),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                previewUrl == null || failed -> {
                    Icon(
                        if (audioPreview) Icons.Filled.MusicNote else if (item.isVideo) Icons.Filled.Movie else Icons.Filled.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(48.dp)
                    )
                }
                else -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (isVideo && bitmap != null) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            stringResource(R.string.video),
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MediaMetadataContainer(
    item: ResolvedMedia,
    index: Int,
    totalCount: Int,
    contentType: DownloadContentType,
    audioFormat: AudioOutputFormat,
    filenamePattern: String,
    audioFilenamePattern: String = FilenameFormatter.DEFAULT_AUDIO_PATTERN,
    isSaving: Boolean,
    isSaved: Boolean,
    downloadsEnabled: Boolean,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
    fallbackTitle: String? = null,
    fallbackAuthor: String? = null,
    bitmapWidth: Int = 0,
    bitmapHeight: Int = 0
) {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val colors = MaterialTheme.colorScheme
    val audioPreview = item.isAudioPreview(contentType)

    val width = if (item.isVideo) item.width else if (bitmapWidth > 0) bitmapWidth else item.width
    val height = if (item.isVideo) item.height else if (bitmapHeight > 0) bitmapHeight else item.height
    val resolution = if (width > 0 && height > 0) "${width}x$height" else ""
    val size = item.fileSize?.let { String.format(locale, "%.1f MB", it / (1024.0 * 1024.0)) }.orEmpty()
    val details = if (audioPreview) {
        val formatLabel = when {
            item.backend == MediaBackend.YT_DLP -> when (audioFormat) {
                AudioOutputFormat.ORIGINAL -> stringResource(R.string.original_audio)
                AudioOutputFormat.M4A -> "M4A"
                AudioOutputFormat.MP3 -> "MP3"
            }
            !item.fileExtension.isNullOrBlank() -> item.fileExtension.uppercase(locale)
            else -> "AUDIO"
        }
        listOf(formatLabel, size).filter(String::isNotEmpty)
    } else {
        listOf(resolution, size).filter(String::isNotEmpty)
    }

    val extension = when {
        audioPreview -> when {
            item.backend == MediaBackend.YT_DLP -> when (audioFormat) {
                AudioOutputFormat.ORIGINAL -> "m4a"
                AudioOutputFormat.M4A -> "m4a"
                AudioOutputFormat.MP3 -> "mp3"
            }
            !item.fileExtension.isNullOrBlank() -> item.fileExtension.lowercase(locale)
            else -> "m4a"
        }
        item.kind == MediaKind.VIDEO || item.isVideo -> "mp4"
        item.kind == MediaKind.AUDIO -> "m4a"
        else -> item.fileExtension?.lowercase(locale)?.takeIf { it in listOf("jpg", "jpeg", "png", "webp", "gif") } ?: "jpg"
    }

    val activePattern = if (audioPreview) audioFilenamePattern else filenamePattern
    val actualFileName = FilenameFormatter.format(
        pattern = activePattern,
        username = item.username ?: fallbackAuthor,
        width = width,
        height = height,
        index = index,
        itemCount = totalCount,
        fileExtension = extension,
        title = item.title ?: fallbackTitle,
        artist = item.artist,
        album = item.album
    )

    val author = item.username ?: fallbackAuthor

    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = colors.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (totalCount > 1) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.secondaryContainer,
                            modifier = Modifier.height(22.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    stringResource(R.string.preview_page_count, index + 1, totalCount),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                    color = colors.onSecondaryContainer
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.surfaceContainerHighest,
                        modifier = Modifier.height(22.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                if (audioPreview) Icons.Filled.MusicNote else if (item.isVideo) Icons.Filled.Movie else Icons.Filled.Image,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                stringResource(
                                    if (audioPreview) R.string.audio else if (item.isVideo) R.string.video else R.string.photo
                                ),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
                                color = colors.onSurface
                            )
                        }
                    }

                    if (details.isNotEmpty()) {
                        Text(
                            details.joinToString(" · "),
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .basicMarquee(),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = colors.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                if (audioPreview) {
                    val displayTitle = item.title?.takeIf(String::isNotBlank)
                        ?: fallbackTitle?.takeIf(String::isNotBlank)
                        ?: actualFileName
                    val artist = item.artist?.takeIf(String::isNotBlank)
                        ?: item.username?.takeIf(String::isNotBlank)
                        ?: fallbackAuthor?.takeIf(String::isNotBlank)
                    val album = item.album?.takeIf(String::isNotBlank)
                    val artistAlbumSubtitle = when {
                        !artist.isNullOrBlank() && !album.isNullOrBlank() -> "$artist · $album"
                        !artist.isNullOrBlank() -> artist
                        !album.isNullOrBlank() -> album
                        else -> null
                    }

                    val marqueeItems = buildList {
                        add(
                            ParallelMarqueeTextItem(
                                text = displayTitle,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                color = colors.onSurface
                            )
                        )
                        if (!artistAlbumSubtitle.isNullOrBlank()) {
                            add(
                                ParallelMarqueeTextItem(
                                    text = artistAlbumSubtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = colors.onSurfaceVariant,
                                    topSpacing = 1.dp
                                )
                            )
                        }
                        add(
                            ParallelMarqueeTextItem(
                                text = actualFileName,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                                topSpacing = 1.dp
                            )
                        )
                    }

                    ParallelMarqueeTextColumn(items = marqueeItems)
                } else {
                    val marqueeItems = buildList {
                        add(
                            ParallelMarqueeTextItem(
                                text = actualFileName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                color = colors.onSurface
                            )
                        )
                        if (!author.isNullOrBlank()) {
                            add(
                                ParallelMarqueeTextItem(
                                    text = "@$author",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = colors.onSurfaceVariant,
                                    topSpacing = 1.dp
                                )
                            )
                        }
                    }

                    ParallelMarqueeTextColumn(items = marqueeItems)
                }
            }

            Spacer(Modifier.width(10.dp))

            FilledTonalIconButton(
                onClick = onDownload,
                enabled = downloadsEnabled && !isSaved,
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (isSaved) colors.primaryContainer else colors.surfaceContainerHighest,
                    contentColor = if (isSaved) colors.onPrimaryContainer else colors.onSurfaceVariant
                )
            ) {
                when {
                    isSaving -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = colors.primary,
                        strokeWidth = 2.dp
                    )
                    isSaved -> Icon(
                        Icons.Filled.Check,
                        stringResource(R.string.saved),
                        modifier = Modifier.size(20.dp)
                    )
                    else -> Icon(
                        Icons.Filled.Download,
                        stringResource(R.string.download_item),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

internal fun ResolvedMedia.isAudioPreview(contentType: DownloadContentType): Boolean =
    isAudio || (backend == MediaBackend.YT_DLP && contentType == DownloadContentType.AUDIO)
