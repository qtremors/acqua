package dev.qtremors.acqua.platform

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.os.Build
import android.provider.MediaStore
import dev.qtremors.acqua.data.history.HistoryEntry
import dev.qtremors.acqua.domain.WebLink
import android.app.DownloadManager
import android.widget.Toast
import androidx.core.net.toUri
import dev.qtremors.acqua.core.data.R

class FileActions(context: Context) {
    private val appContext = context.applicationContext

    fun open(uri: String, mimeType: String) = runCatching {
        appContext.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri.toUri(), mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }.onFailure {
        Toast.makeText(appContext, R.string.no_app_to_open_file, Toast.LENGTH_SHORT).show()
    }

    fun share(uri: String, mimeType: String) = runCatching {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri.toUri())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        appContext.startActivity(
            Intent.createChooser(sendIntent, appContext.getString(R.string.share_media))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        Toast.makeText(appContext, R.string.could_not_share_file, Toast.LENGTH_SHORT).show()
    }

    // Call off the main thread: content providers can take time to respond.
    fun storageLocation(uriString: String): String = runCatching {
        val uri = uriString.toUri()
        if (uri.scheme == "file") return@runCatching uri.path ?: uriString
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == "content") {
            appContext.contentResolver.query(
                uri, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val path = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    val name = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (path >= 0 && name >= 0) {
                        val folder = cursor.getString(path)
                        if (!folder.isNullOrBlank()) return@runCatching folder.trimEnd('/') + "/" + cursor.getString(name).orEmpty()
                    }
                }
            }
        }
        uriString
    }.getOrDefault(uriString)

    fun openSource(url: String) = runCatching {
        val normalized = WebLink.normalize(url) ?: error("Invalid source link")
        appContext.startActivity(Intent(Intent.ACTION_VIEW, normalized.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(appContext, R.string.cannot_open_link, Toast.LENGTH_SHORT).show()
    }

    fun shareMany(entries: List<HistoryEntry>) = runCatching {
        require(entries.isNotEmpty() && entries.all { it.isDownloaded && it.fileUri.isNotBlank() })
        val uris = ArrayList(entries.map { it.fileUri.toUri() }.distinct())
        val types = entries.map { it.mimeType.ifBlank { "application/octet-stream" } }.distinct()
        val families = types.map { it.substringBefore('/') }.distinct()
        val sharedType = if (types.size == 1) types.single() else if (families.size == 1) "${families.single()}/*" else "*/*"
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = sharedType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            clipData = ClipData.newUri(appContext.contentResolver, "Media", uris.first()).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        appContext.startActivity(Intent.createChooser(send, appContext.getString(R.string.share_media)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(appContext, R.string.could_not_share_file, Toast.LENGTH_SHORT).show()
    }

    fun openDownloads() = runCatching {
        appContext.startActivity(
            Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        Toast.makeText(appContext, R.string.no_app_to_open_file, Toast.LENGTH_SHORT).show()
    }
}
