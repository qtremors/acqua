package dev.qtremors.acqua.feature.settings

import android.content.Context
import dev.qtremors.acqua.core.data.BuildConfig
import dev.qtremors.acqua.data.updater.AppUpdateGateway
import dev.qtremors.acqua.data.updater.AppUpdateInfo
import dev.qtremors.acqua.data.updater.AppUpdater
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class SettingsAppUpdateFailure { CHECK, DOWNLOAD, INSTALL }

internal data class SettingsAppUpdateState(
    val isChecking: Boolean = false,
    val isDialogOpen: Boolean = false,
    val info: AppUpdateInfo? = null,
    val isDownloading: Boolean = false,
    val progress: Int? = null,
    val downloadedApk: File? = null,
    val failure: SettingsAppUpdateFailure? = null
)

internal class SettingsAppUpdates(private val scope: CoroutineScope) {
    private var defaultUpdater: AppUpdater? = null

    fun updater(context: Context): AppUpdater = defaultUpdater
        ?: AppUpdater(context.applicationContext).also { defaultUpdater = it }

    private val mutableState = MutableStateFlow(SettingsAppUpdateState())
    val state = mutableState.asStateFlow()

    fun check(checkForUpdate: suspend () -> Result<AppUpdateInfo>) {
        if (state.value.isChecking) return
        if (state.value.isDownloading || state.value.downloadedApk != null) {
            mutableState.update { it.copy(isDialogOpen = true) }
            return
        }
        mutableState.value = SettingsAppUpdateState(isChecking = true)
        scope.launch {
            try {
                val info = checkForUpdate().getOrThrow()
                mutableState.value = SettingsAppUpdateState(info = info, isDialogOpen = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                mutableState.value = SettingsAppUpdateState(failure = SettingsAppUpdateFailure.CHECK, isDialogOpen = true)
            } catch (_: RuntimeException) {
                mutableState.value = SettingsAppUpdateState(failure = SettingsAppUpdateFailure.CHECK, isDialogOpen = true)
            }
        }
    }

    fun dismiss() { mutableState.update { it.copy(isDialogOpen = false) } }

    fun download(updater: AppUpdateGateway) {
        val current = state.value
        val info = current.info ?: return
        val url = info.apkDownloadUrl?.takeIf(String::isNotBlank) ?: return
        val name = info.apkName?.takeIf(String::isNotBlank) ?: return
        if (!info.isUpdateAvailable || current.isDownloading || current.downloadedApk != null) return
        mutableState.update { it.copy(isDownloading = true, progress = null, failure = null) }
        scope.launch {
            try {
                val file = updater.downloadUpdate(
                    downloadUrl = url,
                    fileName = name,
                    expectedSizeBytes = info.apkSizeBytes,
                    expectedVersionCode = info.latestVersionCode.takeIf { it > 0 },
                    expectedPackageName = BuildConfig.APPLICATION_ID,
                    requireNewerThanInstalled = true
                ) { _, total, percent ->
                    mutableState.update { it.copy(progress = percent.takeIf { total > 0 }) }
                }.getOrThrow()
                mutableState.update { it.copy(isDownloading = false, downloadedApk = file, progress = 100) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                mutableState.update { it.copy(isDownloading = false, failure = SettingsAppUpdateFailure.DOWNLOAD) }
            } catch (_: RuntimeException) {
                mutableState.update { it.copy(isDownloading = false, failure = SettingsAppUpdateFailure.DOWNLOAD) }
            }
        }
    }

    fun install(updater: AppUpdateGateway) {
        val file = state.value.downloadedApk ?: return
        mutableState.update { it.copy(failure = null) }
        try {
            if (updater.canRequestPackageInstalls()) {
                updater.installApk(file, expectedPackageName = BuildConfig.APPLICATION_ID).getOrThrow()
            } else {
                updater.openInstallPermissionSettings()
            }
        } catch (_: IOException) {
            mutableState.update { it.copy(failure = SettingsAppUpdateFailure.INSTALL) }
        } catch (_: RuntimeException) {
            mutableState.update { it.copy(failure = SettingsAppUpdateFailure.INSTALL) }
        }
    }
}
