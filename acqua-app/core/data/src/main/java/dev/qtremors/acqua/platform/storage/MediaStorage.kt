package dev.qtremors.acqua.platform.storage

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.FileProvider
import androidx.annotation.RequiresApi
import dev.qtremors.acqua.data.history.HistoryEntry
import dev.qtremors.acqua.data.history.HistoryRepository
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.downloader.FilenameFormatter
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.data.network.HttpMediaClient
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.MediaKind
import java.io.File
import java.util.UUID

class MediaStorage(
    context: Context,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: AppSettingsRepository,
    private val mediaDownloader: HttpMediaClient
) {
    private val appContext = context.applicationContext

    suspend fun save(
        item: ResolvedMedia,
        index: Int,
        sourceUrl: String,
        itemCount: Int = 1,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Uri {
        val settings = settingsRepository.downloadSettings()
        val baseFolder = settingsRepository.sanitizedBaseFolder(settings.baseFolder)
        val category = when (item.kind) {
            MediaKind.VIDEO -> "Videos"
            MediaKind.AUDIO -> "Audio"
            MediaKind.IMAGE -> "Images"
        }
        val relativePath = buildString {
            append("Download/")
            append(baseFolder)
            if (settings.categorizeMedia) append("/$category")
        }
        val extension = item.fileExtension ?: when (item.kind) {
            MediaKind.VIDEO -> "mp4"
            MediaKind.AUDIO -> "m4a"
            MediaKind.IMAGE -> "jpg"
        }
        val mimeType = item.mimeType ?: when (item.kind) {
            MediaKind.VIDEO -> "video/mp4"
            MediaKind.AUDIO -> "audio/mp4"
            MediaKind.IMAGE -> "image/jpeg"
        }
        val isAudio = item.kind == MediaKind.AUDIO
        val fileName = FilenameFormatter.format(
            pattern = if (isAudio) settings.audioFilenamePattern else settings.filenamePattern,
            username = item.username,
            width = if (isAudio) 0 else item.width,
            height = if (isAudio) 0 else item.height,
            index = index,
            itemCount = itemCount,
            fileExtension = extension,
            title = item.title,
            artist = item.artist,
            album = item.album
        )
        val requestCookies = item.requestCookies.takeIf {
            item.explicitBrowserSessionAuthorized || settingsRepository.useBrowserSessions()
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(item, sourceUrl, relativePath, fileName, mimeType, requestCookies, onProgress)
        } else {
            saveLegacy(
                item,
                sourceUrl,
                baseFolder,
                category,
                fileName,
                mimeType,
                requestCookies,
                settings.categorizeMedia,
                onProgress
            )
        }
    }

    suspend fun saveProcessedFile(
        sourceFile: File,
        media: ResolvedMedia,
        contentType: DownloadContentType,
        sourceUrl: String
    ): Uri {
        require(sourceFile.isFile && sourceFile.length() > 0L) { "The processed media file is empty." }
        val settings = settingsRepository.downloadSettings()
        val baseFolder = settingsRepository.sanitizedBaseFolder(settings.baseFolder)
        val isAudio = contentType == DownloadContentType.AUDIO || media.isAudio
        val category = if (isAudio) "Audio" else "Videos"
        val extension = sourceFile.extension.lowercase().ifBlank { if (isAudio) "m4a" else "mp4" }
        val mimeType = mimeTypeFor(extension, isAudio)
        val fileName = FilenameFormatter.format(
            pattern = if (isAudio) settings.audioFilenamePattern else settings.filenamePattern,
            username = media.username,
            width = if (isAudio) 0 else media.width,
            height = if (isAudio) 0 else media.height,
            index = 0,
            itemCount = 1,
            fileExtension = extension,
            title = media.title,
            artist = media.artist,
            album = media.album
        )
        val relativePath = buildString {
            append("Download/")
            append(baseFolder)
            if (settings.categorizeMedia) append("/$category")
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = appContext.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create the destination file in Downloads.")
            PendingMediaStoreRegistry.track(appContext, uri)
            try {
                resolver.openOutputStream(uri)?.use { output ->
                    sourceFile.inputStream().use { input -> input.copyTo(output) }
                } ?: error("Could not open the destination media file.")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                values.putDownloadTimestamp(System.currentTimeMillis(), includeDateTaken = !isAudio)
                resolver.update(uri, values, null, null)
                warmThumbnail(uri, enabled = !isAudio)
                val finalName = queryDisplayName(uri, fileName)
                recordProcessedDownload(sourceUrl, uri, finalName, mimeType, sourceFile.length(), !isAudio)
                PendingMediaStoreRegistry.complete(appContext, uri)
                uri
            } catch (error: Exception) {
                resolver.delete(uri, null, null)
                PendingMediaStoreRegistry.complete(appContext, uri)
                throw error
            }
        } else {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetDirectory = File(
                downloads,
                if (settings.categorizeMedia) "$baseFolder/$category" else baseFolder
            )
            check(targetDirectory.mkdirs() || targetDirectory.isDirectory) {
                "Could not create the configured Downloads subfolder."
            }
            val target = uniqueFile(targetDirectory, fileName)
            sourceFile.copyTo(target, overwrite = true)
            target.setLastModified(System.currentTimeMillis())
            scanLegacyFile(target, mimeType)
            val uri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                target
            )
            recordProcessedDownload(sourceUrl, uri, target.name, mimeType, target.length(), !isAudio)
            uri
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun saveWithMediaStore(
        item: ResolvedMedia,
        sourceUrl: String,
        relativePath: String,
        fileName: String,
        mimeType: String,
        requestCookies: String?,
        onProgress: (Long, Long) -> Unit
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
        PendingMediaStoreRegistry.track(appContext, uri)

        return try {
            val bytesDownloaded = resolver.openOutputStream(uri)?.use { output ->
                mediaDownloader.downloadToStreamCancellable(item, output, requestCookies, onProgress)
            } ?: error("Could not open the destination media file.")

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            values.putDownloadTimestamp(System.currentTimeMillis(), includeDateTaken = !item.isAudio)
            resolver.update(uri, values, null, null)
            warmThumbnail(uri, enabled = !item.isAudio)
            val finalName = queryDisplayName(uri, fileName)
            recordDownload(item, sourceUrl, uri, finalName, mimeType, bytesDownloaded)
            PendingMediaStoreRegistry.complete(appContext, uri)
            uri
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            PendingMediaStoreRegistry.complete(appContext, uri)
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun saveLegacy(
        item: ResolvedMedia,
        sourceUrl: String,
        baseFolder: String,
        category: String,
        fileName: String,
        mimeType: String,
        requestCookies: String?,
        categorizeMedia: Boolean,
        onProgress: (Long, Long) -> Unit
    ): Uri {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDirectory = File(downloads, if (categorizeMedia) "$baseFolder/$category" else baseFolder)
        check(targetDirectory.mkdirs() || targetDirectory.isDirectory) {
            "Could not create the configured Downloads subfolder."
        }
        val file = uniqueFile(targetDirectory, fileName)

        return try {
            val bytesDownloaded = file.outputStream().use { output ->
                mediaDownloader.downloadToStreamCancellable(item, output, requestCookies, onProgress)
            }
            file.setLastModified(System.currentTimeMillis())
            scanLegacyFile(file, mimeType)
            val uri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file
            )
            recordDownload(item, sourceUrl, uri, file.name, mimeType, bytesDownloaded)
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
                thumbnailUrl = null
            )
        )
    }

    private fun recordProcessedDownload(
        sourceUrl: String,
        uri: Uri,
        fileName: String,
        mimeType: String,
        bytesDownloaded: Long,
        isVideo: Boolean
    ) {
        historyRepository.add(
            HistoryEntry(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                url = sourceUrl,
                fileName = fileName,
                fileUri = uri.toString(),
                isVideo = isVideo,
                mimeType = mimeType,
                sizeBytes = bytesDownloaded,
                isDownloaded = true,
                thumbnailUrl = null
            )
        )
    }

    private fun mimeTypeFor(extension: String, audio: Boolean): String = when (extension) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "opus" -> "audio/opus"
        "ogg", "oga" -> "audio/ogg"
        "aac" -> "audio/aac"
        "wav" -> "audio/wav"
        "webm" -> if (audio) "audio/webm" else "video/webm"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        else -> if (audio) "audio/*" else "video/mp4"
    }

    private fun uniqueFile(directory: File, fileName: String): File {
        val original = File(directory, fileName)
        if (!original.exists()) return original
        for (sequence in 1..9_999) {
            val candidate = File(directory, FilenameFormatter.withCollisionSuffix(fileName, sequence))
            if (!candidate.exists()) return candidate
        }
        error("Could not create a unique destination filename.")
    }

    private fun ContentValues.putDownloadTimestamp(timestampMillis: Long, includeDateTaken: Boolean) {
        val timestampSeconds = timestampMillis / 1_000L
        put(MediaStore.MediaColumns.DATE_ADDED, timestampSeconds)
        put(MediaStore.MediaColumns.DATE_MODIFIED, timestampSeconds)
        if (includeDateTaken) put("datetaken", timestampMillis)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun warmThumbnail(uri: Uri, enabled: Boolean) {
        if (!enabled) return
        runCatching {
            appContext.contentResolver.loadThumbnail(uri, Size(THUMBNAIL_SIZE, THUMBNAIL_SIZE), null)
                .recycle()
        }
    }

    private fun scanLegacyFile(file: File, mimeType: String) {
        MediaScannerConnection.scanFile(
            appContext,
            arrayOf(file.absolutePath),
            arrayOf(mimeType),
            null
        )
    }

    private fun queryDisplayName(uri: Uri, fallback: String): String {
        return runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                    ?.takeIf(String::isNotBlank)
            }
        }.getOrNull() ?: fallback
    }

    private companion object {
        const val THUMBNAIL_SIZE = 512
    }
}
