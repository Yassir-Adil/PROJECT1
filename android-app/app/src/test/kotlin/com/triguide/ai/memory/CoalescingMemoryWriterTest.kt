package com.triguide.ai.memory

import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * RED suite for the expected [CoalescingMemoryWriter] helper (storage-performance).
 *
 * Expected API (implementation owned by a later slice; this file is tests-only):
 * ```
 * internal class CoalescingMemoryWriter(
 *     dispatcher: kotlinx.coroutines.CoroutineDispatcher =
 *         kotlinx.coroutines.Dispatchers.IO,
 * ) : java.io.Closeable {
 *     fun offer(write: () -> Unit): Boolean
 *     override fun close()
 * }
 * ```
 *
 * Contract under test: single serial worker + [kotlinx.coroutines.channels.Channel.CONFLATED]
 * so [offer] never blocks the caller on slow/fsync work; pending work coalesces to the
 * latest write; [close] rejects new offers and discards pending without claiming to
 * interrupt an already-running synchronous write body.
 *
 * Scope: best-effort durable label/summary notes only. Must never drive navigation
 * decisions or speech events (wiring owned elsewhere; this helper has no such APIs).
 *
 * Timing: [CountDownLatch] / [CompletableFuture] waits bounded at 3s. No Thread.sleep,
 * no coroutines-test library.
 */
class CoalescingMemoryWriterTest {

    @Test
    fun offerReturnsWhileFirstWriteIsStillBlocked() {
        withWriter { writer ->
            val firstStarted = CountDownLatch(1)
            val releaseFirst = CountDownLatch(1)
            val firstFinished = CountDownLatch(1)

            assertTrue(
                writer.offer {
                    firstStarted.countDown()
                    assertTrue(
                        "first write release timed out",
                        releaseFirst.await(TIMEOUT_SEC, TimeUnit.SECONDS),
                    )
                    firstFinished.countDown()
                },
            )
            assertTrue(
                "first write must start",
                firstStarted.await(TIMEOUT_SEC, TimeUnit.SECONDS),
            )

            val offerWhileBlocked = CompletableFuture.supplyAsync {
                writer.offer { /* coalesced successor; body may or may not run */ }
            }
            assertEquals(
                "offer must complete without waiting for the blocked write",
                true,
                offerWhileBlocked.get(TIMEOUT_SEC, TimeUnit.SECONDS),
            )

            releaseFirst.countDown()
            assertTrue(firstFinished.await(TIMEOUT_SEC, TimeUnit.SECONDS))
        }
    }

    @Test
    fun oneHundredOffersWhileBlockedKeepOnlyTheLatestPendingWrite() {
        withWriter { writer ->
            val firstStarted = CountDownLatch(1)
            val releaseFirst = CountDownLatch(1)
            val executed = CopyOnWriteArrayList<Int>()
            val latestFinished = CountDownLatch(1)

            assertTrue(
                writer.offer {
                    firstStarted.countDown()
                    assertTrue(
                        "first write release timed out",
                        releaseFirst.await(TIMEOUT_SEC, TimeUnit.SECONDS),
                    )
                    executed += 0
                },
            )
            assertTrue(firstStarted.await(TIMEOUT_SEC, TimeUnit.SECONDS))

            for (i in 1..100) {
                val value = i
                assertTrue(
                    "offer $value must be accepted while open",
                    writer.offer {
                        executed += value
                        if (value == 100) latestFinished.countDown()
                    },
                )
            }

            releaseFirst.countDown()
            assertTrue(
                "latest coalesced write must run",
                latestFinished.await(TIMEOUT_SEC, TimeUnit.SECONDS),
            )

            assertEquals(
                "blocked first write plus only the latest of 100 pending offers",
                listOf(0, 100),
                executed.toList(),
            )
        }
    }

