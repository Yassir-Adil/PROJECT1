package com.triguide.ai.domain

import com.triguide.ai.sim.PlanarPose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R1a: camera temporal confirmation counts distinct inference timestamps,
 * not 50 ms UI-loop replays of the same DetectionFrame.
 *
 * [FakePositiveWalkablePolicy] exists only for these tests. It must never be
 * wired into the production detector-only camera path.
 */
class CameraDistinctSampleVotingTest {

    @Test
    fun sameFrameReplayedAcrossManyTicksDoesNotConfirm() {
        val session = GuidanceSession(FakePositiveWalkablePolicy())
        val sampleAt = 1_000L
        var now = sampleAt + 50L
        var last = session.onCamera(cameraSample(completedAt = sampleAt, now = now))
        assertEquals(GuidanceAction.HOLD, last.action)
        assertEquals(0.0, last.linearSpeedMps, 0.0)
        assertEquals(0.0, last.steeringRadians, 0.0)

        // Stay under STALE_FRAME_MS so this checks replay voting, not staleness.
        repeat(15) {
            now += 50L
            last = session.onCamera(cameraSample(completedAt = sampleAt, now = now))
            assertEquals(GuidanceAction.HOLD, last.action)
            assertEquals(0.0, last.linearSpeedMps, 0.0)
            assertEquals(0.0, last.steeringRadians, 0.0)
            assertNotEquals(GuidanceAction.STRAIGHT, last.action)
        }
    }

    @Test
    fun threeDistinctTimestampsCanConfirmUnderFakePositivePolicy() {
        val session = GuidanceSession(FakePositiveWalkablePolicy())
        val first = session.onCamera(cameraSample(completedAt = 1_000L, now = 1_050L))
        val second = session.onCamera(cameraSample(completedAt = 1_120L, now = 1_170L))
        assertEquals(GuidanceAction.HOLD, first.action)
        assertEquals(GuidanceAction.HOLD, second.action)
        assertEquals(0.0, first.linearSpeedMps, 0.0)
        assertEquals(0.0, second.linearSpeedMps, 0.0)

        val third = session.onCamera(cameraSample(completedAt = 1_240L, now = 1_290L))
        assertEquals(GuidanceAction.STRAIGHT, third.action)
        assertTrue(third.linearSpeedMps > 0.0)
        assertEquals(0.0, third.steeringRadians, 0.0)
    }

    @Test
    fun staleReplayResetsToUnknownAndZeroCommand() {
        val session = GuidanceSession(FakePositiveWalkablePolicy())
        val sampleAt = 1_000L
        repeat(NavigationConfig.CONFIRM_FRAMES) { index ->
            session.onCamera(
                cameraSample(
                    completedAt = sampleAt + index * 100L,
                    now = sampleAt + index * 100L + 40L,
                ),
            )
        }
        val committed = session.decision.value
        assertEquals(GuidanceAction.STRAIGHT, committed.action)

        val stale = session.onCamera(
            cameraSample(
                completedAt = sampleAt + (NavigationConfig.CONFIRM_FRAMES - 1) * 100L,
                now = sampleAt + (NavigationConfig.CONFIRM_FRAMES - 1) * 100L +
                    NavigationConfig.STALE_FRAME_MS,
            ),
        )
        assertEquals(GuidanceAction.UNKNOWN, stale.action)
        assertEquals(0.0, stale.linearSpeedMps, 0.0)
        assertEquals(0.0, stale.steeringRadians, 0.0)
        assertTrue(stale.reason.contains("stale", ignoreCase = true))
    }

