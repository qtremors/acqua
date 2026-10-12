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
import androidx.exifinterface.media.ExifInterface
import dev.qtremors.acqua.data.history.HistoryEntry
import dev.qtremors.acqua.data.history.HistoryWriter
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.downloader.FilenameFormatter
import dev.qtremors.acqua.downloader.DownloadContentType
import dev.qtremors.acqua.data.network.HttpMediaClient
import dev.qtremors.acqua.domain.ResolvedMedia
import dev.qtremors.acqua.domain.MediaKind
import java.io.File
import java.io.OutputStream
import java.util.UUID

class MediaStorage(
    context: Context,
    private val historyRepository: HistoryWriter,
    private val settingsRepository: AppSettingsRepository,
    private val mediaDownloader: HttpMediaClient,
    private val copyProcessedBytes: (File, OutputStream) -> Unit = { source, output ->
        source.inputStream().use { input -> input.copyTo(output) }
    }
) {
    private val appContext = context.applicationContext

    suspend fun save(
        item: ResolvedMedia,
        index: Int,
        sourceUrl: String,
        itemCount: Int = 1,
        settings: dev.qtremors.acqua.data.settings.DownloadSettings = settingsRepository.downloadSettings(),
        embedMetadata: Boolean = settingsRepository.mediaProcessingSettings().embedMetadata,
        preserveOriginalMetadata: Boolean = settingsRepository.mediaProcessingSettings().preserveOriginalMetadata,
        finalDownloadName: () -> String? = { null },
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Uri {
        val destination = settings.destinations[item.kind]
        val baseFolder = settingsRepository.sanitizedBaseFolder(settings.baseFolder)
        val organization = destination.folders(item, sourceUrl).joinToString("/")
        val category = when (item.kind) {
            MediaKind.VIDEO -> "Videos"
            MediaKind.AUDIO -> "Audio"
            MediaKind.IMAGE -> "Images"
        }
        val relativePath = buildString {
            append("Download/")
            append(baseFolder)
            if (settings.categorizeMedia) append("/$category")
            if (organization.isNotEmpty()) append("/$organization")
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
            pattern = if (item.downloadName != null) "{title}" else if (isAudio) settings.audioFilenamePattern else settings.filenamePattern,
            username = item.username,
            width = if (isAudio) 0 else item.width,
            height = if (isAudio) 0 else item.height,
            index = index,
            itemCount = itemCount,
            fileExtension = extension,
            title = item.downloadName ?: item.title,
            artist = item.artist,
            album = item.album,
            context = dev.qtremors.acqua.downloader.FilenameContext.fromMedia(item)
        )
        val requestCookies = item.requestCookies.takeIf {
            settingsRepository.useBrowserSessions() && item.explicitBrowserSessionAuthorized
        }

        if (destination.treeUri != null) {
            return saveInFolder(item, sourceUrl, destination, fileName, mimeType, requestCookies,
                embedMetadata, preserveOriginalMetadata, finalDownloadName, onProgress)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(item, sourceUrl, relativePath, fileName, mimeType, requestCookies,
                embedMetadata, preserveOriginalMetadata, finalDownloadName, onProgress)
        } else {
            saveLegacy(
                item,
                sourceUrl,
                baseFolder + (if (settings.categorizeMedia) "/$category" else "") + (if (organization.isNotEmpty()) "/$organization" else ""),
                category,
                fileName,
                mimeType,
                requestCookies,
                false,
                embedMetadata,
                preserveOriginalMetadata,
                finalDownloadName,
                onProgress
            )
        }
    }

    suspend fun saveProcessedFile(
        sourceFile: File,
        media: ResolvedMedia,
        contentType: DownloadContentType,
        sourceUrl: String,
        preserveOriginalMetadata: Boolean = settingsRepository.mediaProcessingSettings().preserveOriginalMetadata,
        index: Int = 0,
        itemCount: Int = 1,
        settings: dev.qtremors.acqua.data.settings.DownloadSettings = settingsRepository.downloadSettings(),
        caption: Boolean = false,
        artifactSuffix: String = "",
        publishedMediaName: String? = null,
        thumbnail: Boolean = false,
        chapterTitle: String? = null
    ): Uri {
        require(sourceFile.isFile && sourceFile.length() > 0L) { "The processed media file is empty." }
        val baseFolder = settingsRepository.sanitizedBaseFolder(settings.baseFolder)
        val isAudio = contentType == DownloadContentType.AUDIO || media.isAudio
        val destination = settings.destinations[if (isAudio) MediaKind.AUDIO else MediaKind.VIDEO]
        val organization = destination.folders(media, sourceUrl).joinToString("/")
        val category = if (isAudio) "Audio" else "Videos"
        val extension = sourceFile.extension.lowercase().ifBlank { if (isAudio) "m4a" else "mp4" }
        val mimeType = if (caption) when (extension) {
            "srt" -> "application/x-subrip"
            "vtt" -> "text/vtt"
            "ass" -> "text/x-ssa"
            else -> "text/plain"
        } else mimeTypeFor(extension, isAudio)
        val timestamp = if (preserveOriginalMetadata) {
            media.sourceTimestampMillis ?: sourceFile.lastModified()
        } else System.currentTimeMillis()
        val pattern = if (media.downloadName != null) "{title}" else if (isAudio) settings.audioFilenamePattern else settings.filenamePattern
        val chapterPattern = if (chapterTitle != null && media.downloadName == null &&
            !pattern.contains("{chapter}") && !pattern.contains("{chapter|")) "${pattern}_{chapter}" else pattern
        val baseName = FilenameFormatter.format(
            pattern = chapterPattern,
            username = media.username,
            width = if (isAudio) 0 else media.width,
            height = if (isAudio) 0 else media.height,
            index = index,
            itemCount = itemCount,
            fileExtension = extension,
            title = media.downloadName ?: media.title,
            artist = media.artist,
            album = media.album,
            context = dev.qtremors.acqua.downloader.FilenameContext.fromMedia(media, chapterTitle)
        )
        val fileName = FilenameFormatter.withArtifactSuffix(publishedMediaName ?: baseName, artifactSuffix, extension)
        val relativePath = buildString {
            append("Download/")
            append(baseFolder)
            if (settings.categorizeMedia) append("/$category")
            if (organization.isNotEmpty()) append("/$organization")
        }

        if (destination.treeUri != null) {
            return DownloadFolderPublisher(appContext).publish(sourceFile, media, sourceUrl, destination, fileName, mimeType,
                copyProcessedBytes) { uri, finalName ->
                recordProcessedDownload(sourceUrl, uri, finalName, mimeType, sourceFile.length(), !isAudio && !caption && !thumbnail, media)
            }
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
                    copyProcessedBytes(sourceFile, output)
                } ?: error("Could not open the destination media file.")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                values.putDownloadTimestamp(timestamp, includeDateTaken = !isAudio && !caption)
                resolver.update(uri, values, null, null)
                warmThumbnail(uri, enabled = thumbnail || !isAudio && !caption)
                val finalName = queryDisplayName(uri, fileName)
                recordProcessedDownload(sourceUrl, uri, finalName, mimeType, sourceFile.length(), !isAudio && !caption && !thumbnail, media)
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
                relativePath.removePrefix("Download/")
            )
            check(targetDirectory.mkdirs() || targetDirectory.isDirectory) {
                "Could not create the configured Downloads subfolder."
            }
            val target = uniqueFile(targetDirectory, fileName)
            try {
                target.outputStream().use { output -> copyProcessedBytes(sourceFile, output) }
                target.setLastModified(timestamp)
                scanLegacyFile(target, mimeType)
                val uri = FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    target
                )
                recordProcessedDownload(sourceUrl, uri, target.name, mimeType, target.length(), !isAudio && !caption && !thumbnail, media)
                uri
            } catch (error: Exception) {
                target.delete()
                throw error
            }
        }
    }

    private suspend fun saveInFolder(
        item: ResolvedMedia, sourceUrl: String, destination: dev.qtremors.acqua.data.settings.DownloadDestination,
        fileName: String, mimeType: String, requestCookies: String?,
        embedMetadata: Boolean, preserveOriginalMetadata: Boolean,
        finalDownloadName: () -> String?, onProgress: (Long, Long) -> Unit
    ): Uri {
        DownloadFolderAccess(appContext).root(Uri.parse(destination.treeUri))
        val temporary = File.createTempFile("folder-download-", ".part", appContext.cacheDir)
        try {
            val bytes = temporary.outputStream().use { mediaDownloader.downloadToStreamCancellable(item, it, requestCookies, onProgress) }
            if (canWriteImageMetadata(mimeType, embedMetadata)) writeImageMetadata(ExifInterface(temporary), item, sourceUrl, preserveOriginalMetadata)
            val finalName = finalDownloadName()?.let { renamedFileName(fileName, it) } ?: fileName
            return DownloadFolderPublisher(appContext).publish(temporary, item, sourceUrl, destination, finalName, mimeType,
                copyProcessedBytes) { uri, name -> recordDownload(item, sourceUrl, uri, name, mimeType, bytes) }
        } finally { temporary.delete() }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun saveWithMediaStore(
        item: ResolvedMedia,
        sourceUrl: String,
        relativePath: String,
        fileName: String,
        mimeType: String,
        requestCookies: String?,
        embedMetadata: Boolean,
        preserveOriginalMetadata: Boolean,
        finalDownloadName: () -> String?,
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

            if (canWriteImageMetadata(mimeType, embedMetadata)) {
                resolver.openFileDescriptor(uri, "rw")?.use {
                    writeImageMetadata(ExifInterface(it.fileDescriptor), item, sourceUrl, preserveOriginalMetadata)
                }
            }

            values.clear()
            finalDownloadName()?.let { values.put(MediaStore.Downloads.DISPLAY_NAME, renamedFileName(fileName, it)) }
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            values.putDownloadTimestamp(downloadTimestamp(item, preserveOriginalMetadata), includeDateTaken = !item.isAudio)
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
        embedMetadata: Boolean,
        preserveOriginalMetadata: Boolean,
        finalDownloadName: () -> String?,
        onProgress: (Long, Long) -> Unit
    ): Uri {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDirectory = File(downloads, if (categorizeMedia) "$baseFolder/$category" else baseFolder)
        check(targetDirectory.mkdirs() || targetDirectory.isDirectory) {
            "Could not create the configured Downloads subfolder."
        }
        var file = uniqueFile(targetDirectory, fileName)

        return try {
            val bytesDownloaded = file.outputStream().use { output ->
                mediaDownloader.downloadToStreamCancellable(item, output, requestCookies, onProgress)
            }
            if (canWriteImageMetadata(mimeType, embedMetadata)) writeImageMetadata(ExifInterface(file), item, sourceUrl, preserveOriginalMetadata)
            finalDownloadName()?.let { name ->
                val requestedName = renamedFileName(fileName, name)
                if (requestedName != file.name) {
                    val renamed = uniqueFile(targetDirectory, requestedName)
                    runCatching {
                        if (!file.renameTo(renamed)) {
                            if (Build.VERSION.SDK_INT >= 26) java.nio.file.Files.move(file.toPath(), renamed.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                            else { file.copyTo(renamed, overwrite = true); check(file.delete()) }
                        }
                    }.getOrElse { error -> renamed.delete(); throw error }
                    file = renamed
                }
            }
            file.setLastModified(downloadTimestamp(item, preserveOriginalMetadata))
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
                thumbnailUrl = null,
                title = item.title, author = item.artist ?: item.username, sourceIdentity = item.sourceIdentity
            )
        )
    }

    private fun recordProcessedDownload(
        sourceUrl: String,
        uri: Uri,
        fileName: String,
        mimeType: String,
        bytesDownloaded: Long,
        isVideo: Boolean,
        media: ResolvedMedia
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
                thumbnailUrl = null,
                title = media.title, author = media.artist ?: media.username, sourceIdentity = media.sourceIdentity
            )
        )
    }

    private fun mimeTypeFor(extension: String, audio: Boolean): String = when (extension) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
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
        if (original.createNewFile()) return original
        for (sequence in 1..9_999) {
            val candidate = File(directory, FilenameFormatter.withCollisionSuffix(fileName, sequence))
            if (candidate.createNewFile()) return candidate
        }
        error("Could not create a unique destination filename.")
    }

    private fun canWriteImageMetadata(mimeType: String, embedMetadata: Boolean): Boolean =
        embedMetadata && mimeType in setOf("image/jpeg", "image/png", "image/webp")

    private fun renamedFileName(original: String, name: String): String = FilenameFormatter.format(
        pattern = "{title}", username = null, width = 0, height = 0, index = 0,
        fileExtension = original.substringAfterLast('.'), title = name
    )

    private fun writeImageMetadata(exif: ExifInterface, media: ResolvedMedia, sourceUrl: String, preserve: Boolean) {
        ImageDownloadMetadata.apply(
            exif, sourceUrl, downloadTimestamp(media, preserve), preserve
        )
    }

    private fun downloadTimestamp(media: ResolvedMedia, preserve: Boolean): Long =
        if (preserve) {
            media.sourceTimestampMillis ?: System.currentTimeMillis()
        } else System.currentTimeMillis()

    private fun ContentValues.putDownloadTimestamp(timestampMillis: Long, includeDateTaken: Boolean) {
        val timestampSeconds = timestampMillis / 1_000L
        put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1_000L)
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

    fun queryDisplayName(uri: Uri, fallback: String): String {
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
