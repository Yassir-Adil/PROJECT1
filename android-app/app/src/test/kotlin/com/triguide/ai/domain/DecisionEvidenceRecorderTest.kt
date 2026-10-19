package com.triguide.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionEvidenceRecorderTest {
    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveCapacityIsRejected() {
        DecisionEvidenceRecorder(capacity = 0)
    }

    @Test
    fun recordsRingAndFormatsWithoutFrameClaims() {
        val recorder = DecisionEvidenceRecorder(capacity = 2)
        val hold = NavigationDecisions.fromAction(
            action = GuidanceAction.HOLD,
            reason = "Obstacle in proposed corridor.",
            confidence = 0.8f,
            basedOnRealVision = true,
            metricDepthUsed = false,
            perceptionLabel = NavigationConfig.CAMERA_LABEL,
            pathRisk = PathRiskLevel.BLOCKED,
            diagnostic = "diag:blocked label=chair",
            subjectLabel = "chair",
            subjectSide = "center",
        )
        val unknown = NavigationDecisions.fromAction(
            action = GuidanceAction.UNKNOWN,
            reason = "Off corridor only.",
            confidence = 0.4f,
            basedOnRealVision = true,
            metricDepthUsed = false,
            perceptionLabel = NavigationConfig.CAMERA_LABEL,
            pathRisk = PathRiskLevel.UNKNOWN,
            diagnostic = "diag:unknown off_corridor",
        )
        recorder.record(hold, 100)
        recorder.record(unknown, 200)
        recorder.record(hold, 300)
        assertEquals(2, recorder.snapshot().size)
        assertEquals(300L, recorder.snapshot().last().elapsedMs)
        val text = recorder.formatLatest(4).lowercase()
        assertTrue(text.contains("ai evidence recorder"))
        assertTrue(text.contains("action=hold"))
        assertFalse(text.contains("safe path"))
        assertFalse(text.contains("image saved"))
        assertFalse(text.contains("person photo"))
    }

    @Test
    fun guidanceSessionRecordsDistinctCameraVotes() {
        val session = GuidanceSession()
        val base = SceneObservation(
            cues = listOf(DirectionalCue("chair", 0.9f, 0.5f, 0.12f, 0.6f)),
            mode = PerceptionMode.CAMERA_2D,
            realDetector = true,
            metricDepthAvailable = false,
            frameAgeMs = 50L,
            sampleCompletedAtElapsedMs = 1_000L,
        )
        session.onCamera(base)
        session.onCamera(base.copy(sampleCompletedAtElapsedMs = 1_000L)) // replay — no new record
        session.onCamera(base.copy(sampleCompletedAtElapsedMs = 1_100L))
        assertEquals(2, session.evidenceRecorder.snapshot().size)
    }
}
