package dev.qtremors.acqua.feature.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.qtremors.acqua.data.backup.PreferencesBackupManager
import dev.qtremors.acqua.data.onboarding.OnboardingPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep {
    WelcomeAndFeatures,
    SetupPermissions,
    Done
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WelcomeAndFeatures,
    val hasStoragePermission: Boolean = true,
    val hasNotificationPermission: Boolean = false,
    val notificationPermissionRequired: Boolean = false,
    val notificationPermissionHandled: Boolean = false,
    val isCompleting: Boolean = false,
    val preferencesLoaded: Boolean = false,
    val isCompleted: Boolean = false,
    val restoreState: OnboardingRestoreState = OnboardingRestoreState.Idle
) {
    val canContinue: Boolean
        get() = step != OnboardingStep.SetupPermissions || hasStoragePermission
}

class OnboardingViewModel(
    private val onboardingPreferences: OnboardingPreferences,
    private val backupManager: PreferencesBackupManager,
    private val appVersionCode: Int = 15
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private var pendingRestoreUri: Uri? = null

    init {
        viewModelScope.launch {
            onboardingPreferences.onboardingState.collect { preferences ->
                _state.update {
                    it.copy(
                        preferencesLoaded = true,
                        isCompleted = preferences.isCompleted
                    )
                }
            }
        }
    }

    fun updatePermissionState(
        hasStoragePermission: Boolean,
        hasNotificationPermission: Boolean,
        notificationPermissionRequired: Boolean
    ) {
        _state.update {
            it.copy(
                hasStoragePermission = hasStoragePermission,
                hasNotificationPermission = hasNotificationPermission,
                notificationPermissionRequired = notificationPermissionRequired,
                notificationPermissionHandled = it.notificationPermissionHandled ||
                    !notificationPermissionRequired ||
                    hasNotificationPermission
            )
        }
    }

    fun next() {
        when (_state.value.step) {
            OnboardingStep.WelcomeAndFeatures -> setStep(OnboardingStep.SetupPermissions)
            OnboardingStep.SetupPermissions -> {
                completeOnboarding(markNotificationHandled = true)
            }
            OnboardingStep.Done -> completeOnboarding(markNotificationHandled = false)
        }
    }

    fun back() {
        val previous = when (_state.value.step) {
            OnboardingStep.WelcomeAndFeatures -> OnboardingStep.WelcomeAndFeatures
            OnboardingStep.SetupPermissions -> OnboardingStep.WelcomeAndFeatures
            OnboardingStep.Done -> OnboardingStep.SetupPermissions
        }
        setStep(previous)
    }

    fun handleNotificationPermissionResult() {
        _state.update { it.copy(notificationPermissionHandled = true) }
    }

    fun setStep(step: OnboardingStep) {
        _state.update { it.copy(step = step) }
    }

    fun previewBackup(uri: Uri) {
        if (_state.value.restoreState == OnboardingRestoreState.Busy) return
        _state.update { it.copy(restoreState = OnboardingRestoreState.Busy) }
        pendingRestoreUri = uri
        viewModelScope.launch {
            backupManager.preview(uri).fold(
                onSuccess = { preview ->
                    _state.update {
                        it.copy(restoreState = OnboardingRestoreState.Preview(preview))
                    }
                },
                onFailure = { error ->
                    pendingRestoreUri = null
                    _state.update {
                        it.copy(
                            restoreState = OnboardingRestoreState.Error(
                                error.message ?: "Failed to inspect backup file"
                            )
                        )
                    }
                }
            )
        }
    }

    fun applyRestoreBackup() {
        val uri = pendingRestoreUri ?: return
        if (_state.value.restoreState == OnboardingRestoreState.Busy) return
        _state.update { it.copy(restoreState = OnboardingRestoreState.Busy) }
        viewModelScope.launch {
            backupManager.restoreFrom(uri).fold(
                onSuccess = { result ->
                    pendingRestoreUri = null
                    completeOnboarding(markNotificationHandled = true)
                    _state.update {
                        it.copy(
                            restoreState = OnboardingRestoreState.Success(
                                items = result.items,
                                requiresRestart = false
                            )
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            restoreState = OnboardingRestoreState.Error(
                                error.message ?: "Failed to restore settings backup"
                            )
                        )
                    }
                }
            )
        }
    }

    fun dismissRestoreBackup() {
        pendingRestoreUri = null
        _state.update { it.copy(restoreState = OnboardingRestoreState.Idle) }
    }

    private fun completeOnboarding(markNotificationHandled: Boolean) {
        if (_state.value.isCompleting) return
        _state.update { it.copy(isCompleting = true) }
        viewModelScope.launch {
            onboardingPreferences.completeOnboarding(
                versionCode = appVersionCode,
                markNotificationHandled = markNotificationHandled
            )
            _state.update { it.copy(isCompleting = false, isCompleted = true) }
        }
    }
}