    @Test
    fun missingSampleIdentityResetsToUnknownAndClearsPendingVotes() {
        val session = GuidanceSession(FakePositiveWalkablePolicy())
        assertEquals(
            GuidanceAction.HOLD,
            session.onCamera(cameraSample(completedAt = 500L, now = 550L)).action,
        )
        val missing = session.onCamera(
            SceneObservation(
                cues = emptyList(),
                mode = PerceptionMode.CAMERA_2D,
                realDetector = true,
                metricDepthAvailable = false,
                frameAgeMs = 50L,
                sampleCompletedAtElapsedMs = null,
                summary = "missing identity",
            ),
        )
        assertEquals(GuidanceAction.UNKNOWN, missing.action)
        assertEquals(0.0, missing.linearSpeedMps, 0.0)
        assertEquals(0.0, missing.steeringRadians, 0.0)

        // Recovery must gather a full fresh distinct sequence again.
        assertEquals(
            GuidanceAction.HOLD,
            session.onCamera(cameraSample(completedAt = 800L, now = 850L)).action,
        )
        assertEquals(
            GuidanceAction.HOLD,
            session.onCamera(cameraSample(completedAt = 900L, now = 950L)).action,
        )
        assertEquals(
            GuidanceAction.STRAIGHT,
            session.onCamera(cameraSample(completedAt = 1_000L, now = 1_050L)).action,
        )
    }

    @Test
    fun outOfOrderSampleResetsToUnknownAndZeroCommand() {
        val session = GuidanceSession(FakePositiveWalkablePolicy())
        assertEquals(
            GuidanceAction.HOLD,
            session.onCamera(cameraSample(completedAt = 2_000L, now = 2_050L)).action,
        )
        val outOfOrder = session.onCamera(cameraSample(completedAt = 1_500L, now = 2_100L))
        assertEquals(GuidanceAction.UNKNOWN, outOfOrder.action)
        assertEquals(0.0, outOfOrder.linearSpeedMps, 0.0)
        assertEquals(0.0, outOfOrder.steeringRadians, 0.0)
        assertTrue(outOfOrder.reason.contains("out of order", ignoreCase = true))
    }

    @Test
    fun afterStaleResetAFreshSequenceMustStartOver() {
        val session = GuidanceSession(FakePositiveWalkablePolicy())
        session.onCamera(cameraSample(completedAt = 1_000L, now = 1_040L))
        session.onCamera(cameraSample(completedAt = 1_100L, now = 1_140L))
        session.onCamera(cameraSample(completedAt = 1_200L, now = 1_240L))
        assertEquals(GuidanceAction.STRAIGHT, session.decision.value.action)

        val stale = session.onCamera(cameraSample(completedAt = 1_200L, now = 1_200L + NavigationConfig.STALE_FRAME_MS))
        assertEquals(GuidanceAction.UNKNOWN, stale.action)

        val again1 = session.onCamera(cameraSample(completedAt = 3_000L, now = 3_040L))
        val again2 = session.onCamera(cameraSample(completedAt = 3_100L, now = 3_140L))
        assertEquals(GuidanceAction.HOLD, again1.action)
        assertEquals(GuidanceAction.HOLD, again2.action)
        assertEquals(0.0, again1.linearSpeedMps, 0.0)

        val again3 = session.onCamera(cameraSample(completedAt = 3_200L, now = 3_240L))
        assertEquals(GuidanceAction.STRAIGHT, again3.action)
    }

