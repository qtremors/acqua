package dev.qtremors.acqua.feature.downloader

import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.data.network.MediaPreviewCache
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.core.data.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

@Composable
internal fun MediaPreviewCarousel(
    media: List<ResolvedMedia>,
    dimensions: Map<Int, Pair<Int, Int>>,
    contentType: DownloadContentType,
    cropArtwork: Boolean,
    previewCache: MediaPreviewCache,
    useBrowserSessions: Boolean,
    side: Dp,
    onActiveIndex: (Int) -> Unit,
    onBitmapDimensions: (Int, Int, Int) -> Unit,
    selection: Set<Int>? = null,
    selectionEnabled: Boolean = true,
    onSelect: (Int, Boolean) -> Unit = { _, _ -> }
) {
    val mediaKey = media.map { it.url }
    // Start in the middle of the repeated list so swipes wrap in either direction.
    val middle = (Int.MAX_VALUE / 2 / media.size) * media.size
    val listState = key(mediaKey) {
        rememberLazyListState(initialFirstVisibleItemIndex = if (media.size > 1) middle else 0)
    }
    var positioned by remember(mediaKey) { mutableStateOf(false) }
    val density = LocalDensity.current

    fun cardSize(index: Int, source: Pair<Int, Int>? = dimensions[index]): Pair<Dp, Dp> {
        val item = media[index]
        val width = source?.first ?: item.width
        val height = source?.second ?: item.height
        val aspect = when {
            item.isAudioPreview(contentType) && cropArtwork -> 1f
            width > 0 && height > 0 -> width.toFloat() / height
            item.isVideo -> 16f / 9f
            else -> 1f
        }
        return computeCardDimensions(aspect, side, side)
    }

    BoxWithConstraints(Modifier.fillMaxWidth().height(side).testTag("media-preview-container")) {
        val viewportWidth = maxWidth
        fun centerOffset(width: Dp) = with(density) { -((viewportWidth - width) / 2).roundToPx() }

        LaunchedEffect(mediaKey, side, viewportWidth) {
            if (media.size > 1) {
                val page = if (positioned) listState.layoutInfo.centerItemIndex() ?: listState.firstVisibleItemIndex
                    else listState.firstVisibleItemIndex
                listState.scrollToItem(page, centerOffset(cardSize(page % media.size).first))
                positioned = true
            }
        }
        LaunchedEffect(listState, mediaKey) {
            snapshotFlow { listState.layoutInfo.centerItemIndex() }
                .distinctUntilChanged().collect { page ->
                    if (page != null) onActiveIndex(page % media.size)
                }
        }

        @Composable
        fun Preview(page: Int) {
            val index = page % media.size
            val (width, height) = cardSize(index)
            MediaPreviewCard(media[index], index, useBrowserSessions, previewCache, contentType, cropArtwork,
                width, height, modifier = Modifier.previewSelection(
                    selected = selection?.contains(index) == true,
                    selecting = selection != null,
                    enabled = selectionEnabled && media[index].unavailableReason == null,
                    selectLabel = stringResource(R.string.preview_select_item, index + 1),
                    deselectLabel = stringResource(R.string.preview_deselect_item, index + 1),
                    onSelect = { onSelect(index, it) }
                ), selection = selection?.takeIf { media[index].unavailableReason == null }?.contains(index),
                onBitmapDimensions = { sourceWidth, sourceHeight ->
                    val source = sourceWidth to sourceHeight
                    if (media.size > 1 && source != dimensions[index] && !listState.isScrollInProgress) {
                        // A neighboring card changing width must not move the selected item.
                        listState.layoutInfo.centerItemIndex()?.let { centeredPage ->
                            val centeredIndex = centeredPage % media.size
                            val centeredSource = if (centeredIndex == index) source else dimensions[centeredIndex]
                            listState.requestScrollToItem(centeredPage, centerOffset(cardSize(centeredIndex, centeredSource).first))
                        }
                    }
                    onBitmapDimensions(index, sourceWidth, sourceHeight)
                })
        }

        if (media.size == 1) {
            Box(Modifier.fillMaxWidth().height(side), contentAlignment = Alignment.Center) { Preview(0) }
        } else {
            LazyRow(state = listState, modifier = Modifier.fillMaxWidth().height(side),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                flingBehavior = rememberSnapFlingBehavior(listState, snapPosition = SnapPosition.Center)) {
                items(Int.MAX_VALUE) { page -> Preview(page) }
            }
        }
    }
}

private fun LazyListLayoutInfo.centerItemIndex(): Int? {
    val center = (viewportStartOffset + viewportEndOffset) / 2
    return visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index
}
