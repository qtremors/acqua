package dev.qtremors.acqua.feature.onboarding

import dev.qtremors.acqua.data.backup.PreferencesBackupItem
import dev.qtremors.acqua.data.backup.PreferencesBackupPreview

data class OnboardingRestoreItem(
    val id: String,
    val label: String
)

sealed interface OnboardingRestoreState {
    data object Idle : OnboardingRestoreState
    data object Busy : OnboardingRestoreState

    data class Preview(
        val preview: PreferencesBackupPreview
    ) : OnboardingRestoreState

    data class Success(
        val items: List<PreferencesBackupItem>,
        val requiresRestart: Boolean = false
    ) : OnboardingRestoreState

    data class Error(
        val message: String
    ) : OnboardingRestoreState
}
