package dev.qtremors.acqua.downloader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeDiagnosticsTest {
    @Test
    fun `diagnostics redact remote and account data and remain bounded`() {
        val token = "abcDEF0123456789abcDEF0123456789"
        val error = IllegalStateException(
            "GET https://example.com/private?token=$token " +
                "Cookie: sessionid=$token Authorization Bearer-$token " +
                "user@example.com @private.account C:\\Users\\name\\secret.txt " +
                "/data/user/0/dev.qtremors.acqua/cache/file " + "x".repeat(1_000)
        )

        val message = SafeDiagnostics.redact(error).message.orEmpty()

        listOf(
            token,
            "example.com",
            "sessionid=$token",
            "user@example.com",
            "@private.account",
            "Users\\name",
            "/data/user"
        ).forEach { sensitive -> assertFalse(message.contains(sensitive)) }
        assertTrue(message.contains("[url]"))
        assertTrue(message.contains("[email]"))
        assertTrue(message.length <= 512)
    }

    @Test
    fun `missing diagnostic message receives a safe stable value`() {
        val message = SafeDiagnostics.redact(RuntimeException()).message.orEmpty()

        assertTrue(message.endsWith("No diagnostic message"))
    }
}