    @Test
    fun closeRejectsNewWorkAndDiscardsPendingWithoutInterruptingInFlightWrite() {
        withWriter { writer ->
            val inFlightStarted = CountDownLatch(1)
            val releaseInFlight = CountDownLatch(1)
            val inFlightFinished = CountDownLatch(1)
            val pendingRan = AtomicBoolean(false)

            assertTrue(
                writer.offer {
                    inFlightStarted.countDown()
                    assertTrue(
                        "in-flight release timed out",
                        releaseInFlight.await(TIMEOUT_SEC, TimeUnit.SECONDS),
                    )
                    inFlightFinished.countDown()
                },
            )
            assertTrue(inFlightStarted.await(TIMEOUT_SEC, TimeUnit.SECONDS))
            assertTrue(writer.offer { pendingRan.set(true) })

            // UI disposal must not join a synchronous write that is still blocked.
            val closeDone = CompletableFuture.runAsync { writer.close() }
            closeDone.get(TIMEOUT_SEC, TimeUnit.SECONDS)

            val rejected = CompletableFuture.supplyAsync {
                val deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SEC)
                while (System.nanoTime() < deadlineNs) {
                    if (!writer.offer { fail("must not run after close accepts rejection") }) {
                        return@supplyAsync true
                    }
                }
                false
            }
            assertTrue(
                "close must reject new offers within ${TIMEOUT_SEC}s",
                rejected.get(TIMEOUT_SEC, TimeUnit.SECONDS),
            )

            assertEquals(
                "close must not interrupt a synchronous in-flight write body",
                1L,
                inFlightFinished.count,
            )

            releaseInFlight.countDown()
            assertTrue(inFlightFinished.await(TIMEOUT_SEC, TimeUnit.SECONDS))
            closeDone.get(TIMEOUT_SEC, TimeUnit.SECONDS)

            assertFalse(
                "pending write queued before close must be discarded",
                pendingRan.get(),
            )
            assertFalse(
                "closed writer must keep rejecting offers",
                writer.offer { fail("must stay closed") },
            )
        }
    }

    @Test
    fun exceptionInOneWriteDoesNotStopLaterWrites() {
        withWriter { writer ->
            val firstEntered = CountDownLatch(1)
            val secondRan = CountDownLatch(1)

            assertTrue(
                writer.offer {
                    firstEntered.countDown()
                    throw RuntimeException("injected write failure")
                },
            )
            assertTrue(firstEntered.await(TIMEOUT_SEC, TimeUnit.SECONDS))

            assertTrue(writer.offer { secondRan.countDown() })
            assertTrue(
                "worker must accept a later write after a prior exception",
                secondRan.await(TIMEOUT_SEC, TimeUnit.SECONDS),
            )
        }
    }

    @Test
    fun writesExecuteSeriallyNeverConcurrently() {
        withWriter { writer ->
            val inWrite = AtomicInteger(0)
            val peak = AtomicInteger(0)
            val firstStarted = CountDownLatch(1)
            val releaseFirst = CountDownLatch(1)
            val secondStarted = CountDownLatch(1)
            val secondFinished = CountDownLatch(1)

            fun enter() {
                val now = inWrite.incrementAndGet()
                peak.updateAndGet { current -> maxOf(current, now) }
            }

            fun exit() {
                inWrite.decrementAndGet()
            }

            assertTrue(
                writer.offer {
                    enter()
                    firstStarted.countDown()
                    assertTrue(releaseFirst.await(TIMEOUT_SEC, TimeUnit.SECONDS))
                    exit()
                },
            )
            assertTrue(firstStarted.await(TIMEOUT_SEC, TimeUnit.SECONDS))

            assertTrue(
                writer.offer {
                    enter()
                    secondStarted.countDown()
                    exit()
                    secondFinished.countDown()
                },
            )

            assertEquals(
                "second write must not enter while the first is still running",
                1L,
                secondStarted.count,
            )
            assertEquals(1, inWrite.get())
            assertEquals(1, peak.get())

            releaseFirst.countDown()
            assertTrue(secondStarted.await(TIMEOUT_SEC, TimeUnit.SECONDS))
            assertTrue(secondFinished.await(TIMEOUT_SEC, TimeUnit.SECONDS))
            assertEquals("peak concurrent writes must stay 1", 1, peak.get())
            assertEquals(0, inWrite.get())
        }
    }

    /**
     * Owns a single-thread executor + dispatcher for the writer under test and always
     * shuts them down (even when assertions fail).
     */
    private fun withWriter(block: (CoalescingMemoryWriter) -> Unit) {
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val writer = CoalescingMemoryWriter(dispatcher)
        try {
            block(writer)
        } finally {
            try {
                writer.close()
            } catch (_: Throwable) {
                // Test cleanup must still shut down the executor.
            }
            dispatcher.close()
            executor.shutdownNow()
            assertTrue(
                "test executor must terminate within ${TIMEOUT_SEC}s",
                executor.awaitTermination(TIMEOUT_SEC, TimeUnit.SECONDS),
            )
        }
    }

    companion object {
        private const val TIMEOUT_SEC = 3L
    }
}
