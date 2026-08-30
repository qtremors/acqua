package dev.qtremors.acqua.downloader

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

object YtDlpRuntime {
    private val lifecycleLock = ReentrantReadWriteLock()
    @Volatile private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            try {
                YtDlpTemporaryFiles.cleanupAbandoned(context.applicationContext)
                YoutubeDL.getInstance().init(context.applicationContext)
                FFmpeg.getInstance().init(context.applicationContext)
                initialized = true
            } catch (error: Throwable) {
                Log.e(TAG, "Unable to initialize the yt-dlp runtime", error)
                throw error
            }
        }
    }

    fun execute(
        context: Context,
        request: YoutubeDLRequest,
        processId: String,
        callback: (Float, Long, String) -> Unit = { _, _, _ -> }
    ): YoutubeDLResponse = lifecycleLock.read {
        initialize(context)
        YoutubeDL.getInstance().execute(request, processId, callback)
    }

    fun cancel(processId: String): Boolean = YoutubeDL.getInstance().destroyProcessById(processId)

    fun update(context: Context): YtDlpUpdateStatus = lifecycleLock.write {
        initialize(context)
        YoutubeDL.getInstance()
            .updateYoutubeDL(context.applicationContext, YoutubeDL.UpdateChannel.STABLE)
            .toAcquaUpdateStatus()
    }

    fun version(context: Context): String? = lifecycleLock.read {
        initialize(context)
        YoutubeDL.getInstance().version(context.applicationContext)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: YoutubeDL.getInstance().execute(
                YoutubeDLRequest(emptyList()).apply { addOption("--version") }
            ).out.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty)
    }

    private const val TAG = "YtDlpRuntime"
}

enum class YtDlpUpdateStatus {
    UPDATED,
    ALREADY_CURRENT
}

internal fun YoutubeDL.UpdateStatus?.toAcquaUpdateStatus(): YtDlpUpdateStatus = when (this) {
    YoutubeDL.UpdateStatus.DONE -> YtDlpUpdateStatus.UPDATED
    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE, null -> YtDlpUpdateStatus.ALREADY_CURRENT
}
