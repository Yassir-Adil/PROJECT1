package com.triguide.ai.domain

/**
 * The only admissible fusion direction for monocular depth in TriGuide.
 *
 * Depth is a soft, relative signal. It may add a diagnostic HOLD, but it may
 * never remove an existing hazard and may never establish clearance. Keeping
 * this rule in a small pure policy object makes later runtime integration
 * reviewable and prevents accidental coupling to the navigation engine.
 */
object RelativeDepthRiskGate {
    fun merge(
        detectorHold: Boolean,
        depthEvidence: RelativeDepthEvidence?,
    ): RelativeDepthRiskContribution {
        val depthRaisesRisk = depthEvidence?.raisesRisk == true
        return RelativeDepthRiskContribution(
            holdRequired = detectorHold || depthRaisesRisk,
            depthRaisedRisk = depthRaisesRisk,
            reason = when {
                detectorHold && depthRaisesRisk -> "detector_and_relative_depth_hold"
                detectorHold -> "detector_hold_preserved"
                depthRaisesRisk -> "relative_depth_hold_only"
                depthEvidence == null -> "relative_depth_unavailable"
                else -> "relative_depth_did_not_lower_risk"
            },
        )
    }
}

data class RelativeDepthRiskContribution(
    val holdRequired: Boolean,
    val depthRaisedRisk: Boolean,
    val reason: String,
)

/**
 * Bounded admission gate for a future depth worker.
 *
 * CameraX already keeps the detector path latest-only. This companion gate
 * gives a depth worker its own low-rate budget, so adding depth cannot create
 * an unbounded queue or contend with every camera frame.
 */
class RelativeDepthInferenceCadenceGate(
    private val minimumIntervalMs: Long = DEFAULT_MINIMUM_INTERVAL_MS,
) {
    private var lastAcceptedElapsedMs: Long? = null

    init {
        require(minimumIntervalMs > 0L) { "minimumIntervalMs must be positive" }
    }

    fun evaluate(nowElapsedMs: Long): RelativeDepthCadenceDecision {
        if (nowElapsedMs < 0L) {
            return RelativeDepthCadenceDecision(
                admitted = false,
                reason = RelativeDepthCadenceReason.INVALID_CLOCK,
                previousAcceptedElapsedMs = lastAcceptedElapsedMs,
            )
        }
        val previous = lastAcceptedElapsedMs
        if (previous != null) {
            if (nowElapsedMs < previous) {
                return RelativeDepthCadenceDecision(
                    admitted = false,
                    reason = RelativeDepthCadenceReason.CLOCK_ROLLBACK,
                    previousAcceptedElapsedMs = previous,
                )
            }
            if (nowElapsedMs - previous < minimumIntervalMs) {
                return RelativeDepthCadenceDecision(
                    admitted = false,
                    reason = RelativeDepthCadenceReason.TOO_SOON,
                    previousAcceptedElapsedMs = previous,
                )
            }
        }
        lastAcceptedElapsedMs = nowElapsedMs
        return RelativeDepthCadenceDecision(
            admitted = true,
            reason = RelativeDepthCadenceReason.ACCEPTED,
            previousAcceptedElapsedMs = previous,
        )
    }

    /** Compatibility helper for callers that only need the admission bit. */
    fun tryAdmit(nowElapsedMs: Long): Boolean = evaluate(nowElapsedMs).admitted

    data class RelativeDepthCadenceDecision(
        val admitted: Boolean,
        val reason: RelativeDepthCadenceReason,
        val previousAcceptedElapsedMs: Long?,
    )

    enum class RelativeDepthCadenceReason {
        ACCEPTED,
        INVALID_CLOCK,
        CLOCK_ROLLBACK,
        TOO_SOON,
    }

    fun reset() {
        lastAcceptedElapsedMs = null
    }

    companion object {
        /** Four samples per second is a conservative starting budget. */
        const val DEFAULT_MINIMUM_INTERVAL_MS = 250L
    }
}
