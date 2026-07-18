package dev.qtremors.acqua.platform.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.annotation.RequiresApi
import dev.qtremors.acqua.data.history.HistoryEntry
import dev.qtremors.acqua.data.history.HistoryRepository
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.downloader.FilenameFormatter
import dev.qtremors.acqua.data.network.MediaDownloader
import dev.qtremors.acqua.domain.ResolvedMedia
import java.io.File
import java.util.UUID

class MediaStorage(
    context: Context,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: AppSettingsRepository,
    private val mediaDownloader: MediaDownloader
) {
    private val appContext = context.applicationContext

    fun save(item: ResolvedMedia, index: Int, sourceUrl: String): Uri {
        val settings = settingsRepository.downloadSettings()
        val baseFolder = settingsRepository.sanitizedBaseFolder(settings.baseFolder)
        val category = if (item.isVideo) "Videos" else "Images"
        val relativePath = buildString {
            append("Download/")
            append(baseFolder)
            if (settings.categorizeMedia) append("/$category")
        }
        val extension = item.fileExtension ?: if (item.isVideo) "mp4" else "jpg"
        val mimeType = item.mimeType ?: if (item.isVideo) "video/mp4" else "image/jpeg"
        val fileName = FilenameFormatter.format(
            settings.filenamePattern,
            item.username,
            item.width,
            item.height,
            index,
            extension
        )
        val requestCookies = item.requestCookies.takeIf { settingsRepository.useBrowserSessions() }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(item, sourceUrl, relativePath, fileName, mimeType, requestCookies)
        } else {
            saveLegacy(item, sourceUrl, baseFolder, category, fileName, mimeType, requestCookies, settings.categorizeMedia)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveWithMediaStore(
        item: ResolvedMedia,
        sourceUrl: String,
        relativePath: String,
        fileName: String,
        mimeType: String,
        requestCookies: String?
    ): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = appContext.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create the destination file in Downloads.")

        return try {
            val bytesDownloaded = resolver.openOutputStream(uri)?.use { output ->
                mediaDownloader.downloadToStream(item, output, requestCookies)
            } ?: error("Could not open the destination media file.")

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            recordDownload(item, sourceUrl, uri, fileName, mimeType, bytesDownloaded)
            uri
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(
        item: ResolvedMedia,
        sourceUrl: String,
        baseFolder: String,
        category: String,
        fileName: String,
        mimeType: String,
        requestCookies: String?,
        categorizeMedia: Boolean
    ): Uri {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDirectory = File(downloads, if (categorizeMedia) "$baseFolder/$category" else baseFolder)
        check(targetDirectory.mkdirs() || targetDirectory.isDirectory) {
            "Could not create the configured Downloads subfolder."
        }
        val file = File(targetDirectory, fileName)

        return try {
            val bytesDownloaded = file.outputStream().use { output ->
                mediaDownloader.downloadToStream(item, output, requestCookies)
            }
            val uri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file
            )
            recordDownload(item, sourceUrl, uri, fileName, mimeType, bytesDownloaded)
            uri
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    private fun recordDownload(
        item: ResolvedMedia,
        sourceUrl: String,
        uri: Uri,
        fileName: String,
        mimeType: String,
        bytesDownloaded: Long
    ) {
        historyRepository.add(
            HistoryEntry(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                url = sourceUrl,
                fileName = fileName,
                fileUri = uri.toString(),
                isVideo = item.isVideo,
                mimeType = mimeType,
                sizeBytes = bytesDownloaded,
                isDownloaded = true,
                thumbnailUrl = item.thumbnailUrl ?: item.previewUrl
            )
        )
    }
}
