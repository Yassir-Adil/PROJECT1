package com.triguide.ai.diagnostics

/**
 * Plane tracking state for diagnostic inventory (not SDK objects).
 *
 * Distinct from hit-level boolean tracking on [SurfaceHitCandidate].
 */
internal enum class SurfacePlaneTracking {
    TRACKING,
    PAUSED,
    STOPPED,
}

/** Primitive immutable plane observation for inventory (no SDK objects retained). */
internal data class SurfacePlaneCandidate(
    val kind: SurfaceKind,
    val tracking: SurfacePlaneTracking,
    val subsumed: Boolean,
)

/**
 * Bounded orientation-count snapshot of available planes.
 *
 * Diagnostic only — upward is not floor, vertical is not a confirmed wall,
 * and none of these counts imply clear / walkable / safe / navigation authority.
 */
internal data class SurfaceInventorySnapshot(
    val status: String,
    val inspected: Int,
    val truncated: Boolean,
    val trackingUpward: Int,
    val trackingVertical: Int,
    val trackingDownward: Int,
    val paused: Int,
    val stopped: Int,
    val subsumed: Int,
    val unknownKind: Int,
)

/**
 * Summarizes a bounded iterable of primitive plane candidates.
 * Diagnostic only — no speech, navigation, or movement authority.
 */
internal object SurfaceInventoryPolicy {
    const val MAX_PLANES: Int = 256

    private const val STATUS_NOT_TRACKING = "not_tracking"
    private const val STATUS_OBSERVED = "observed"
    private const val STATUS_BOUNDED = "bounded"

    fun summarize(
        candidates: Iterable<SurfacePlaneCandidate>,
        cameraTracking: Boolean,
    ): SurfaceInventorySnapshot {
        if (!cameraTracking) {
            return SurfaceInventorySnapshot(
                status = STATUS_NOT_TRACKING,
                inspected = 0,
                truncated = false,
                trackingUpward = 0,
                trackingVertical = 0,
                trackingDownward = 0,
                paused = 0,
                stopped = 0,
                subsumed = 0,
                unknownKind = 0,
            )
        }

        val iterator = candidates.iterator()
        var inspected = 0
        var trackingUpward = 0
        var trackingVertical = 0
        var trackingDownward = 0
        var paused = 0
        var stopped = 0
        var subsumed = 0
        var unknownKind = 0

        while (inspected < MAX_PLANES && iterator.hasNext()) {
            val candidate = iterator.next()
            inspected++
            when {
                candidate.subsumed -> subsumed++
                candidate.tracking == SurfacePlaneTracking.PAUSED -> paused++
                candidate.tracking == SurfacePlaneTracking.STOPPED -> stopped++
                candidate.tracking == SurfacePlaneTracking.TRACKING -> {
                    when (candidate.kind) {
                        SurfaceKind.HORIZONTAL_UPWARD -> trackingUpward++
                        SurfaceKind.VERTICAL_SURFACE -> trackingVertical++
                        SurfaceKind.HORIZONTAL_DOWNWARD -> trackingDownward++
                        SurfaceKind.UNKNOWN -> unknownKind++
                    }
                }
            }
        }

        val truncated = inspected == MAX_PLANES && iterator.hasNext()
        return SurfaceInventorySnapshot(
            status = if (truncated) STATUS_BOUNDED else STATUS_OBSERVED,
            inspected = inspected,
            truncated = truncated,
            trackingUpward = trackingUpward,
            trackingVertical = trackingVertical,
            trackingDownward = trackingDownward,
            paused = paused,
            stopped = stopped,
            subsumed = subsumed,
            unknownKind = unknownKind,
        )
    }
}
