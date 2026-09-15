package dev.qtremors.acqua.platform.permissions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DownloadPermissionPrompt {
    NONE,
    STORAGE_RATIONALE,
    STORAGE_SETTINGS,
    NOTIFICATION_EXPLANATION,
    NOTIFICATION_DENIED
}

class DownloadPermissionGate(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val mutablePrompt = MutableStateFlow(DownloadPermissionPrompt.NONE)
    val prompt = mutablePrompt.asStateFlow()

    private var pendingAction: (() -> Unit)? = null
    private var pendingNeedsNotification = false

    val hasRequestedStorage: Boolean
        get() = savedStateHandle[KEY_STORAGE_REQUESTED] ?: false

    val hasRequestedNotifications: Boolean
        get() = savedStateHandle[KEY_NOTIFICATIONS_REQUESTED] ?: false

    fun hold(needsNotification: Boolean, action: () -> Unit) {
        pendingNeedsNotification = needsNotification
        pendingAction = action
    }

    fun markStorageRequested() {
        savedStateHandle[KEY_STORAGE_REQUESTED] = true
    }

    fun markNotificationsRequested() {
        savedStateHandle[KEY_NOTIFICATIONS_REQUESTED] = true
    }

    fun show(prompt: DownloadPermissionPrompt) {
        mutablePrompt.value = prompt
    }

    fun dismiss() {
        mutablePrompt.value = DownloadPermissionPrompt.NONE
        pendingAction = null
        pendingNeedsNotification = false
    }

    fun resumePending(): PendingPermissionAction? {
        val action = pendingAction ?: return null
        pendingAction = null
        mutablePrompt.value = DownloadPermissionPrompt.NONE
        return PendingPermissionAction(pendingNeedsNotification, action).also {
            pendingNeedsNotification = false
        }
    }

    fun runPendingWithoutNotifications() {
        val action = pendingAction
        dismiss()
        action?.invoke()
    }

    private companion object {
        const val KEY_STORAGE_REQUESTED = "permissions.storage.requested"
        const val KEY_NOTIFICATIONS_REQUESTED = "permissions.notifications.requested"
    }
}

data class PendingPermissionAction(
    val needsNotification: Boolean,
    val action: () -> Unit
)
