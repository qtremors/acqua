package dev.qtremors.acqua.data.history

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri
import java.io.File
import java.io.FileNotFoundException

enum class HistoryFileDeletionStatus { DELETED, MISSING, DENIED, FAILED, SKIPPED, CANCELLED }

sealed interface HistoryFileDeletionAttempt {
    data class Finished(val status: HistoryFileDeletionStatus) : HistoryFileDeletionAttempt
    data class Consent(val sender: IntentSender, val deletesOnApproval: Boolean) : HistoryFileDeletionAttempt
}

interface HistoryFileDeletionGateway {
    fun delete(entry: HistoryEntry, allowConsent: Boolean = true): HistoryFileDeletionAttempt
    fun verifyApprovedDeletion(entry: HistoryEntry): HistoryFileDeletionStatus
}

/** Only invoked after an explicit file-deletion confirmation. History records are retained. */
class HistoryFileDeleter(context: Context,
    private val requestMediaConsent: (Uri) -> IntentSender = { uri ->
        check(Build.VERSION.SDK_INT >= 30)
        MediaStore.createDeleteRequest(context.contentResolver, listOf(uri)).intentSender
    }
) : HistoryFileDeletionGateway {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    override fun delete(entry: HistoryEntry, allowConsent: Boolean): HistoryFileDeletionAttempt {
        if (!entry.isDownloaded || entry.fileUri.isBlank()) return finished(HistoryFileDeletionStatus.SKIPPED)
        val uri = entry.fileUri.toUri()
        return try {
            when (uri.scheme) {
                "file" -> finished(deleteLegacyFile(uri))
                "content" -> deleteContent(uri)
                else -> finished(HistoryFileDeletionStatus.DENIED)
            }
        } catch (error: SecurityException) {
            if (!allowConsent) finished(HistoryFileDeletionStatus.DENIED)
            else consent(uri, error)
        } catch (_: FileNotFoundException) {
            finished(HistoryFileDeletionStatus.MISSING)
        } catch (_: RuntimeException) {
            finished(HistoryFileDeletionStatus.FAILED)
        } catch (_: java.io.IOException) {
            finished(HistoryFileDeletionStatus.FAILED)
        }
    }

    private fun deleteContent(uri: Uri): HistoryFileDeletionAttempt {
        // Never pass a collection or document tree to a provider's delete operation.
        if (uri.authority == MediaStore.AUTHORITY && !isMediaItem(uri)) return finished(HistoryFileDeletionStatus.DENIED)
        if (DocumentsContract.isTreeUri(uri) && !DocumentsContract.isDocumentUri(appContext, uri)) return finished(HistoryFileDeletionStatus.DENIED)
        if (uri.authority == "${appContext.packageName}.fileprovider" && (uri.pathSegments.firstOrNull() != "downloads" || uri.pathSegments.size < 2)) {
            return finished(HistoryFileDeletionStatus.DENIED)
        }
        if (resolver.getType(uri) == DocumentsContract.Document.MIME_TYPE_DIR) return finished(HistoryFileDeletionStatus.DENIED)
        if (uri.authority != MediaStore.AUTHORITY && uri.authority != "${appContext.packageName}.fileprovider" &&
            !DocumentsContract.isDocumentUri(appContext, uri)) {
            val count = resolver.query(uri, null, null, null, null)?.use { it.count }
            if (count != 1) return finished(when (count) {
                0 -> HistoryFileDeletionStatus.MISSING
                null -> HistoryFileDeletionStatus.FAILED
                else -> HistoryFileDeletionStatus.DENIED
            })
        }
        val deleted = if (DocumentsContract.isDocumentUri(appContext, uri)) {
            DocumentsContract.deleteDocument(resolver, uri)
        } else resolver.delete(uri, null, null) > 0
        if (!deleted && uri.authority == "${appContext.packageName}.fileprovider") {
            resolver.openFileDescriptor(uri, "r")?.close()
        }
        return finished(if (deleted) HistoryFileDeletionStatus.DELETED else absentStatus(uri))
    }

    private fun deleteLegacyFile(uri: Uri): HistoryFileDeletionStatus {
        val file = File(uri.path.orEmpty()).canonicalFile
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).canonicalFile
        if (!file.path.startsWith(root.path + File.separator) || file.isDirectory) return HistoryFileDeletionStatus.DENIED
        if (!file.exists()) return HistoryFileDeletionStatus.MISSING
        return if (file.delete()) HistoryFileDeletionStatus.DELETED else HistoryFileDeletionStatus.FAILED
    }

    private fun consent(uri: Uri, error: SecurityException): HistoryFileDeletionAttempt = try {
        when {
            Build.VERSION.SDK_INT >= 30 && isMediaItem(uri) -> HistoryFileDeletionAttempt.Consent(requestMediaConsent(uri), true)
            Build.VERSION.SDK_INT == 29 && error is RecoverableSecurityException ->
                HistoryFileDeletionAttempt.Consent(error.userAction.actionIntent.intentSender, false)
            else -> finished(HistoryFileDeletionStatus.DENIED)
        }
    } catch (_: RuntimeException) { finished(HistoryFileDeletionStatus.DENIED) }

    override fun verifyApprovedDeletion(entry: HistoryEntry): HistoryFileDeletionStatus = try {
        if (absentStatus(entry.fileUri.toUri()) == HistoryFileDeletionStatus.MISSING) HistoryFileDeletionStatus.DELETED
        else HistoryFileDeletionStatus.FAILED
    } catch (_: SecurityException) { HistoryFileDeletionStatus.DENIED }
      catch (_: FileNotFoundException) { HistoryFileDeletionStatus.DELETED }
      catch (_: RuntimeException) { HistoryFileDeletionStatus.FAILED }

    private fun absentStatus(uri: Uri): HistoryFileDeletionStatus = resolver.query(uri, null, null, null, null)?.use {
        if (it.moveToFirst()) HistoryFileDeletionStatus.FAILED else HistoryFileDeletionStatus.MISSING
    } ?: HistoryFileDeletionStatus.FAILED

    private fun isMediaItem(uri: Uri): Boolean = uri.authority == MediaStore.AUTHORITY &&
        uri.pathSegments.lastOrNull()?.toLongOrNull()?.let { it >= 0 } == true &&
        (uri.pathSegments.size == 3 && uri.pathSegments[1] in setOf("downloads", "file") ||
            uri.pathSegments.size == 4 && uri.pathSegments[1] in setOf("audio", "video", "images") && uri.pathSegments[2] == "media")

    private fun finished(status: HistoryFileDeletionStatus) = HistoryFileDeletionAttempt.Finished(status)
}
