package dev.qtremors.acqua.feature.settings

import dev.qtremors.acqua.core.data.BuildConfig
import dev.qtremors.acqua.data.updater.AppUpdateGateway
import dev.qtremors.acqua.data.updater.AppUpdateInfo
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsAppUpdatesTest {
    @Test fun `checks are explicit avoid duplicate requests and recover from failure`() = runTest {
        val updates = SettingsAppUpdates(this)
        var calls = 0
        val pending = CompletableDeferred<Result<AppUpdateInfo>>()
        val check: suspend () -> Result<AppUpdateInfo> = { calls++; pending.await() }
        assertEquals(0, calls)
        updates.check(check)
        updates.check(check)
        runCurrent()
        assertEquals(1, calls)
        assertTrue(updates.state.value.isChecking)
        pending.complete(Result.failure(IOException("offline")))
        runCurrent()
        assertEquals(SettingsAppUpdateFailure.CHECK, updates.state.value.failure)
        assertFalse(updates.state.value.isChecking)
        updates.check { Result.success(testAppUpdateInfo().copy(isUpdateAvailable = false)) }
        runCurrent()
        assertNull(updates.state.value.failure)
        assertFalse(updates.state.value.info!!.isUpdateAvailable)
    }

    @Test fun `download uses release validation survives dismiss and installs without another download`() = runTest {
        val updates = SettingsAppUpdates(this)
        val gateway = FakeSettingsAppUpdateGateway()
        gateway.downloadGate = CompletableDeferred()
        updates.check { Result.success(testAppUpdateInfo()) }
        runCurrent()
        updates.download(gateway)
        updates.download(gateway)
        runCurrent()
        gateway.progress!!(40L, 100L, 40)
        assertEquals(40, updates.state.value.progress)
        assertEquals(1, gateway.downloads)
        assertEquals("https://example.com/Acqua.apk", gateway.url)
        assertEquals("Acqua.apk", gateway.fileName)
        assertEquals(100L, gateway.size)
        assertEquals(41, gateway.version)
        assertEquals(BuildConfig.APPLICATION_ID, gateway.packageName)
        assertTrue(gateway.requireNewer)
        updates.dismiss()
        assertTrue(updates.state.value.isDownloading)
        updates.check { error("Reopening progress must not check again") }
        assertTrue(updates.state.value.isDialogOpen)
        gateway.downloadGate!!.complete(gateway.downloadResult)
        runCurrent()
        assertFalse(updates.state.value.isDownloading)
        assertEquals(gateway.downloadResult.getOrThrow(), updates.state.value.downloadedApk)
        updates.install(gateway)
        assertEquals(1, gateway.permissionRequests)
        assertEquals(0, gateway.installs)
        gateway.allowsInstall = true
        updates.install(gateway)
        assertEquals(1, gateway.installs)
        assertEquals(BuildConfig.APPLICATION_ID, gateway.packageName)
        assertEquals(1, gateway.downloads)
    }

    @Test fun `download and install failures preserve retry state and missing assets cannot download`() = runTest {
        val updates = SettingsAppUpdates(this)
        val gateway = FakeSettingsAppUpdateGateway()
        updates.check { Result.success(testAppUpdateInfo().copy(apkDownloadUrl = null)) }
        runCurrent()
        updates.download(gateway)
        assertEquals(0, gateway.downloads)
        updates.check { Result.success(testAppUpdateInfo()) }
        runCurrent()
        gateway.downloadResult = Result.failure(IOException("offline"))
        updates.download(gateway)
        runCurrent()
        assertEquals(SettingsAppUpdateFailure.DOWNLOAD, updates.state.value.failure)
        gateway.downloadResult = Result.success(File("Acqua.apk"))
        updates.download(gateway)
        runCurrent()
        assertNull(updates.state.value.failure)
        gateway.allowsInstall = true
        gateway.installResult = Result.failure(IllegalStateException("installer unavailable"))
        updates.install(gateway)
        assertEquals(SettingsAppUpdateFailure.INSTALL, updates.state.value.failure)
        gateway.installResult = Result.success(Unit)
        updates.install(gateway)
        assertNull(updates.state.value.failure)
        assertEquals(2, gateway.downloads)
    }
}

internal fun testAppUpdateInfo() = AppUpdateInfo(
    currentVersionName = "0.4.0", currentVersionCode = 40,
    latestVersionName = "0.4.1", latestVersionCode = 41, isUpdateAvailable = true,
    releaseTitle = "Acqua 0.4.1", releaseNotes = "Fixes", releaseUrl = "https://example.com/release",
    apkName = "Acqua.apk", apkDownloadUrl = "https://example.com/Acqua.apk", apkSizeBytes = 100L, publishedAt = null
)

internal class FakeSettingsAppUpdateGateway : AppUpdateGateway {
    var downloads = 0
    var installs = 0
    var permissionRequests = 0
    var allowsInstall = false
    var url: String? = null
    var fileName: String? = null
    var size = 0L
    var version: Int? = null
    var packageName: String? = null
    var requireNewer = false
    var downloadResult: Result<File> = Result.success(File("Acqua.apk"))
    var downloadGate: CompletableDeferred<Result<File>>? = null
    var installResult: Result<Unit> = Result.success(Unit)
    var progress: ((Long, Long, Int) -> Unit)? = null

    override suspend fun downloadUpdate(
        downloadUrl: String, fileName: String, expectedSizeBytes: Long,
        expectedVersionCode: Int?, expectedPackageName: String?, requireNewerThanInstalled: Boolean,
        authorizationToken: String?, onProgress: (Long, Long, Int) -> Unit
    ): Result<File> {
        downloads++
        url = downloadUrl
        this.fileName = fileName
        size = expectedSizeBytes
        version = expectedVersionCode
        packageName = expectedPackageName
        requireNewer = requireNewerThanInstalled
        progress = onProgress
        return downloadGate?.await() ?: downloadResult
    }
    override fun canRequestPackageInstalls() = allowsInstall
    override fun openInstallPermissionSettings() { permissionRequests++ }
    override fun installApk(apkFile: File, expectedPackageName: String?, requireNewerThanInstalled: Boolean): Result<Unit> {
        installs++
        packageName = expectedPackageName
        return installResult
    }
}
