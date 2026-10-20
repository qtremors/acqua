package dev.qtremors.acqua.ui.image

import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Size
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.asImage
import coil3.fetch.ImageFetchResult
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.request.Options
import coil3.size.Scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import dev.qtremors.acqua.platform.image.BoundedBitmapDecoder
import kotlin.coroutines.CoroutineContext

class AudioAlbumArtFetcher(
    private val file: File,
    private val options: Options,
    private val contentUri: String? = null,
    private val ioContext: CoroutineContext = Dispatchers.IO
) : Fetcher {
    override suspend fun fetch(): FetchResult? = withContext(ioContext) {
        if (file.exists() && file.length() > ThumbnailPolicy.MAX_AUDIO_BYTES) return@withContext null
        val context = options.context
        val targetSize = ThumbnailTargetSize.fromOptions(options)

        val retriever = MediaMetadataRetriever()
        try {
            if (contentUri != null) {
                retriever.setDataSource(context, contentUri.toUri())
            } else {
                if (!file.exists() || !file.isFile) return@withContext null
                retriever.setDataSource(file.absolutePath)
            }
            val art = retriever.embeddedPicture
            if (art != null && art.size <= MAX_EMBEDDED_ART_BYTES) {
                val bitmap = BoundedBitmapDecoder.decode(art, targetSize, targetSize, fill = options.scale == Scale.FILL)
                if (bitmap != null) {
                    return@withContext ImageFetchResult(
                        image = bitmap.asImage(),
                        isSampled = true,
                        dataSource = DataSource.DISK
                    )
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        } finally {
            retriever.release()
        }

        // System thumbnails can be smaller or stale. Use them only when the file
        // has no readable embedded cover, rather than stretching them over it.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && contentUri != null) {
                val bitmap = context.contentResolver.loadThumbnail(contentUri.toUri(), Size(targetSize, targetSize), null)
                return@withContext ImageFetchResult(
                    image = bitmap.asImage(),
                    isSampled = true,
                    dataSource = DataSource.DISK
                )
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
        null
    }

    class Factory : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data.extension.lowercase() in ThumbnailKey.AudioExtensions) {
                AudioAlbumArtFetcher(data, options)
            } else {
                null
            }
        }
    }

    class KeyFactory : Fetcher.Factory<ThumbnailKey> {
        override fun create(data: ThumbnailKey, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data.type == ThumbnailType.Audio) {
                AudioAlbumArtFetcher(data.file, options, data.contentUri)
            } else {
                null
            }
        }
    }

    private companion object {
        const val MAX_EMBEDDED_ART_BYTES = 8 * 1024 * 1024
    }
}
