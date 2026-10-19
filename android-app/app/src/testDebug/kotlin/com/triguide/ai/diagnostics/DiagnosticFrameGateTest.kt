package com.triguide.ai.diagnostics

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class DiagnosticFrameGateTest {
    private fun gate(interval: Long = 10, age: Long = 50) =
        DiagnosticFrameGate(interval, age).apply { start() }

    @Test fun stoppedGateCannotAdmit() {
        assertEquals("stopped", DiagnosticFrameGate().offer(1, 0).reason)
    }
    @Test fun invalidTimestampsDoNotPoisonValidAdmission() {
        val gate = gate()
        assertEquals("invalid_timestamp", gate.offer(0, 0).reason)
        assertEquals("invalid_timestamp", gate.offer(1, -1).reason)
        assertNotNull(gate.offer(1, 0).ticket)
    }
    @Test fun atMostOneInFlightAndNoQueuedTicket() {
        val gate = gate()
        val ticket = gate.offer(1, 100).ticket!!
        assertEquals("busy", gate.offer(2, 120).reason)
        assertEquals("accepted", gate.complete(ticket, 125))
        assertEquals("out_of_order", gate.offer(2, 130).reason)
        assertNotNull(gate.offer(3, 130).ticket)
    }
    @Test fun duplicateAndReversedSensorSamplesAreRejected() {
        val gate = gate()
        val ticket = gate.offer(100, 100).ticket!!
        gate.complete(ticket, 101)
        assertEquals("out_of_order", gate.offer(100, 110).reason)
        assertEquals("out_of_order", gate.offer(99, 110).reason)
    }
    @Test fun cadenceBoundaryIsInclusive() {
        val gate = gate()
        gate.complete(gate.offer(1, 100).ticket!!, 101)
        assertEquals("cadence", gate.offer(2, 109).reason)
        assertNotNull(gate.offer(3, 110).ticket)
    }
    @Test fun rejectedReceiptRollbackDoesNotPoisonFollowingFrame() {
        val gate = gate()
        gate.complete(gate.offer(1, 100).ticket!!, 101)
        assertEquals("clock_rollback", gate.offer(2, 99).reason)
        assertNotNull(gate.offer(2, 110).ticket)
    }
    @Test fun maximumAgeBoundaryIsInclusiveAndStaleCompletionReleasesSlot() {
        val gate = gate()
        assertEquals("accepted", gate.complete(gate.offer(1, 100).ticket!!, 150))
        assertEquals("stale", gate.complete(gate.offer(2, 160).ticket!!, 211))
        assertNotNull(gate.offer(3, 220).ticket)
    }
    @Test fun completionClockRollbackIsRejectedAndReleasesSlot() {
        val gate = gate()
        assertEquals("clock_rollback", gate.complete(gate.offer(1, 100).ticket!!, 99))
        assertNotNull(gate.offer(2, 110).ticket)
    }
    @Test fun stopRejectsLateCompletionAndAllNewOffers() {
        val gate = gate()
        val ticket = gate.offer(1, 100).ticket!!
        gate.stop()
        assertEquals("stopped_or_old_session", gate.complete(ticket, 110))
        assertEquals("stopped", gate.offer(2, 120).reason)
    }
    @Test fun oldCompletionCannotReleaseNewSessionTicket() {
        val gate = gate()
        val old = gate.offer(1, 100).ticket!!
        gate.stop()
        gate.start()
        val fresh = gate.offer(1, 200).ticket!!
        assertEquals("stopped_or_old_session", gate.complete(old, 210))
        assertEquals("busy", gate.offer(2, 220).reason)
        assertEquals("accepted", gate.complete(fresh, 230))
    }
    @Test fun repeatedOrForgedCompletionCannotReleaseActiveTicket() {
        val gate = gate()
        val ticket = gate.offer(1, 100).ticket!!
        assertEquals("not_active", gate.complete(DiagnosticFrameGate.Ticket(ticket.generation, 100), 110))
        assertEquals("accepted", gate.complete(ticket, 110))
        assertEquals("not_active", gate.complete(ticket, 110))
    }
    @Test fun differentCameraClockEpochDoesNotBecomeFakeCaptureLatency() {
        val gate = gate()
        assertEquals("accepted", gate.complete(gate.offer(Long.MAX_VALUE, 10).ticket!!, 20))
    }
    @Test fun concurrentOffersAdmitOnlyOneTicket() {
        val gate = gate(interval = 0)
        val admitted = AtomicInteger()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val jobs = (1L..64L).map { value -> pool.submit {
                start.await()
                if (gate.offer(value, 100).ticket != null) admitted.incrementAndGet()
            } }
            start.countDown()
            jobs.forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, admitted.get())
        } finally { pool.shutdownNow() }
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsNegativeInterval() { gate(-1) }
    @Test(expected = IllegalArgumentException::class) fun rejectsZeroAge() { gate(age = 0) }
    @Test(expected = IllegalStateException::class) fun refusesImplicitRestart() { gate().start() }
}
