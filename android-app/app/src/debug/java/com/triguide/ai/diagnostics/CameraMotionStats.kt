package com.triguide.ai.diagnostics

import kotlin.math.hypot

/**
 * Diagnostic SDK pose displacement within uninterrupted tracking segments.
 *
 * Scalar state only: Euclidean distance from each segment origin, aggregated
 * as a max across segments. Not physical calibrated movement, parallax proof,
 * readiness, or walkability. Holds no SDK types and does not log coordinates.
 */
internal data class CameraMotionSnapshot(
    val trackedSamples: Int,
    val trackingSegments: Int,
    val maxSegmentDisplacementMeters: Double?,
    val capacityReached: Boolean,
    val stopped: Boolean,
)

/**
 * Bounded, thread-safe accumulator for camera-pose displacement samples.
 */
internal class CameraMotionStats {
    private val lock = Any()

    private var trackedSamples: Int = 0
    private var trackingSegments: Int = 0
    private var maxSegmentDisplacementMeters: Double? = null
    private var stopped: Boolean = false

    /** Last accepted timestamp (tracking or pause). 0 means none yet (stamps must be > 0). */
    private var lastAcceptedTimestampNs: Long = 0L

    private var segmentActive: Boolean = false
    private var originX: Double = 0.0
    private var originY: Double = 0.0
    private var originZ: Double = 0.0

    fun record(
        timestampNs: Long,
        tracking: Boolean,
        x: Double,
        y: Double,
        z: Double,
    ): String = synchronized(lock) {
        if (stopped) {
            return REASON_STOPPED
        }
        if (trackedSamples >= MAX_TRACKED_SAMPLES) {
            return REASON_CAPACITY
        }
        if (timestampNs <= 0L) {
            return REASON_INVALID_TIMESTAMP
        }
        if (timestampNs <= lastAcceptedTimestampNs) {
            return REASON_OUT_OF_ORDER
        }

        if (!tracking) {
            lastAcceptedTimestampNs = timestampNs
            segmentActive = false
            return REASON_NOT_TRACKING
        }

        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) {
            return REASON_INVALID_POSE
        }

        if (!segmentActive) {
            segmentActive = true
            originX = x
            originY = y
            originZ = z
            trackingSegments += 1
            trackedSamples += 1
            lastAcceptedTimestampNs = timestampNs
            val currentMax = maxSegmentDisplacementMeters
            maxSegmentDisplacementMeters =
                if (currentMax == null) 0.0 else maxOf(currentMax, 0.0)
            return REASON_ACCEPTED
        }

        val dx = x - originX
        val dy = y - originY
        val dz = z - originZ
        if (!dx.isFinite() || !dy.isFinite() || !dz.isFinite()) {
            return REASON_INVALID_POSE
        }
        val displacement = hypot(hypot(dx, dy), dz)
        if (!displacement.isFinite()) {
            return REASON_INVALID_POSE
        }

        trackedSamples += 1
        lastAcceptedTimestampNs = timestampNs
        val currentMax = maxSegmentDisplacementMeters ?: 0.0
        if (displacement > currentMax) {
            maxSegmentDisplacementMeters = displacement
        }
        return REASON_ACCEPTED
    }

    fun snapshot(): CameraMotionSnapshot = synchronized(lock) {
        CameraMotionSnapshot(
            trackedSamples = trackedSamples,
            trackingSegments = trackingSegments,
            maxSegmentDisplacementMeters = maxSegmentDisplacementMeters,
            capacityReached = trackedSamples >= MAX_TRACKED_SAMPLES,
            stopped = stopped,
        )
    }

    fun stop() = synchronized(lock) {
        stopped = true
    }

    companion object {
        const val MAX_TRACKED_SAMPLES: Int = 10_000

        private const val REASON_ACCEPTED: String = "accepted"
        private const val REASON_STOPPED: String = "stopped"
        private const val REASON_CAPACITY: String = "capacity"
        private const val REASON_INVALID_TIMESTAMP: String = "invalid_timestamp"
        private const val REASON_OUT_OF_ORDER: String = "out_of_order"
        private const val REASON_NOT_TRACKING: String = "not_tracking"
        private const val REASON_INVALID_POSE: String = "invalid_pose"
    }
}
