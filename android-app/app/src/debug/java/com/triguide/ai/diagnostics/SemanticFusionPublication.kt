package com.triguide.ai.diagnostics

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
    @Synchronized fun canPublish(revision: Long, receivedNs: Long, nowNs: Long): Boolean =
        revision == generation && SemanticFusionMapping.fresh(receivedNs, nowNs, !stopped, tracking)
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
