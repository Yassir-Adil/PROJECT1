package com.triguide.ai.diagnostics

/** Diagnostic admission only: AR timestamps and elapsed-clock durations never mix. */
internal class RawDepthSampleGate(
    private val maxProcessingAgeNs: Long = 250_000_000L,
    private val maxSensorLagNs: Long = 500_000_000L,
) {
    init {
        require(maxProcessingAgeNs >= 0)
        require(maxSensorLagNs >= 0)
    }

    private var stopped = false
    private var lastCameraNs = 0L
    private var lastDepthNs = 0L
    private var lastCompletedElapsedNs = 0L

    @Synchronized
    fun accept(
        cameraTimestampNs: Long,
        depthTimestampNs: Long,
        confidenceTimestampNs: Long,
        receivedElapsedNs: Long,
        completedElapsedNs: Long,
        tracking: Boolean,
    ): String {
        if (stopped) return "stopped"
        if (!tracking) return "not_tracking"
        if (receivedElapsedNs < 0 || completedElapsedNs < receivedElapsedNs ||
            completedElapsedNs < lastCompletedElapsedNs) return "invalid_elapsed_clock"
        if (completedElapsedNs - receivedElapsedNs > maxProcessingAgeNs) return "processing_stale"
        if (cameraTimestampNs <= 0 || depthTimestampNs <= 0 || confidenceTimestampNs <= 0) {
            return "invalid_sensor_timestamp"
        }
        if (depthTimestampNs != confidenceTimestampNs) return "confidence_mismatch"
        if (depthTimestampNs > cameraTimestampNs) return "depth_in_future"
        if (cameraTimestampNs - depthTimestampNs > maxSensorLagNs) return "sensor_stale"
        if (cameraTimestampNs <= lastCameraNs) return "camera_not_new"
        if (depthTimestampNs <= lastDepthNs) return "depth_not_new"
        lastCameraNs = cameraTimestampNs
        lastDepthNs = depthTimestampNs
        lastCompletedElapsedNs = completedElapsedNs
        return "accepted"
    }

    @Synchronized
    fun stop() { stopped = true }
}
