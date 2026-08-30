package dev.qtremors.acqua.ui.image

import android.media.MediaMetadataRetriever
import android.util.Size
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.CoroutineContext

class VideoThumbnailFetcher(
    private val file: File,
    private val options: Options,
    private val contentUri: String? = null,
    private val ioContext: CoroutineContext = Dispatchers.IO
) : Fetcher {
    companion object {
        private val semaphore = Semaphore(2)
    }

    override suspend fun fetch(): FetchResult? = semaphore.withPermit {
        withContext(ioContext) {
            val context = options.context
            val targetSize = ThumbnailTargetSize.fromOptions(options)

            try {
                contentUri?.let { uri ->
                    val bitmap = context.contentResolver.loadThumbnail(uri.toUri(), Size(targetSize, targetSize), null)
                    return@withContext DrawableResult(
                        drawable = bitmap.toDrawable(context.resources),
                        isSampled = true,
                        dataSource = DataSource.DISK
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }

            if (contentUri == null && (!file.exists() || !file.isFile)) return@withContext null

            val retriever = MediaMetadataRetriever()
            try {
                if (contentUri != null) {
                    retriever.setDataSource(context, contentUri.toUri())
                } else {
                    retriever.setDataSource(file.absolutePath)
                }
                val bitmap = retriever.getScaledFrameAtTime(
                    -1,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    targetSize,
                    targetSize
                ) ?: return@withContext null

                DrawableResult(
                    drawable = bitmap.toDrawable(context.resources),
                    isSampled = true,
                    dataSource = DataSource.DISK
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            } finally {
                retriever.release()
            }
        }
    }

    class Factory : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data.extension.lowercase() in ThumbnailKey.VideoExtensions) {
                VideoThumbnailFetcher(data, options)
            } else {
                null
            }
        }
    }

    class KeyFactory : Fetcher.Factory<ThumbnailKey> {
        override fun create(data: ThumbnailKey, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data.type == ThumbnailType.Video) {
                VideoThumbnailFetcher(data.file, options, data.contentUri)
            } else {
                null
            }
        }
    }
}
