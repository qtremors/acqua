package dev.qtremors.acqua.downloader

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Hold the archive check and publication together for matching duplicate policies. */
internal object DownloadDuplicateGate {
    private class Entry(val mutex: Mutex = Mutex(), var users: Int = 0)
    private val entries = mutableMapOf<String, Entry>()

    suspend fun <T> run(identity: String, block: suspend () -> T): T {
        val entry = synchronized(entries) { entries.getOrPut(identity) { Entry() }.also { it.users++ } }
        try { return entry.mutex.withLock { block() } } finally {
            synchronized(entries) {
                entry.users--
                if (entry.users == 0) entries.remove(identity)
            }
        }
    }
}
