package dev.qtremors.acqua.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class YtDlpFailureTest {
    @Test
    fun `obfuscated runtime initialization failure is identified`() {
        val failure = RuntimeException(
            "class we is not a concrete class",
            ExceptionInInitializerError()
        )

        assertEquals(YtDlpFailure.RUNTIME, failure.toYtDlpFailure())
    }

    @Test
    fun `wrapped network failures are identified and retryable`() {
        val unknownHost = IllegalStateException("update failed", UnknownHostException("offline"))
        val timeout = RuntimeException(SocketTimeoutException("slow"))

        assertEquals(YtDlpFailure.NETWORK, unknownHost.toYtDlpFailure())
        assertEquals(YtDlpFailure.NETWORK, timeout.toYtDlpFailure())
        assertTrue(unknownHost.hasNetworkCause())
        assertTrue(timeout.hasNetworkCause())
    }

    @Test
    fun `update response failures are identified`() {
        val failure = IllegalStateException("unable to get download url")

        assertEquals(YtDlpFailure.UPDATE_SERVICE, failure.toYtDlpFailure())
    }

    @Test
    fun `storage failures are identified`() {
        val failure = IOException("No space left on device")

        assertEquals(YtDlpFailure.STORAGE, failure.toYtDlpFailure())
        assertFalse(failure.hasNetworkCause())
    }

    @Test
    fun `unknown failures stay unknown and are not retryable`() {
        val failure = IllegalArgumentException("unexpected value")

        assertEquals(YtDlpFailure.UNKNOWN, failure.toYtDlpFailure())
        assertFalse(failure.hasNetworkCause())
    }
}
