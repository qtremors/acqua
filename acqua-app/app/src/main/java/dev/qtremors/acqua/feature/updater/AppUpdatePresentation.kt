package dev.qtremors.acqua.feature.updater

import android.content.res.Resources
import androidx.annotation.StringRes
import dev.qtremors.acqua.R
import java.io.File
import java.text.DateFormat
import java.util.Date

enum class AppUpdateFailure(@StringRes val messageResource: Int) {
    INVALID_REPOSITORY(R.string.updater_invalid_repository),
    NO_APK(R.string.updater_no_apk),
    NO_RELEASE(R.string.updater_no_release),
    NOT_FOUND(R.string.updater_not_found),
    GITHUB_REQUEST(R.string.updater_github_request_failed),
    DOWNLOAD(R.string.updater_download_failed),
    INSTALL(R.string.updater_install_failed),
    UNKNOWN(R.string.updater_unknown_failure)
}

sealed interface AppUpdateNotice {
    data object RepositoryTracked : AppUpdateNotice
    data class RepositoryUntracked(val fullName: String) : AppUpdateNotice
    data class RefreshComplete(val repositoryCount: Int) : AppUpdateNotice
    data class Connected(val username: String) : AppUpdateNotice
    data object TokenRemoved : AppUpdateNotice
    data class RateLimited(val resetEpochSeconds: Long?) : AppUpdateNotice
    data class Failure(val reason: AppUpdateFailure) : AppUpdateNotice
}

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progressPercent: Int
    ) : UpdateDownloadState
    data class Downloaded(val apkFile: File) : UpdateDownloadState
    data class Error(val reason: AppUpdateFailure) : UpdateDownloadState
}

internal fun AppUpdateNotice.localized(resources: Resources): String = when (this) {
    AppUpdateNotice.RepositoryTracked -> resources.getString(R.string.updater_repository_tracked)
    is AppUpdateNotice.RepositoryUntracked -> resources.getString(
        R.string.updater_repository_untracked,
        fullName
    )
    is AppUpdateNotice.RefreshComplete -> resources.getQuantityString(
        R.plurals.updater_refresh_complete,
        repositoryCount,
        repositoryCount
    )
    is AppUpdateNotice.Connected -> resources.getString(R.string.connected_as, username)
    AppUpdateNotice.TokenRemoved -> resources.getString(R.string.updater_token_removed)
    is AppUpdateNotice.RateLimited -> resetEpochSeconds?.let { reset ->
        resources.getString(
            R.string.updater_rate_limited_until,
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(reset * 1_000L))
        )
    } ?: resources.getString(R.string.updater_rate_limited)
    is AppUpdateNotice.Failure -> resources.getString(reason.messageResource)
}
