package com.triguide.ai.ui

import com.triguide.ai.domain.DetectorReadiness
import com.triguide.ai.domain.DirectionalCue
import com.triguide.ai.domain.NavigationConfig
import com.triguide.ai.domain.PerceptionMode
import com.triguide.ai.domain.SceneObservation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DescribeEligibilityTest {
    private val fresh = SceneObservation(
        cues = listOf(DirectionalCue("chair", 0.90f, 0.50f, 0.12f)),
        mode = PerceptionMode.CAMERA_2D,
        realDetector = true,
        metricDepthAvailable = false,
        frameAgeMs = 100L,
        sampleCompletedAtElapsedMs = 1_000L,
    )

    private fun eligible(
        scene: SceneObservation = fresh,
        running: Boolean = true,
        permission: Boolean = true,
        state: String = "Streaming",
        readiness: DetectorReadiness = DetectorReadiness.READY,
        error: String? = null,
    ): Boolean = canDescribeFreshCameraScene(
        running = running,
        permissionGranted = permission,
        cameraState = state,
        readiness = readiness,
        inferenceError = error,
        scene = scene,
    )

    @Test
    fun freshStreamingRealDetectorSceneCanBeDescribed() {
        assertTrue(eligible())
        assertTrue(eligible(scene = fresh.copy(frameAgeMs = NavigationConfig.STALE_FRAME_MS - 1)))
    }

    @Test
    fun pausedOrUnavailableCameraCannotDescribeFrozenScene() {
        assertFalse(eligible(running = false))
        assertFalse(eligible(permission = false))
        assertFalse(eligible(state = "Idle"))
        assertFalse(eligible(state = "Starting camera"))
        assertFalse(eligible(state = "Failed: disconnected"))
        assertFalse(eligible(readiness = DetectorReadiness.LOADING))
        assertFalse(eligible(error = "inference failed"))
    }

    @Test
    fun missingOldOrContradictoryFrameCannotBeDescribed() {
        assertFalse(eligible(scene = fresh.copy(frameAgeMs = null)))
        assertFalse(eligible(scene = fresh.copy(frameAgeMs = NavigationConfig.STALE_FRAME_MS)))
        assertFalse(eligible(scene = fresh.copy(frameAgeMs = -1L)))
        assertFalse(eligible(scene = fresh.copy(realDetector = false)))
        assertFalse(eligible(scene = fresh.copy(mode = PerceptionMode.UNAVAILABLE)))
        assertFalse(eligible(scene = fresh.copy(contradictory = true)))
    }
}
