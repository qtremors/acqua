package dev.qtremors.acqua.downloader

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

/** Both scheduling backends share a FIFO queue and a conservative global bound. */
internal object DownloadExecutionGate {
    private val gate = BoundedDownloadGate()
    fun configure(maximum: Int) = gate.configure(maximum)
    suspend fun <T> run(maximum: () -> Int = { 1 }, block: suspend () -> T): T {
        gate.configure(maximum)
        return gate.run(block)
    }
}

internal class BoundedDownloadGate(initialMaximum: Int = 1) {
    private val lock = Any()
    private val waiting = ArrayDeque<CancellableContinuation<Lease>>()
    private var maximum = initialMaximum.takeIf { it in 1..2 } ?: 1
    private var active = 0

    fun configure(value: Int) = configure { value }

    fun configure(value: () -> Int) = synchronized(lock) {
        maximum = value().takeIf { it in 1..2 } ?: 1
        drain()
    }

    suspend fun <T> run(block: suspend () -> T): T {
        val lease = suspendCancellableCoroutine<Lease> { continuation ->
            synchronized(lock) {
                waiting.addLast(continuation)
                continuation.invokeOnCancellation { synchronized(lock) { waiting.remove(continuation); drain() } }
                drain()
            }
        }
        try { currentCoroutineContext().ensureActive(); return block() } finally { lease.release() }
    }

    private fun drain() {
        while (active < maximum && waiting.isNotEmpty()) {
            val next = waiting.removeFirst()
            if (!next.isActive) continue
            active++
            next.resume(Lease()) { _, lease, _ -> lease.release() }
        }
    }

    private inner class Lease {
        private val released = AtomicBoolean()
        fun release() {
            if (!released.compareAndSet(false, true)) return
            synchronized(lock) { active--; drain() }
        }
    }
}
