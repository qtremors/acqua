package dev.qtremors.acqua.downloader

import android.content.Context
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
            YtDlpTemporaryFiles.cleanupAbandoned(context.applicationContext)
            YoutubeDL.getInstance().init(context.applicationContext)
            FFmpeg.getInstance().init(context.applicationContext)
            initialized = true
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

    fun update(context: Context): YoutubeDL.UpdateStatus? = lifecycleLock.write {
        initialize(context)
        YoutubeDL.getInstance().updateYoutubeDL(
            context.applicationContext,
            YoutubeDL.UpdateChannel.STABLE
        )
    }

    fun version(context: Context): String? = runCatching {
        initialize(context)
        YoutubeDL.getInstance().version(context.applicationContext)
    }.getOrNull()
}
