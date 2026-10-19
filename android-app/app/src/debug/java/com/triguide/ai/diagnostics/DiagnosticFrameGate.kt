package com.triguide.ai.diagnostics

/** Debug-only, one in-flight frame; no pixel ownership and no navigation output. */
internal class DiagnosticFrameGate(
    private val minimumIntervalNs: Long = 100_000_000L,
    private val maximumAgeNs: Long = 250_000_000L,
) {
    init {
        require(minimumIntervalNs >= 0L)
        require(maximumAgeNs > 0L)
    }

    internal class Ticket internal constructor(val generation: Long, val receivedNs: Long)
    internal data class Admission(val ticket: Ticket?, val reason: String)

    private var running = false
    private var generation = 0L
    private var active: Ticket? = null
    private var lastSensorNs: Long? = null
    private var lastReceivedNs: Long? = null
    private var lastAdmittedNs: Long? = null

    @Synchronized fun start() {
        check(!running) { "Stop the previous session before starting." }
        check(generation < Long.MAX_VALUE)
        generation++
        active = null
        lastSensorNs = null
        lastReceivedNs = null
        lastAdmittedNs = null
        running = true
    }

    @Synchronized fun stop() {
        running = false
        active = null
    }

    @Synchronized fun offer(sensorTimestampNs: Long, receivedNs: Long): Admission {
        if (!running) return Admission(null, "stopped")
        if (sensorTimestampNs <= 0L || receivedNs < 0L) return Admission(null, "invalid_timestamp")
        if (lastSensorNs?.let { sensorTimestampNs <= it } == true) return Admission(null, "out_of_order")
        if (lastReceivedNs?.let { receivedNs < it } == true) return Admission(null, "clock_rollback")
        lastSensorNs = sensorTimestampNs
        lastReceivedNs = receivedNs
        if (active != null) return Admission(null, "busy")
        if (lastAdmittedNs?.let { receivedNs - it < minimumIntervalNs } == true) {
            return Admission(null, "cadence")
        }
        val ticket = Ticket(generation, receivedNs)
        active = ticket
        lastAdmittedNs = receivedNs
        return Admission(ticket, "admitted")
    }

    /** Only receipt and completion clocks share elapsedRealtimeNanos. Never subtract sensor time. */
    @Synchronized fun complete(ticket: Ticket, completedNs: Long): String {
        if (!running || ticket.generation != generation) return "stopped_or_old_session"
        if (active !== ticket) return "not_active"
        active = null
        if (completedNs < ticket.receivedNs) return "clock_rollback"
        if (completedNs - ticket.receivedNs > maximumAgeNs) return "stale"
        return "accepted"
    }
}
