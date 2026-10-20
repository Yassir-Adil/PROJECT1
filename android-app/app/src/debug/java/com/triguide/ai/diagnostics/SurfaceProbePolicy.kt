package com.triguide.ai.diagnostics

/**
 * Geometry orientation of an observed surface hit for diagnostic probing.
 *
 * Not object semantics: upward is not floor, vertical is not a confirmed wall,
 * and none of these kinds imply clear / walkable / safe.
 */
internal enum class SurfaceKind {
    UNKNOWN,
    VERTICAL_SURFACE,
    HORIZONTAL_UPWARD,
    HORIZONTAL_DOWNWARD,
}

/** Primitive immutable plane-hit observation (no SDK objects retained). */
internal data class SurfaceHitCandidate(
    val kind: SurfaceKind,
    val distanceMeters: Float,
    val tracking: Boolean,
    val subsumed: Boolean,
    val insidePolygon: Boolean,
)

/** Result of pure-CPU nearest-valid surface selection. */
internal data class SurfaceProbeReading(
    val kind: SurfaceKind,
    val distanceMeters: Float?,
    val reason: String,
)

/**
 * Selects the nearest valid surface hit from primitive candidates.
 * Diagnostic only — no speech, navigation, or movement authority.
 */
internal object SurfaceProbePolicy {
    private const val MAX_CANDIDATES = 64
    private const val REASON_NOT_TRACKING = "not_tracking"
    private const val REASON_TOO_MANY_HITS = "too_many_hits"
    private const val REASON_NO_VALID_SURFACE = "no_valid_surface"
    private const val REASON_OBSERVED_SURFACE = "observed_surface"

    fun select(
        candidates: List<SurfaceHitCandidate>,
        tracking: Boolean,
    ): SurfaceProbeReading {
        if (!tracking) {
            return SurfaceProbeReading(
                kind = SurfaceKind.UNKNOWN,
                distanceMeters = null,
                reason = REASON_NOT_TRACKING,
            )
        }
        if (candidates.size > MAX_CANDIDATES) {
            return SurfaceProbeReading(
                kind = SurfaceKind.UNKNOWN,
                distanceMeters = null,
                reason = REASON_TOO_MANY_HITS,
            )
        }
        var nearest: SurfaceHitCandidate? = null
        for (candidate in candidates) {
            if (!isValid(candidate)) continue
            val current = nearest
            if (current == null || candidate.distanceMeters < current.distanceMeters) {
                nearest = candidate
            }
        }
        val selected = nearest
            ?: return SurfaceProbeReading(
                kind = SurfaceKind.UNKNOWN,
                distanceMeters = null,
                reason = REASON_NO_VALID_SURFACE,
            )
        return SurfaceProbeReading(
            kind = selected.kind,
            distanceMeters = selected.distanceMeters,
            reason = REASON_OBSERVED_SURFACE,
        )
    }

    private fun isValid(candidate: SurfaceHitCandidate): Boolean {
        if (candidate.kind == SurfaceKind.UNKNOWN) return false
        if (!candidate.tracking) return false
        if (candidate.subsumed) return false
        if (!candidate.insidePolygon) return false
        val distance = candidate.distanceMeters
        return distance.isFinite() && distance > 0f
    }
}

/** Named viewport ray in VIEW pixels (matches setDisplayGeometry space). */
internal data class SurfaceProbeRay(
    val name: String,
    val xPx: Float,
    val yPx: Float,
)

/**
 * Fixed diagnostic probe rays in display VIEW coordinates.
 * Not raw depth or CPU-image pixel space.
 */
internal object SurfaceProbeLayout {
    fun points(width: Int, height: Int): List<SurfaceProbeRay> {
        if (width <= 0 || height <= 0) return emptyList()
        val w = width.toFloat()
        val h = height.toFloat()
        return listOf(
            SurfaceProbeRay(name = "center", xPx = 0.5f * w, yPx = 0.5f * h),
            SurfaceProbeRay(name = "lower_left", xPx = 0.25f * w, yPx = 0.8f * h),
            SurfaceProbeRay(name = "lower_center", xPx = 0.5f * w, yPx = 0.8f * h),
            SurfaceProbeRay(name = "lower_right", xPx = 0.75f * w, yPx = 0.8f * h),
        )
    }
}
