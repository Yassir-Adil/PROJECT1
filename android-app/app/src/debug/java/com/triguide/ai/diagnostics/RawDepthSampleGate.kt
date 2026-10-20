package com.triguide.ai.diagnostics

/**
 * Diagnostic admission only for same-Frame [RawDepthPairProof] pairs.
 *
 * Compares frame and depth stamps only to prior stamps from their own source.
 * Processing age uses local elapsedRealtime only. Never subtracts unrelated clocks,
 * never demands depth/confidence equality, never grants navigation authority.
 * Does not claim absolute sensor age or calibrated accuracy.
 */
internal class RawDepthSampleGate(
    private val maxProcessingAgeNs: Long = 250_000_000L,
) {
    init {
        require(maxProcessingAgeNs >= 0)
    }

    private var stopped = false
    private var lastCameraNs = 0L
    private var lastDepthNs = 0L
    private var lastCompletedElapsedNs = 0L

    @Synchronized
    fun accept(
        pair: RawDepthPairProof,
        depthTimestampNs: Long,
        confidenceTimestampNs: Long,
        receivedElapsedNs: Long,
        completedElapsedNs: Long,
        tracking: Boolean,
    ): String {
        if (stopped) return "stopped"
        if (!pair.isActive) return "inactive_proof"
        if (!tracking) return "not_tracking"
        if (receivedElapsedNs < 0 || completedElapsedNs < receivedElapsedNs ||
            completedElapsedNs < lastCompletedElapsedNs
        ) {
            return "invalid_elapsed_clock"
        }
        if (completedElapsedNs - receivedElapsedNs > maxProcessingAgeNs) {
            return "processing_stale"
        }
        if (depthTimestampNs <= 0L || confidenceTimestampNs <= 0L) {
            return "invalid_sensor_timestamp"
        }
        val frameTimestampNs = pair.frameTimestampNs
        if (frameTimestampNs <= lastCameraNs) return "camera_not_new"
        if (depthTimestampNs <= lastDepthNs) return "depth_not_new"

        lastCameraNs = frameTimestampNs
        lastDepthNs = depthTimestampNs
        lastCompletedElapsedNs = completedElapsedNs
        return "accepted"
    }

    @Synchronized
    fun stop() {
        stopped = true
    }
}
