package dev.qtremors.acqua.platform.permissions

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPermissionGateTest {
    @Test
    fun `pending action survives prompt changes and runs once`() {
        val gate = DownloadPermissionGate(SavedStateHandle())
        var executions = 0

        gate.hold(needsNotification = true) { executions += 1 }
        gate.show(DownloadPermissionPrompt.NOTIFICATION_EXPLANATION)
        gate.runPendingWithoutNotifications()
        gate.runPendingWithoutNotifications()

        assertEquals(1, executions)
        assertEquals(DownloadPermissionPrompt.NONE, gate.prompt.value)
    }

    @Test
    fun `permission request history is restored`() {
        val handle = SavedStateHandle()
        val gate = DownloadPermissionGate(handle)
        assertFalse(gate.hasRequestedStorage)
        assertFalse(gate.hasRequestedNotifications)

        gate.markStorageRequested()
        gate.markNotificationsRequested()
        val restored = DownloadPermissionGate(handle)

        assertTrue(restored.hasRequestedStorage)
        assertTrue(restored.hasRequestedNotifications)
    }

    @Test
    fun `resume returns notification requirement and consumes action`() {
        val gate = DownloadPermissionGate(SavedStateHandle())
        var executed = false
        gate.hold(needsNotification = true) { executed = true }

        val pending = gate.resumePending()

        assertTrue(pending?.needsNotification == true)
        pending?.action?.invoke()
        assertTrue(executed)
        assertEquals(null, gate.resumePending())
    }
}