    @Test
    fun realDetectorBoxPathRemainsHoldOrUnknownThroughSession() {
        val session = GuidanceSession(NavigationDecisionEngine())
        val blocked = DetectionFrame(
            objects = listOf(DetectedObject("chair", 0.92f, 80f, 80f, 220f, 220f)),
            imageWidth = 300,
            imageHeight = 300,
            mirrorHorizontally = false,
            inferenceLatencyMs = 20.0,
            fromRealDetector = true,
            completedAtElapsedMs = 5_000L,
        )
        repeat(20) { tick ->
            val scene = SceneObservations.fromCamera(
                readiness = DetectorReadiness.READY,
                frame = blocked,
                nowElapsedMs = 5_000L + tick * 50L,
            )
            val decision = session.onCamera(scene)
            assertEquals(GuidanceAction.HOLD, decision.action)
            assertEquals(PathRiskLevel.BLOCKED, decision.pathRisk)
            assertEquals(0.0, decision.linearSpeedMps, 0.0)
            assertEquals(0.0, decision.steeringRadians, 0.0)
            assertNotEquals(GuidanceAction.STRAIGHT, decision.action)
            assertFalse(decision.metricDepthUsed)
        }

        val empty = DetectionFrame(
            objects = emptyList(),
            imageWidth = 300,
            imageHeight = 300,
            mirrorHorizontally = false,
            inferenceLatencyMs = 18.0,
            fromRealDetector = true,
            completedAtElapsedMs = 6_000L,
        )
        val unknown = session.onCamera(
            SceneObservations.fromCamera(
                readiness = DetectorReadiness.READY,
                frame = empty,
                nowElapsedMs = 6_050L,
            ),
        )
        assertEquals(GuidanceAction.UNKNOWN, unknown.action)
        assertEquals(PathRiskLevel.UNKNOWN, unknown.pathRisk)
        assertEquals(0.0, unknown.linearSpeedMps, 0.0)
        assertEquals(0.0, unknown.steeringRadians, 0.0)
    }

    @Test
    fun fromCameraCarriesCompletionTimestampIntoObservation() {
        val frame = DetectionFrame(
            objects = emptyList(),
            imageWidth = 200,
            imageHeight = 200,
            mirrorHorizontally = false,
            inferenceLatencyMs = 12.0,
            fromRealDetector = true,
            completedAtElapsedMs = 9_999L,
        )
        val scene = SceneObservations.fromCamera(
            readiness = DetectorReadiness.READY,
            frame = frame,
            nowElapsedMs = 10_099L,
        )
        assertEquals(9_999L, scene.sampleCompletedAtElapsedMs)
        assertEquals(100L, scene.frameAgeMs)
    }

    @Test
    fun syntheticVotingRemainsCallBasedAndUnchanged() {
        val session = GuidanceSession()
        val clear = SyntheticObstacleWorld.observe(PlanarPose(0.0, 3.0, 0.0))
        assertEquals(GuidanceAction.HOLD, session.onSynthetic(clear).action)
        assertEquals(GuidanceAction.HOLD, session.onSynthetic(clear).action)
        assertEquals(GuidanceAction.STRAIGHT, session.onSynthetic(clear).action)
        assertTrue(session.decision.value.metricDepthUsed)
        assertFalse(session.decision.value.basedOnRealVision)
    }

    private fun cameraSample(completedAt: Long, now: Long): SceneObservation {
        val age = (now - completedAt).coerceAtLeast(0L)
        return SceneObservation(
            cues = emptyList(),
            mode = PerceptionMode.CAMERA_2D,
            realDetector = true,
            metricDepthAvailable = false,
            frameAgeMs = age,
            sampleCompletedAtElapsedMs = completedAt,
            summary = "fake-clock camera sample",
        )
    }

    /**
     * Test-only positive walkable stand-in. Production camera path keeps
     * [NavigationDecisionEngine] and never authorizes clearance from boxes alone.
     */
    private class FakePositiveWalkablePolicy : NavigationDecisionPolicy {
        private val real = NavigationDecisionEngine()

        override fun decide(observation: SceneObservation): NavigationDecision {
            if (observation.mode != PerceptionMode.CAMERA_2D ||
                !observation.realDetector ||
                observation.contradictory ||
                observation.sampleCompletedAtElapsedMs == null ||
                observation.frameAgeMs == null ||
                observation.frameAgeMs!! >= NavigationConfig.STALE_FRAME_MS
            ) {
                return real.decide(observation)
            }
            return NavigationDecisions.fromAction(
                action = GuidanceAction.STRAIGHT,
                reason = "Fake positive walkable evidence for temporal tests only. " +
                    "Not wired to the detector-only camera path.",
                confidence = 0.70f,
                basedOnRealVision = true,
                metricDepthUsed = false,
                perceptionLabel = "FakeWalkable",
                pathRisk = PathRiskLevel.CLEAR_OBSERVED,
            )
        }
    }
}
