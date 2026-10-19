package com.triguide.ai.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenLaneCueTest {

    private fun fusedClear(
        frames: Int = EvidenceFusionGate.MIN_AGREEING_DISTINCT_FRAMES,
    ) = FusionGateResult(
        verdict = FusionPathVerdict.CLEAR_OBSERVED,
        turnHint = FusionTurnHint.NONE,
        reasonCode = "test",
        agreeingDistinctFrames = frames,
        diagnostic = "test",
    )

    @Test
    fun greenLaneRequiresPositiveFusionEvidence() {
        assertFalse(OpenLaneCue.showGreenWalkLane(PathRiskLevel.UNKNOWN, null))
        assertFalse(OpenLaneCue.showGreenWalkLane(PathRiskLevel.CLEAR_OBSERVED, null))
        assertFalse(OpenLaneCue.showGreenWalkLane(PathRiskLevel.POSSIBLE_OBSTACLE, null))
        assertFalse(OpenLaneCue.showGreenWalkLane(PathRiskLevel.BLOCKED, null))
        assertFalse(
            OpenLaneCue.showGreenWalkLane(
                PathRiskLevel.CLEAR_OBSERVED,
                fusedClear(frames = EvidenceFusionGate.MIN_AGREEING_DISTINCT_FRAMES - 1),
            ),
        )
        assertTrue(OpenLaneCue.showGreenWalkLane(PathRiskLevel.CLEAR_OBSERVED, fusedClear()))
    }

    @Test
    fun softBeepRequiresValidatedLaneNotDetectorEmpty() {
        assertFalse(
            OpenLaneCue.shouldSoftBeep(
                pathRisk = PathRiskLevel.UNKNOWN,
                fusedEvidence = null,
                cameraStreaming = true,
                guidanceRunning = true,
                emergencyActive = false,
                nowElapsedMs = 10_000L,
                lastBeepElapsedMs = null,
            ),
        )
        assertFalse(
            OpenLaneCue.shouldSoftBeep(
                pathRisk = PathRiskLevel.CLEAR_OBSERVED,
                fusedEvidence = fusedClear(),
                cameraStreaming = true,
                guidanceRunning = true,
                emergencyActive = true,
                nowElapsedMs = 10_000L,
                lastBeepElapsedMs = null,
            ),
        )
        assertTrue(
            OpenLaneCue.shouldSoftBeep(
                pathRisk = PathRiskLevel.CLEAR_OBSERVED,
                fusedEvidence = fusedClear(),
                cameraStreaming = true,
                guidanceRunning = true,
                emergencyActive = false,
                nowElapsedMs = 10_000L,
                lastBeepElapsedMs = null,
            ),
        )
        assertFalse(
            OpenLaneCue.shouldSoftBeep(
                pathRisk = PathRiskLevel.CLEAR_OBSERVED,
                fusedEvidence = fusedClear(),
                cameraStreaming = true,
                guidanceRunning = true,
                emergencyActive = false,
                nowElapsedMs = 10_000L,
                lastBeepElapsedMs = 9_000L,
                cooldownMs = 2_800L,
            ),
        )
        assertTrue(
            OpenLaneCue.shouldSoftBeep(
                pathRisk = PathRiskLevel.CLEAR_OBSERVED,
                fusedEvidence = fusedClear(),
                cameraStreaming = true,
                guidanceRunning = true,
                emergencyActive = false,
                nowElapsedMs = 12_900L,
                lastBeepElapsedMs = 10_000L,
                cooldownMs = 2_800L,
            ),
        )
    }
}
