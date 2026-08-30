package dev.qtremors.acqua.ui.image

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
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
import kotlinx.coroutines.withContext
import java.io.File
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

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && contentUri != null) {
                val bitmap = context.contentResolver.loadThumbnail(contentUri.toUri(), Size(targetSize, targetSize), null)
                return@withContext DrawableResult(
                    drawable = bitmap.toDrawable(context.resources),
                    isSampled = true,
                    dataSource = DataSource.DISK
                )
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        }

        val retriever = MediaMetadataRetriever()
        try {
            if (contentUri != null) {
                retriever.setDataSource(context, contentUri.toUri())
            } else {
                if (!file.exists() || !file.isFile) return@withContext null
                retriever.setDataSource(file.absolutePath)
            }
            val art = retriever.embeddedPicture
            if (art != null) {
                val decodeOptions = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(art, 0, art.size, decodeOptions)

                var sampleSize = 1
                while (decodeOptions.outWidth / sampleSize > targetSize ||
                    decodeOptions.outHeight / sampleSize > targetSize
                ) {
                    sampleSize *= 2
                }

                decodeOptions.apply {
                    inJustDecodeBounds = false
                    inSampleSize = sampleSize
                }

                val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size, decodeOptions)
                    ?: return@withContext null
                DrawableResult(
                    drawable = bitmap.toDrawable(context.resources),
                    isSampled = true,
                    dataSource = DataSource.DISK
                )
            } else {
                null
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        } finally {
            retriever.release()
        }
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
}
