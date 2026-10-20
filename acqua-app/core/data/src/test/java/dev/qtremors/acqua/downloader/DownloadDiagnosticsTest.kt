package dev.qtremors.acqua.downloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadDiagnosticsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `redaction removes credentials private URLs accounts and paths including spaces`() {
        val source = listOf("Authorization: Bearer shortBearer", "Proxy-Authorization: Basic shortBasic",
            "Cookie: first=cookieOne; second=cookieTwo", "Set-Cookie: first=setOne; second=setTwo; HttpOnly",
            "\"password\":\"private phrase\"", "api_key=privateApi", "token=privateToken",
            "https://private.example/path?token=privateToken", "person@example.com @accountName",
            "Destination: C:\\Users\\Alice Smith\\Private Video.mp4", "File /home/alice/Private Video.mp4").joinToString("\n")
        val safe = SafeDiagnostics.text(source, 8192)
        for (privateValue in listOf("shortBearer", "shortBasic", "cookieOne", "cookieTwo", "setOne", "setTwo",
            "private phrase", "privateApi", "privateToken", "private.example", "person@example.com", "accountName", "Alice Smith", "Private Video")) {
            assertFalse("Leaked fixture: $privateValue", safe.contains(privateValue))
        }
        assertTrue(safe.contains("[redacted]"))
        assertTrue(safe.contains("[path]"))
    }

    @Test fun `records survive recreation are bounded by count and discard oldest equal timestamp entries`() {
        val store = DownloadDiagnosticsStore(context) { 1000L }
        store.clear()
        val ids = (0 until 25).map { UUID.randomUUID().toString() }
        ids.forEach { store.record(it, IllegalStateException("fixture"), DownloadFailure.UNKNOWN, DownloadPhase.TRANSFER, "worker:DIRECT") }
        val retained = DownloadDiagnosticsStore(context) { 1000L }.load()
        assertEquals(20, retained.size)
        assertEquals(ids.takeLast(20).reversed(), retained.map { it.id })
        assertTrue(retained.all { it.details == null && it.backend == "worker:DIRECT" })
    }

    @Test fun `expired records are removed from storage and clear removes every retained report`() {
        var time = 1000L
        val store = DownloadDiagnosticsStore(context) { time }
        store.clear()
        store.record(UUID.randomUUID().toString(), IllegalStateException("fixture"), DownloadFailure.UNKNOWN, DownloadPhase.TRANSFER, "job:DIRECT")
        time += DownloadDiagnosticsStore.RETENTION_MS + 1
        assertTrue(store.load().isEmpty())
        val preferences = context.getSharedPreferences("acqua_diagnostics_v1", Context.MODE_PRIVATE)
        assertEquals("[]", preferences.getString("records", null))
        store.record(UUID.randomUUID().toString(), IllegalStateException("fixture"), DownloadFailure.UNKNOWN, DownloadPhase.COMPLETE, "app")
        store.clear()
        assertNull(preferences.getString("records", null))
    }

    @Test fun `extended transcripts are opt in bounded and redacted before storage and copying`() {
        val store = DownloadDiagnosticsStore(context) { 1000L }
        store.clear()
        val enabled = DiagnosticTranscript(true)
        val disabled = DiagnosticTranscript(false)
        repeat(100) { index ->
            val line = "line-$index https://private.example/asset token=privateToken"
            enabled.append(line)
            disabled.append(line)
        }
        val error = IllegalStateException("Authorization: Bearer privateBearer")
        val id = UUID.randomUUID().toString()
        store.record(id, error, DownloadFailure.UNKNOWN, DownloadPhase.PROCESSING, "worker:YT_DLP", enabled)
        val restored = requireNotNull(DownloadDiagnosticsStore(context) { 1000L }.get(id))
        assertNotNull(restored.details)
        assertTrue(restored.details!!.length <= 8192)
        assertTrue(restored.details.contains("line-99"))
        assertFalse(restored.details.contains("line-0 "))
        assertFalse(restored.copyText().contains("privateToken"))
        assertFalse(restored.copyText().contains("privateBearer"))
        assertFalse(restored.copyText().contains("private.example"))
        assertTrue(restored.copyText().contains(id))
        assertNull(disabled.finish(error))
    }

    @Test fun `crash capture always hands the original throwable to the existing handler`() {
        val store = DownloadDiagnosticsStore(context) { 1000L }
        store.clear()
        val original = IllegalStateException("token=privateToken")
        var forwarded: Throwable? = null
        val next = Thread.UncaughtExceptionHandler { _, error -> forwarded = error }
        DiagnosticCrashHandler(store, { false }, next).uncaughtException(Thread.currentThread(), original)
        assertSame(original, forwarded)
        assertNotNull(store.get("app-crash"))
        assertFalse(store.get("app-crash")!!.summary.contains("privateToken"))
        forwarded = null
        DiagnosticCrashHandler(store, { error("fixture persistence failure") }, next).uncaughtException(Thread.currentThread(), original)
        assertSame(original, forwarded)
    }

    @Test fun `corrupt and oversized stored diagnostics cannot break the reader`() {
        val preferences = context.getSharedPreferences("acqua_diagnostics_v1", Context.MODE_PRIVATE)
        for (payload in listOf("not JSON", "x".repeat(300_001))) {
            preferences.edit().putString("records", payload).commit()
            assertTrue(DownloadDiagnosticsStore(context).load().isEmpty())
            assertNull(preferences.getString("records", null))
        }
    }
}
