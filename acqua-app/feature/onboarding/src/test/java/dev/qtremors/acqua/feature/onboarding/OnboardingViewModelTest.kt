package dev.qtremors.acqua.feature.onboarding

import android.net.Uri
import dev.qtremors.acqua.MainDispatcherRule
import dev.qtremors.acqua.data.backup.PreferencesBackupGateway
import dev.qtremors.acqua.data.backup.PreferencesBackupItem
import dev.qtremors.acqua.data.backup.PreferencesBackupItemStatus
import dev.qtremors.acqua.data.backup.PreferencesBackupOperationResult
import dev.qtremors.acqua.data.backup.PreferencesBackupPreview
import dev.qtremors.acqua.data.onboarding.OnboardingState
import dev.qtremors.acqua.data.onboarding.OnboardingStateStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `permission completion is idempotent and persists the active version`() = runTest {
        val store = FakeOnboardingStore()
        val viewModel = OnboardingViewModel(store, FakeBackupGateway(), appVersionCode = 24)
        advanceUntilIdle()

        viewModel.updatePermissionState(
            hasStoragePermission = true,
            hasNotificationPermission = false,
            notificationPermissionRequired = true
        )
        viewModel.next()
        viewModel.next()
        viewModel.next()
        advanceUntilIdle()

        assertEquals(listOf(24 to true), store.completions)
        assertTrue(viewModel.state.value.isCompleted)
        assertFalse(viewModel.state.value.isCompleting)
    }

    @Test
    fun `backup preview and restore expose typed recoverable states`() = runTest {
        val store = FakeOnboardingStore()
        val item = PreferencesBackupItem(
            id = "settings",
            label = "Settings",
            status = PreferencesBackupItemStatus.WillRestore
        )
        val backup = FakeBackupGateway(
            previewResult = Result.success(PreferencesBackupPreview(123L, listOf(item))),
            restoreResult = Result.success(PreferencesBackupOperationResult(listOf(item)))
        )
        val viewModel = OnboardingViewModel(store, backup, appVersionCode = 24)
        val uri = Uri.parse("content://backup/settings.json")
        advanceUntilIdle()

        viewModel.previewBackup(uri)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.restoreState is OnboardingRestoreState.Preview)

        viewModel.applyRestoreBackup()
        advanceUntilIdle()

        val restored = viewModel.state.value.restoreState as OnboardingRestoreState.Success
        assertEquals(listOf(item), restored.items)
        assertEquals(listOf(uri), backup.previewed)
        assertEquals(listOf(uri), backup.restored)
        assertEquals(listOf(24 to true), store.completions)
    }

    @Test
    fun `failed preview clears pending restore and uses a stable failure code`() = runTest {
        val backup = FakeBackupGateway(previewResult = Result.failure(IllegalStateException("secret")))
        val viewModel = OnboardingViewModel(FakeOnboardingStore(), backup)
        advanceUntilIdle()

        viewModel.previewBackup(Uri.parse("content://backup/bad.json"))
        advanceUntilIdle()
        viewModel.applyRestoreBackup()
        advanceUntilIdle()

        assertEquals(
            OnboardingRestoreState.Error(OnboardingRestoreFailure.INSPECT),
            viewModel.state.value.restoreState
        )
        assertTrue(backup.restored.isEmpty())
    }

    private class FakeOnboardingStore : OnboardingStateStore {
        override val onboardingState = MutableStateFlow(OnboardingState())
        val completions = mutableListOf<Pair<Int, Boolean>>()

        override suspend fun completeOnboarding(
            versionCode: Int,
            markNotificationHandled: Boolean
        ) {
            completions += versionCode to markNotificationHandled
            onboardingState.value = OnboardingState(
                isCompleted = true,
                completedVersionCode = versionCode,
                notificationPermissionHandled = markNotificationHandled
            )
        }

        override suspend fun setNotificationHandled(handled: Boolean) {
            onboardingState.value = onboardingState.value.copy(
                notificationPermissionHandled = handled
            )
        }
    }

    private class FakeBackupGateway(
        var previewResult: Result<PreferencesBackupPreview> = Result.failure(IllegalStateException()),
        var restoreResult: Result<PreferencesBackupOperationResult> = Result.failure(IllegalStateException())
    ) : PreferencesBackupGateway {
        val previewed = mutableListOf<Uri>()
        val restored = mutableListOf<Uri>()

        override suspend fun preview(uri: Uri): Result<PreferencesBackupPreview> {
            previewed += uri
            return previewResult
        }

        override suspend fun restoreFrom(uri: Uri): Result<PreferencesBackupOperationResult> {
            restored += uri
            return restoreResult
        }
    }
}
