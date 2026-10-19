package com.triguide.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraNavigationSafetyInvariantTest {
    private val engine = NavigationDecisionEngine()
    private val observation = SceneObservation(
        cues = emptyList(),
        mode = PerceptionMode.CAMERA_2D,
        realDetector = true,
        metricDepthAvailable = false,
        frameAgeMs = 0L,
        sampleCompletedAtElapsedMs = 1_000L,
    )

    @Test
    fun everyCameraRiskRemainsHoldOrUnknown() {
        PathRiskLevel.values().forEach { level ->
            val decision = engine.decideCameraRisk(
                observation = observation,
                risk = PathRiskResult(
                    level = level,
                    corridorCues = emptyList(),
                    dominantInCorridor = null,
                    diagnostic = "diag:test_${level.name.lowercase()}",
                ),
            )

            assertTrue(
                "Camera risk $level produced ${decision.action}",
                decision.action == GuidanceAction.HOLD || decision.action == GuidanceAction.UNKNOWN,
            )
            assertFalse(decision.action == GuidanceAction.STRAIGHT)
            assertFalse(decision.action == GuidanceAction.LEFT)
            assertFalse(decision.action == GuidanceAction.RIGHT)
        }
    }

    @Test
    fun cameraClearRiskIsDowngradedToUnknown() {
        val decision = engine.decideCameraRisk(
            observation,
            PathRiskResult(
                level = PathRiskLevel.CLEAR_OBSERVED,
                corridorCues = emptyList(),
                dominantInCorridor = null,
                diagnostic = "diag:detector_clear_only",
            ),
        )

        assertEquals(GuidanceAction.UNKNOWN, decision.action)
        assertEquals(PathRiskLevel.UNKNOWN, decision.pathRisk)
        assertTrue(decision.reason.contains("cannot verify walkable area"))
    }
}
