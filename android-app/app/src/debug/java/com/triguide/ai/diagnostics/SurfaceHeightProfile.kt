package com.triguide.ai.diagnostics

/**
 * Primitive immutable plane height observation for diagnostic profiling
 * (no SDK objects retained).
 *
 * Reuses [SurfaceKind]. Upward is not floor; signed camera-above is not a
 * calibrated camera height or navigation authority.
 */
internal data class SurfaceHeightCandidate(
    val kind: SurfaceKind,
    val centerWorldY: Double,
    val tracking: Boolean,
    val subsumed: Boolean,
)

/**
 * Bounded signed cameraY-planeY range snapshot of eligible upward planes.
 *
 * Diagnostic only — min/max are relative observed diffs, not floor selection,
 * walkability, clearance, or movement permission.
 */
internal data class SurfaceHeightSnapshot(
    val status: String,
    val inspected: Int,
    val eligibleUpward: Int,
    val ignored: Int,
    val invalidHeight: Int,
    val minCameraAboveMeters: Double?,
    val maxCameraAboveMeters: Double?,
    val truncated: Boolean,
)

/**
 * Summarizes a bounded iterable of primitive plane height candidates.
 * Pure / stateless — no speech, navigation, or retained SDK state.
 */
internal object SurfaceHeightProfile {
    const val MAX_PLANES: Int = 256

    private const val STATUS_NOT_TRACKING = "not_tracking"
    private const val STATUS_INVALID_CAMERA = "invalid_camera"
    private const val STATUS_OBSERVED = "observed"

    fun summarize(
        candidates: Iterable<SurfaceHeightCandidate>,
        cameraTracking: Boolean,
        cameraWorldY: Double,
    ): SurfaceHeightSnapshot {
        if (!cameraTracking) {
            return emptySnapshot(STATUS_NOT_TRACKING)
        }
        if (!cameraWorldY.isFinite()) {
            return emptySnapshot(STATUS_INVALID_CAMERA)
        }

        val iterator = candidates.iterator()
        var inspected = 0
        var eligibleUpward = 0
        var ignored = 0
        var invalidHeight = 0
        var minCameraAbove: Double? = null
        var maxCameraAbove: Double? = null

        while (inspected < MAX_PLANES && iterator.hasNext()) {
            val candidate = iterator.next()
            inspected++

            val eligible =
                candidate.kind == SurfaceKind.HORIZONTAL_UPWARD &&
                    candidate.tracking &&
                    !candidate.subsumed

            if (!eligible) {
                ignored++
                continue
            }

            val centerY = candidate.centerWorldY
            if (!centerY.isFinite()) {
                invalidHeight++
                continue
            }

            val diff = cameraWorldY - centerY
            if (!diff.isFinite()) {
                invalidHeight++
                continue
            }

            eligibleUpward++
            minCameraAbove =
                if (minCameraAbove == null) diff else minOf(minCameraAbove, diff)
            maxCameraAbove =
                if (maxCameraAbove == null) diff else maxOf(maxCameraAbove, diff)
        }

        val truncated = inspected == MAX_PLANES && iterator.hasNext()
        return SurfaceHeightSnapshot(
            status = STATUS_OBSERVED,
            inspected = inspected,
            eligibleUpward = eligibleUpward,
            ignored = ignored,
            invalidHeight = invalidHeight,
            minCameraAboveMeters = minCameraAbove,
            maxCameraAboveMeters = maxCameraAbove,
            truncated = truncated,
        )
    }

    private fun emptySnapshot(status: String): SurfaceHeightSnapshot =
        SurfaceHeightSnapshot(
            status = status,
            inspected = 0,
            eligibleUpward = 0,
            ignored = 0,
            invalidHeight = 0,
            minCameraAboveMeters = null,
            maxCameraAboveMeters = null,
            truncated = false,
        )
}
