package com.triguide.ai.memory

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Serial, conflated best-effort disk writer for durable **label/summary notes only**.
 *
 * [offer] never blocks the caller on slow I/O (fsync): work is handed to one worker
 * on [dispatcher] via a [Channel.CONFLATED] queue, so only the latest pending write
 * is kept. [close] stops accepting immediately, discards pending work, and cancels
 * the worker without joining or interrupting a synchronous in-flight write body.
 *
 * Does not close a caller-owned [dispatcher]. Must never be wired to navigation
 * decisions or speech events.
 */
internal class CoalescingMemoryWriter(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Closeable {

    private val closed = AtomicBoolean(false)
    private val channel = Channel<() -> Unit>(Channel.CONFLATED)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    init {
        scope.launch {
            for (write in channel) {
                // After close() cancels the scope, a dequeued element must not run.
                if (closed.get() || !isActive) break
                try {
                    write()
                } catch (_: Exception) {
                    // Best-effort notes: one failed write must not stop the worker.
                    // Do not catch Throwable/Error (VM faults).
                }
            }
        }
    }

    /**
     * Enqueues [write] without waiting for it to run.
     *
     * @return `false` if this writer is already closed (or the channel rejected the send);
     * `true` if the write was accepted (possibly replacing a prior pending write).
     */
    fun offer(write: () -> Unit): Boolean {
        if (closed.get()) return false
        return channel.trySend(write).isSuccess
    }

    /**
     * Stops accepting new work, drops any conflated pending write, and cancels the
     * worker. Returns without [kotlinx.coroutines.runBlocking], join, or interrupting
     * a synchronous body already running inside the worker.
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        channel.close()
        while (true) {
            val result = channel.tryReceive()
            if (result.isFailure) break
        }
        scope.cancel()
    }
}
