package com.triguide.ai.domain

/**
 * Diagnostic cues for a positively observed walkable region.
 *
 * Detector corridor emptiness is deliberately not enough. The caller must
 * provide a successful [EvidenceFusionGate] result as well as a matching
 * CLEAR_OBSERVED path result. Until a rights-cleared producer is wired, the
 * live camera passes null and the green strip stays disabled.
 *
 * Soft open-lane beeps are not used for detector-empty UNKNOWN. A separate
 * [CameraActiveCue] covers the one-shot “app/camera active” tone.
 */
object OpenLaneCue {
    const val BEEP_COOLDOWN_MS = 2_800L

    /** Lower fraction of the preview used for the green walk-lane strip (0 = top, 1 = bottom). */
    const val LANE_TOP_Y = 0.42f

    const val DIAGNOSTIC_LABEL = "observed candidate — not a safety signal"

    fun showGreenWalkLane(
        pathRisk: PathRiskLevel,
        fusedEvidence: FusionGateResult?,
    ): Boolean =
        pathRisk == PathRiskLevel.CLEAR_OBSERVED &&
            fusedEvidence?.verdict == FusionPathVerdict.CLEAR_OBSERVED &&
            fusedEvidence.agreeingDistinctFrames >= EvidenceFusionGate.MIN_AGREEING_DISTINCT_FRAMES

    /**
     * Soft double-beep only when [showGreenWalkLane] is true (validated fusion).
     * Detector-only UNKNOWN must never beep here.
     */
    fun shouldSoftBeep(
        pathRisk: PathRiskLevel,
        fusedEvidence: FusionGateResult?,
        cameraStreaming: Boolean,
        guidanceRunning: Boolean,
        emergencyActive: Boolean,
        nowElapsedMs: Long,
        lastBeepElapsedMs: Long?,
        cooldownMs: Long = BEEP_COOLDOWN_MS,
    ): Boolean {
        if (emergencyActive) return false
        if (!guidanceRunning || !cameraStreaming) return false
        if (!showGreenWalkLane(pathRisk, fusedEvidence)) return false
        if (nowElapsedMs < 0L) return false
        val last = lastBeepElapsedMs ?: return true
        if (last < 0L) return true
        return nowElapsedMs - last >= cooldownMs
    }
}
