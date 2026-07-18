package dev.qtremors.acqua.platform

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import dev.qtremors.acqua.R

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
}
