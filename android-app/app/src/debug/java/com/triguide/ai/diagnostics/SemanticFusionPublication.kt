package com.triguide.ai.diagnostics

/** Reasons are diagnostic only; ALLOWED is display eligibility, never movement permission. */
internal enum class SemanticDisplayDecision {
    ALLOWED, STOPPED, NOT_TRACKING, REVISION_CHANGED, INVALID_TIMESTAMP,
    CLOCK_ROLLBACK, EXPIRED, PUBLICATION_REJECTED, INVALID_BOUNDS, EMPTY_PAINT;

    /** Missing layout may retry within ORIGINAL receipt age; terminal/content rejections clear. */
    val clearsPublication: Boolean get() = this != ALLOWED && this != INVALID_BOUNDS
}

/** Revision invalidation is independent of inference completion and stops are terminal. */
internal class SemanticFusionPublication {
    private var generation = 0L
    private var tracking = false
    private var stopped = false
    @Synchronized fun revision(): Long = generation
    @Synchronized fun trackingChanged(value: Boolean): Boolean {
        if (stopped || value == tracking) return false
        tracking = value
        generation++
        return true
    }
    @Synchronized fun invalidateGeometry() { generation++; }
    @Synchronized fun stop() { stopped = true; tracking = false; generation++ }
    @Synchronized fun decision(revision: Long, receivedNs: Long, nowNs: Long): SemanticDisplayDecision = when {
        stopped -> SemanticDisplayDecision.STOPPED
        !tracking -> SemanticDisplayDecision.NOT_TRACKING
        revision != generation -> SemanticDisplayDecision.REVISION_CHANGED
        receivedNs < 0 -> SemanticDisplayDecision.INVALID_TIMESTAMP
        nowNs < receivedNs -> SemanticDisplayDecision.CLOCK_ROLLBACK
        !SemanticFusionMapping.fresh(receivedNs, nowNs, true, true) -> SemanticDisplayDecision.EXPIRED
        else -> SemanticDisplayDecision.ALLOWED
    }

    @Synchronized fun canPublish(revision: Long, receivedNs: Long, nowNs: Long): Boolean =
        decision(revision, receivedNs, nowNs) == SemanticDisplayDecision.ALLOWED
}

/** Surface size alone misses a 180-degree display rotation. */
internal class SemanticViewport {
    private var applied: Triple<Int, Int, Int>? = null
    fun changed(rotation: Int, width: Int, height: Int): Boolean {
        if (rotation !in 0..3 || width <= 0 || height <= 0) return false
        val current = Triple(rotation, width, height)
        if (current == applied) return false
        applied = current
        return true
    }
}
