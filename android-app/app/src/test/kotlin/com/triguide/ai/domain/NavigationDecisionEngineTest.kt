package com.triguide.ai.domain

import com.triguide.ai.robot.SimulatedTricycleController
import com.triguide.ai.sim.PlanarPose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class NavigationDecisionEngineTest {
    private val engine = NavigationDecisionEngine()

    @Test
    fun obstacleOnTheLeftDoesNotProveARightSideGap() {
        // Left portion of the walking corridor (0.25–0.75), not the far outer edge.
        val decision = engine.decide(camera(cues = listOf(strongCue("chair", center = 0.30f))))
        assertEquals(GuidanceAction.HOLD, decision.action)
        assertEquals(0.0, decision.steeringRadians, 0.0)
        assertEquals(0.0, decision.linearSpeedMps, 0.0)
        assertEquals("chair", decision.subjectLabel)
        assertEquals("left", decision.subjectSide)
        assertTrue(decision.basedOnRealVision)
        assertFalse(decision.metricDepthUsed)
        assertTrue(decision.reason.contains("left side"))
        assertTrue(decision.reason.contains("do not confirm a walkable gap"))
        assertTrue(decision.reason.contains("Depth is not measured"))
        assertNoMetricClaim(decision)
    }

    @Test
    fun obstacleOnTheRightDoesNotProveALeftSideGap() {
        val decision = engine.decide(camera(cues = listOf(strongCue("person", center = 0.70f))))
        assertEquals(GuidanceAction.HOLD, decision.action)
        assertEquals(0.0, decision.steeringRadians, 0.0)
        assertEquals(0.0, decision.linearSpeedMps, 0.0)
        assertTrue(decision.reason.contains("right side"))
        assertTrue(decision.reason.contains("do not confirm a walkable gap"))
        assertEquals("person", decision.subjectLabel)
        assertEquals("right", decision.subjectSide)
        assertNoMetricClaim(decision)
    }

    @Test
    fun farLeftFurnitureOutsideNarrowCorridorIsUnknown() {
        val frame = DetectionFrame(
            objects = listOf(DetectedObject("chair", 0.90f, 0f, 25f, 16f, 85f)),
            imageWidth = 100,
            imageHeight = 100,
            mirrorHorizontally = false,
            inferenceLatencyMs = 20.0,
            fromRealDetector = true,
            completedAtElapsedMs = 1_000L,
        )
        val scene = SceneObservations.fromCamera(DetectorReadiness.READY, frame, 1_100L)
        assertEquals(0.08, scene.cues.single().centerXNormalized.toDouble(), 0.001)
        assertEquals(0.00, scene.cues.single().boxLeftNormalized!!.toDouble(), 0.001)
        assertEquals(0.16, scene.cues.single().boxRightNormalized!!.toDouble(), 0.001)
        val decision = engine.decide(scene)
        assertEquals(PathRiskLevel.UNKNOWN, decision.pathRisk)
        assertEquals(GuidanceAction.UNKNOWN, decision.action)
        assertEquals(0.0, decision.linearSpeedMps, 0.0)
        assertEquals(0.0, decision.steeringRadians, 0.0)
        assertNoMetricClaim(decision)
    }

    @Test
    fun edgeIgnoredItemsOutsideNarrowCorridorAreUnknown() {
        val crossing = listOf(
            DirectionalCue(
                "knife", 0.75f, 0.08f, 0.016f, centerYNormalized = 0.80f,
                boxLeftNormalized = 0.00f, boxRightNormalized = 0.16f,
                boxTopNormalized = 0.75f, boxBottomNormalized = 0.85f,
            ),
            DirectionalCue(
                "cup", 0.80f, 0.92f, 0.016f, centerYNormalized = 0.80f,
                boxLeftNormalized = 0.84f, boxRightNormalized = 1.00f,
                boxTopNormalized = 0.75f, boxBottomNormalized = 0.85f,
            ),
        )
        crossing.forEach { cue ->
            val decision = engine.decide(camera(cues = listOf(cue)))
            assertEquals(PathRiskLevel.UNKNOWN, decision.pathRisk)
            assertEquals(GuidanceAction.UNKNOWN, decision.action)
            assertEquals(0.0, decision.linearSpeedMps, 0.0)
        }
        val malformed = engine.decide(
            camera(cues = listOf(crossing.first().copy(boxRightNormalized = Float.NaN))),
        )
        assertEquals(PathRiskLevel.UNKNOWN, malformed.pathRisk)
        assertEquals(GuidanceAction.UNKNOWN, malformed.action)
        assertEquals(0.0, malformed.linearSpeedMps, 0.0)
        assertEquals(0.0, malformed.steeringRadians, 0.0)
    }

    @Test
    fun aLargeSideBoxStillHoldsWithoutAWalkableAreaModel() {
        val decision = engine.decide(
            camera(cues = listOf(DirectionalCue("person", 0.90f, 0.30f, 0.45f))),
        )
        assertEquals(GuidanceAction.HOLD, decision.action)
        assertEquals(0.0, decision.linearSpeedMps, 0.0)
        assertEquals(0.0, decision.steeringRadians, 0.0)
        assertTrue(decision.reason.contains("do not confirm a walkable gap"))
        assertNoMetricClaim(decision)
    }

    @Test
    fun aBoxInsideTheOldMiddleThirdButLeftOfTheBlockingBandStillHolds() {
        val decision = engine.decide(
            camera(cues = listOf(DirectionalCue("person", 0.80f, 0.38f, 0.08f))),
        )
        assertEquals(GuidanceAction.HOLD, decision.action)
        assertEquals(0.0, decision.linearSpeedMps, 0.0)
        assertEquals(0.0, decision.steeringRadians, 0.0)
        assertEquals("left", decision.subjectSide)
        assertTrue(decision.reason.contains("do not confirm a walkable gap"))
        assertNoMetricClaim(decision)
    }

    @Test
    fun lowerCorridorCupHoldsWithoutClaimingDepthOrClearance() {
        val decision = engine.decide(
            camera(cues = listOf(DirectionalCue("cup", 0.80f, 0.50f, 0.02f, centerYNormalized = 0.85f))),
        )
        assertEquals(PathRiskLevel.POSSIBLE_OBSTACLE, decision.pathRisk)
        assertEquals(GuidanceAction.HOLD, decision.action)
        assertEquals("cup", decision.subjectLabel)
        assertEquals(0.0, decision.linearSpeedMps, 0.0)
        assertEquals(0.0, decision.steeringRadians, 0.0)
        assertNull(VoiceCues.fromDecision(decision))
        assertNoMetricClaim(decision)
    }

    @Test
    fun lowerCorridorKnifeHoldsButOffCorridorKnifeDoesNotProveClearance() {
        val lower = engine.decide(
            camera(cues = listOf(DirectionalCue("knife", 0.75f, 0.52f, 0.015f, centerYNormalized = 0.88f))),
        )
        assertEquals(PathRiskLevel.POSSIBLE_OBSTACLE, lower.pathRisk)
        assertEquals(GuidanceAction.HOLD, lower.action)
        assertEquals(0.0, lower.linearSpeedMps, 0.0)
        assertNoMetricClaim(lower)

        val offCorridor = engine.decide(
            camera(cues = listOf(DirectionalCue("knife", 0.75f, 0.04f, 0.015f, centerYNormalized = 0.88f))),
        )
        assertEquals(PathRiskLevel.UNKNOWN, offCorridor.pathRisk)
        assertEquals(GuidanceAction.UNKNOWN, offCorridor.action)
        assertEquals(0.0, offCorridor.linearSpeedMps, 0.0)
    }

    @Test
    fun accidentalCameraClearRiskFailsClosedWithoutChangingSyntheticClear() {
        val cameraDecision = engine.decideCameraRisk(
            observation = camera(),
            risk = PathRiskResult(
                level = PathRiskLevel.CLEAR_OBSERVED,
                corridorCues = emptyList(),
                dominantInCorridor = null,
                diagnostic = "diag:clear accidental_camera_result",
            ),
        )
        assertEquals(GuidanceAction.UNKNOWN, cameraDecision.action)
        assertEquals(PathRiskLevel.UNKNOWN, cameraDecision.pathRisk)
        assertEquals(0.0, cameraDecision.linearSpeedMps, 0.0)
        assertEquals(0.0, cameraDecision.steeringRadians, 0.0)
        assertFalse(cameraDecision.metricDepthUsed)
        assertTrue(cameraDecision.diagnostic.contains("camera_clear_without_positive_evidence"))

        val syntheticClear = engine.decide(SyntheticObstacleWorld.observe(PlanarPose(0.0, 3.0, 0.0)))
        assertEquals(GuidanceAction.STRAIGHT, syntheticClear.action)
        assertEquals(PathRiskLevel.CLEAR_OBSERVED, syntheticClear.pathRisk)
    }

    @Test
    fun chairNearTheCenterBandHoldsWithoutProvingASidePassage() {
        val decision = engine.decide(
            camera(cues = listOf(DirectionalCue("chair", 0.80f, 0.36f, 0.12f))),
        )
        assertEquals(GuidanceAction.HOLD, decision.action)
        assertEquals(PathRiskLevel.BLOCKED, decision.pathRisk)
        assertEquals(0.0, decision.linearSpeedMps, 0.0)
        assertEquals(0.0, decision.steeringRadians, 0.0)
        assertTrue(decision.reason.contains("do not confirm a walkable gap"))
    }

    @Test
    fun centerObstacleHoldsWithoutChoosingASide() {
        val decision = engine.decide(camera(cues = listOf(strongCue("person", center = 0.50f))))
        assertEquals(GuidanceAction.HOLD, decision.action)
        assertEquals(PathRiskLevel.BLOCKED, decision.pathRisk)
        assertEquals(0.0, decision.linearSpeedMps, 0.0)
        assertEquals(0.0, decision.steeringRadians, 0.0)
        assertEquals("person", decision.subjectLabel)
        assertEquals("center", decision.subjectSide)
        assertTrue(decision.reason.contains("corridor center") || decision.reason.contains("Stop and check"))
        assertEquals("Obstacle ahead.", VoiceCues.fromDecision(decision)?.english)
        assertNoMetricClaim(decision)
    }

    @Test
    fun emptyFrameIsUnknownAndStopsWithoutRepeatedSpeech() {
        val decision = engine.decide(camera())
        assertEquals(GuidanceAction.UNKNOWN, decision.action)
        assertEquals(PathRiskLevel.UNKNOWN, decision.pathRisk)
        assertTrue(decision.reason.contains("Path risk is unknown"))
        assertTrue(decision.reason.contains("Depth is not measured"))
        assertEquals(0.0, decision.linearSpeedMps, 0.0)
        assertEquals(0.0, decision.steeringRadians, 0.0)
        assertFalse(decision.metricDepthUsed)
        assertNull(VoiceCues.fromDecision(decision))
    }

    @Test
    fun bothSidesWithoutADominantBoxHold() {
        val decision = engine.decide(
            camera(
                cues = listOf(
                    strongCue("chair", center = 0.30f),
                    strongCue("table", center = 0.70f),
                ),
            ),
        )
        assertEquals(GuidanceAction.HOLD, decision.action)
        assertEquals(PathRiskLevel.BLOCKED, decision.pathRisk)
        assertTrue(decision.reason.contains("both sides") || decision.reason.contains("Stop and check"))
        assertEquals("Path blocked.", VoiceCues.fromDecision(decision)?.english)
        assertNoMetricClaim(decision)
    }

    @Test
    fun dominantLeftWithAWeakRightBoxStillDoesNotProveARoute() {
        val decision = engine.decide(
            camera(
                cues = listOf(
                    DirectionalCue("chair", 0.80f, 0.30f, 0.25f),
                    DirectionalCue("bottle", 0.60f, 0.70f, 0.20f),
                ),
            ),
        )
        assertEquals(GuidanceAction.HOLD, decision.action)
        assertEquals(0.0, decision.linearSpeedMps, 0.0)
        assertEquals(0.0, decision.steeringRadians, 0.0)
        assertEquals("chair", decision.subjectLabel)
        assertEquals("left", decision.subjectSide)
        val spoken = VoiceCues.fromDecision(decision)
        assertEquals("Obstacle ahead.", spoken?.english)
        assertNoMetricClaim(decision)
    }

    @Test
    fun strongLeftAndStrongRightHold() {
        val decision = engine.decide(
            camera(
                cues = listOf(
                    DirectionalCue("chair", 0.80f, 0.30f, 0.25f),
                    DirectionalCue("table", 0.80f, 0.70f, 0.20f),
                ),
            ),
        )
        assertEquals(GuidanceAction.HOLD, decision.action)
        assertEquals(0.0, decision.linearSpeedMps, 0.0)
        assertEquals(0.0, decision.steeringRadians, 0.0)
        assertEquals("both", decision.subjectSide)
        assertTrue(decision.reason.contains("both sides"))
        val spoken = VoiceCues.fromDecision(decision)
        assertEquals("Path blocked.", spoken?.english)
        assertFalse(spoken!!.english.contains("Turn"))
        assertNoMetricClaim(decision)
    }

    @Test
    fun centerBoxHolds() {
        val decision = engine.decide(camera(cues = listOf(DirectionalCue("chair", 0.90f, 0.50f, 0.22f))))
        assertEquals(GuidanceAction.HOLD, decision.action)
        assertEquals("chair", decision.subjectLabel)
        assertEquals("center", decision.subjectSide)
        assertEquals("Obstacle ahead.", VoiceCues.fromDecision(decision)?.english)
        assertNoMetricClaim(decision)
    }

    @Test
    fun multipleBoxesStillDoNotProveAnAlternateRoute() {
        val decision = engine.decide(
            camera(
                cues = listOf(
                    DirectionalCue("chair", 0.55f, 0.36f, 0.14f),
                    DirectionalCue("bottle", 0.50f, 0.75f, 0.07f),
                    DirectionalCue("cup", 0.48f, 0.86f, 0.06f),
                    DirectionalCue("book", 0.46f, 0.68f, 0.06f),
                ),
            ),
        )
        assertEquals(GuidanceAction.HOLD, decision.action)
        assertEquals(0.0, decision.linearSpeedMps, 0.0)
        assertEquals(0.0, decision.steeringRadians, 0.0)
        assertEquals("chair", decision.subjectLabel)
        assertEquals("left", decision.subjectSide)
        val spoken = VoiceCues.fromDecision(decision)
        assertEquals("Obstacle ahead.", spoken?.english)
        assertNoMetricClaim(decision)
    }

    @Test
    fun dominantChairIsNamedWhenSmallerCupAlsoRemainsInCorridor() {
        val decision = engine.decide(
            camera(
                cues = listOf(
                    DirectionalCue("chair", 0.36f, 0.30f, 0.40f),
                    DirectionalCue("cup", 0.95f, 0.40f, 0.10f),
                ),
            ),
        )
        assertEquals(GuidanceAction.HOLD, decision.action)
        assertEquals(0.0, decision.linearSpeedMps, 0.0)
        assertEquals(0.0, decision.steeringRadians, 0.0)
        assertEquals("chair", decision.subjectLabel)
        assertEquals("Obstacle ahead.", VoiceCues.fromDecision(decision)?.english)
        assertNoMetricClaim(decision)
    }

    @Test
    fun leftThirdPersonDoesNotProveARightSideGap() {
        val decision = engine.decide(
            camera(
                cues = listOf(
                    DirectionalCue("person", 0.90f, 0.30f, 0.30f),
                    DirectionalCue("bottle", 0.40f, 0.50f, 0.02f),
                ),
            ),
        )
        assertEquals(GuidanceAction.HOLD, decision.action)
        assertEquals(0.0, decision.linearSpeedMps, 0.0)
        assertEquals(0.0, decision.steeringRadians, 0.0)
        assertEquals("person", decision.subjectLabel)
        assertEquals("left", decision.subjectSide)
        val spoken = VoiceCues.fromDecision(decision)
        assertEquals("Obstacle ahead.", spoken?.english)
        assertNoMetricClaim(decision)
    }

    @Test
    fun aStrongCenterBoxHoldsAndDoesNotCallThePersonLeft() {
        val decision = engine.decide(
            camera(
                cues = listOf(
                    DirectionalCue("person", 0.80f, 0.20f, 0.20f),
                    DirectionalCue("chair", 0.75f, 0.50f, 0.15f),
                ),
            ),
        )
        assertEquals(GuidanceAction.HOLD, decision.action)
        assertEquals("chair", decision.subjectLabel)
        assertEquals("center", decision.subjectSide)
        val spoken = VoiceCues.fromDecision(decision)
        assertEquals("Obstacle ahead.", spoken?.english)
        assertFalse(spoken!!.english.contains("left"))
        assertEquals("في عائق قدامك.", spoken.arabic)
        assertNoMetricClaim(decision)
    }

    @Test
    fun aMuchStrongerSideStillDoesNotProveAnAlternateRoute() {
        val decision = engine.decide(
            camera(
                cues = listOf(
                    DirectionalCue("couch", 0.95f, 0.30f, 0.40f),
                    DirectionalCue("cup", 0.55f, 0.70f, 0.02f),
                ),
            ),
        )
        assertEquals(GuidanceAction.HOLD, decision.action)
        assertEquals(0.0, decision.linearSpeedMps, 0.0)
        assertEquals(0.0, decision.steeringRadians, 0.0)
        assertEquals("couch", decision.subjectLabel)
        assertEquals("left", decision.subjectSide)
    }

    @Test
    fun missingDepthDoesNotInventClearance() {
        val claimed = engine.decide(camera(cues = listOf(strongCue("chair", 0.15f)), depth = true))
        assertEquals(GuidanceAction.UNKNOWN, claimed.action)
        assertFalse(claimed.metricDepthUsed)
        assertEquals(0.0, claimed.linearSpeedMps, 0.0)
        assertTrue(claimed.reason.contains("contradictory"))
        assertTrue(claimed.reason.contains("Depth is not measured"))

        val side = engine.decide(camera(cues = listOf(strongCue("chair", 0.15f))))
        assertFalse(side.metricDepthUsed)
        assertFalse(side.reason.contains("meter"))
        assertNotEquals(GuidanceAction.STRAIGHT, side.action)
    }

    @Test
    fun aNonFiniteCueDoesNotSteer() {
        val decision = engine.decide(
            camera(cues = listOf(DirectionalCue("chair", Float.NaN, 0.15f, 0.25f))),
        )
        assertEquals(GuidanceAction.UNKNOWN, decision.action)
        assertEquals(0.0, decision.steeringRadians, 0.0)
        assertEquals(0.0, decision.linearSpeedMps, 0.0)
    }

    @Test
    fun aCueBelowTheScoreFloorDoesNotSteer() {
        val below = engine.decide(
            camera(
                cues = listOf(
                    DirectionalCue("chair", DetectorConfig.SCORE_THRESHOLD - 0.01f, 0.15f, 0.25f),
                ),
            ),
        )
        assertEquals(GuidanceAction.UNKNOWN, below.action)
        assertEquals(PathRiskLevel.UNKNOWN, below.pathRisk)
        assertEquals(0.0, below.linearSpeedMps, 0.0)
        assertEquals(0.0, below.steeringRadians, 0.0)

        val atFloor = engine.decide(
            camera(
                cues = listOf(
                    DirectionalCue("chair", DetectorConfig.SCORE_THRESHOLD, 0.36f, 0.25f),
                ),
            ),
        )
        assertEquals(GuidanceAction.HOLD, atFloor.action)
        assertEquals(0.0, atFloor.linearSpeedMps, 0.0)
        assertEquals(0.0, atFloor.steeringRadians, 0.0)
    }

    @Test
    fun aZeroAreaCueIsNotAPassage() {
        val decision = engine.decide(
            camera(cues = listOf(DirectionalCue("speck", 0.99f, 0.10f, 0f))),
        )
        assertEquals(GuidanceAction.UNKNOWN, decision.action)
        assertEquals(PathRiskLevel.UNKNOWN, decision.pathRisk)
        assertEquals(0.0, decision.steeringRadians, 0.0)
        assertEquals(0.0, decision.linearSpeedMps, 0.0)
    }

    @Test
    fun contradictoryObservationsDoNotPickASide() {
        val flagged = engine.decide(
            camera(cues = listOf(strongCue("chair", 0.15f)), contradictory = true),
        )
        assertEquals(GuidanceAction.UNKNOWN, flagged.action)
        assertEquals(0.0, flagged.steeringRadians, 0.0)

        val impossible = engine.decide(
            camera(cues = listOf(DirectionalCue("chair", 0.90f, 1.40f, 0.20f))),
        )
        assertEquals(GuidanceAction.UNKNOWN, impossible.action)
    }

    @Test
    fun staleFrameHolds() {
        val fresh = engine.decide(camera(cues = listOf(strongCue("chair", 0.36f)), age = 999L))
        assertEquals(GuidanceAction.HOLD, fresh.action)
        assertEquals(0.0, fresh.linearSpeedMps, 0.0)
        assertEquals(0.0, fresh.steeringRadians, 0.0)

        val stale = engine.decide(
            camera(cues = listOf(strongCue("chair", 0.36f)), age = NavigationConfig.STALE_FRAME_MS),
        )
        assertEquals(GuidanceAction.UNKNOWN, stale.action)
        assertEquals(PathRiskLevel.UNKNOWN, stale.pathRisk)
        assertEquals(0.0, stale.linearSpeedMps, 0.0)
        assertTrue(stale.reason.contains("stale"))
    }

    @Test
    fun missingTimestampDoesNotSteer() {
        val decision = engine.decide(camera(cues = listOf(strongCue("chair", 0.15f)), age = null))
        assertEquals(GuidanceAction.UNKNOWN, decision.action)
        assertTrue(decision.reason.contains("timestamp"))
        assertEquals(0.0, decision.steeringRadians, 0.0)
    }

    @Test
    fun emptyCameraCorridorIsUnknownAndStops() {
        val clear = engine.decide(camera())
        assertEquals(GuidanceAction.UNKNOWN, clear.action)
        assertEquals(PathRiskLevel.UNKNOWN, clear.pathRisk)
        assertEquals(0.0, clear.linearSpeedMps, 0.0)
        assertEquals(0.0, clear.steeringRadians, 0.0)

        val blocked = engine.decide(camera(cues = listOf(strongCue("chair", 0.50f))))
        assertEquals(GuidanceAction.HOLD, blocked.action)
        assertEquals(PathRiskLevel.BLOCKED, blocked.pathRisk)
    }

    @Test
    fun unavailableFrameIsUnknown() {
        val decision = engine.decide(camera(real = false, mode = PerceptionMode.UNAVAILABLE))
        assertEquals(GuidanceAction.UNKNOWN, decision.action)
        assertFalse(decision.basedOnRealVision)
        assertEquals(0.0, decision.linearSpeedMps, 0.0)
    }

    @Test
    fun confidenceStaysInsideZeroToOne() {
        val decisions = listOf(
            engine.decide(camera(cues = listOf(strongCue("chair", 0.15f)))),
            engine.decide(camera()),
            engine.decide(camera(contradictory = true)),
            engine.decide(SyntheticObstacleWorld.observe(PlanarPose(0.0, 0.0, 0.0))),
        )
        decisions.forEach { decision ->
            assertTrue(decision.confidence in 0f..1f)
            assertTrue(decision.spokenCue.contains(decision.action.userLabel))
        }
    }

    @Test
    fun rapidDirectionChangesDoNotFlip() {
        val smoother = TemporalDecisionSmoother()
        val leftSide = camera(cues = listOf(strongCue("chair", 0.30f)))
        val rightSide = camera(cues = listOf(strongCue("chair", 0.70f)))
        listOf(leftSide, rightSide, leftSide, rightSide, leftSide, rightSide).forEach { scene ->
            val decision = smoother.update(scene)
            assertEquals(GuidanceAction.HOLD, decision.action)
            assertEquals(0.0, decision.linearSpeedMps, 0.0)
            assertEquals(0.0, decision.steeringRadians, 0.0)
        }
    }

    @Test
    fun cameraObjectBoxesNeverCommitASteeringDirection() {
        val smoother = TemporalDecisionSmoother()
        val leftSide = camera(cues = listOf(strongCue("chair", 0.36f)))
        val rightSide = camera(cues = listOf(strongCue("chair", 0.64f)))
        repeat(NavigationConfig.CONFIRM_FRAMES * 2) {
            assertEquals(GuidanceAction.HOLD, smoother.update(leftSide).action)
            val decision = smoother.update(rightSide)
            assertEquals(GuidanceAction.HOLD, decision.action)
            assertEquals(0.0, decision.linearSpeedMps, 0.0)
            assertEquals(0.0, decision.steeringRadians, 0.0)
        }
    }

    @Test
    fun magnitudeChangeAlsoWaits() {
        val smoother = TemporalDecisionSmoother()
        val strong = camera(cues = listOf(strongCue("chair", 0.36f)))
        val slight = camera(cues = listOf(DirectionalCue("chair", 0.80f, 0.30f, 0.10f)))
        repeat(NavigationConfig.CONFIRM_FRAMES) {
            assertEquals(GuidanceAction.HOLD, smoother.update(strong).action)
            assertEquals(GuidanceAction.HOLD, smoother.update(slight).action)
        }
    }

    @Test
    fun staleFrameClearsACommittedTurn() {
        val smoother = TemporalDecisionSmoother()
        val fresh = camera(cues = listOf(strongCue("chair", 0.36f)))
        repeat(NavigationConfig.CONFIRM_FRAMES) { smoother.update(fresh) }
        val stale = smoother.update(fresh.copy(frameAgeMs = NavigationConfig.STALE_FRAME_MS))
        assertEquals(GuidanceAction.UNKNOWN, stale.action)
        assertTrue(stale.reason.contains("stale"))
        assertEquals(GuidanceAction.HOLD, smoother.update(fresh).action)
    }

    @Test
    fun syntheticOriginSteersAwayFromTheLeftBarrel() {
        val scene = SyntheticObstacleWorld.observe(PlanarPose(0.0, 0.0, 0.0))
        val decision = engine.decide(scene)
        assertEquals(GuidanceAction.RIGHT, decision.action)
        assertTrue(decision.metricDepthUsed)
        assertFalse(decision.basedOnRealVision)
        assertTrue(decision.reason.contains("Synthetic map"))
        assertTrue(decision.reason.contains("left barrel"))
        assertEquals(NavigationConfig.SYNTHETIC_LABEL, decision.perceptionLabel)
    }

    @Test
    fun syntheticClearCorridorGoesStraight() {
        val scene = SyntheticObstacleWorld.observe(PlanarPose(0.0, 3.0, 0.0))
        val decision = engine.decide(scene)
        assertEquals(GuidanceAction.STRAIGHT, decision.action)
        assertEquals(0.0, decision.steeringRadians, 1e-9)
        assertTrue(decision.linearSpeedMps > 0.0)
        assertTrue(decision.metricDepthUsed)
        assertTrue(decision.reason.contains("forward corridor"))
        assertFalse(decision.basedOnRealVision)
    }

    @Test
    fun syntheticCenterObstacleHolds() {
        val scene = synthetic(KnownObstacle("post", forwardMeters = 1.0, lateralMeters = 0.0))
        val decision = engine.decide(scene)
        assertEquals(GuidanceAction.HOLD, decision.action)
        assertTrue(decision.metricDepthUsed)
        assertTrue(decision.reason.contains("forward axis"))
        assertEquals(0.0, decision.linearSpeedMps, 0.0)
    }

    @Test
    fun syntheticObstaclesOnBothSidesHold() {
        val scene = synthetic(
            KnownObstacle("left", 1.0, 0.25),
            KnownObstacle("right", 1.0, -0.25),
        )
        assertEquals(GuidanceAction.HOLD, engine.decide(scene).action)
    }

    @Test
    fun syntheticSideOffsetCanBeSlight() {
        val decision = engine.decide(synthetic(KnownObstacle("crate", 1.0, -0.40)))
        assertEquals(GuidanceAction.SLIGHT_LEFT, decision.action)
        assertTrue(decision.steeringRadians > 0.0)
    }

    @Test
    fun incompleteSyntheticCatalogDoesNotGoStraight() {
        val scene = SceneObservation(
            cues = emptyList(),
            mode = PerceptionMode.SYNTHETIC_WORLD,
            realDetector = false,
            metricDepthAvailable = true,
            frameAgeMs = 0L,
            knownObstacles = emptyList(),
            catalogComplete = false,
        )
        val decision = engine.decide(scene)
        assertEquals(GuidanceAction.HOLD, decision.action)
        assertFalse(decision.metricDepthUsed)
        assertNotEquals(GuidanceAction.STRAIGHT, decision.action)
    }

    @Test
    fun cameraObstacleKeepsTheVirtualRobotStoppedWithoutAWalkableMap() {
        val smoother = TemporalDecisionSmoother(engine)
        val robot = SimulatedTricycleController()
        val scene = camera(cues = listOf(strongCue("chair", 0.36f)))
        var decision = smoother.update(scene)
        repeat(NavigationConfig.CONFIRM_FRAMES - 1) {
            decision = smoother.update(scene)
        }
        assertEquals(GuidanceAction.HOLD, decision.action)
        robot.setCommand(decision.toSteeringCommand())
        assertEquals(decision.steeringRadians, robot.state.value.steeringRadians, 1e-9)
        assertEquals(0.0, decision.linearSpeedMps, 0.0)
        assertEquals(0.0, robot.state.value.steeringRadians, 1e-9)
        repeat(15) { robot.tick(0.1) }
        assertEquals(0.0, robot.state.value.pose.headingRadians, 1e-9)
        assertEquals(0.0, robot.state.value.pose.xMeters, 1e-9)
    }

    @Test
    fun frontCameraBoxIsMirroredIntoPreviewSpace() {
        val frame = DetectionFrame(
            objects = listOf(DetectedObject("cup", 0.88f, 0f, 0f, 40f, 40f)),
            imageWidth = 200,
            imageHeight = 200,
            mirrorHorizontally = true,
            inferenceLatencyMs = 20.0,
            fromRealDetector = true,
            completedAtElapsedMs = 1_000L,
        )
        val scene = SceneObservations.fromCamera(
            readiness = DetectorReadiness.READY,
            frame = frame,
            nowElapsedMs = 1_100L,
        )
        assertEquals(PerceptionMode.CAMERA_2D, scene.mode)
        assertFalse(scene.metricDepthAvailable)
        assertEquals(100L, scene.frameAgeMs)
        assertEquals(0.90, scene.cues.single().centerXNormalized.toDouble(), 0.001)
        assertEquals(0.80, scene.cues.single().boxLeftNormalized!!.toDouble(), 0.001)
        assertEquals(1.00, scene.cues.single().boxRightNormalized!!.toDouble(), 0.001)
        assertEquals(0.00, scene.cues.single().boxTopNormalized!!.toDouble(), 0.001)
        assertEquals(0.20, scene.cues.single().boxBottomNormalized!!.toDouble(), 0.001)
        // This mirrored box remains above the walking corridor; it proves no clearance.
        assertEquals(PathRiskLevel.UNKNOWN, engine.decide(scene).pathRisk)
        assertEquals(GuidanceAction.UNKNOWN, engine.decide(scene).action)
    }

    @Test
    fun missingModelProducesNoSteeringSample() {
        val planted = DetectionFrame(
            objects = listOf(DetectedObject("person", 0.99f, 0f, 0f, 80f, 80f)),
            imageWidth = 200,
            imageHeight = 200,
            mirrorHorizontally = false,
            inferenceLatencyMs = 10.0,
            fromRealDetector = true,
            completedAtElapsedMs = 10L,
        )
        val scene = SceneObservations.fromCamera(
            readiness = DetectorReadiness.MODEL_MISSING,
            frame = planted,
            nowElapsedMs = 20L,
        )
        assertTrue(scene.cues.isEmpty())
        assertEquals(PerceptionMode.UNAVAILABLE, scene.mode)
        assertEquals(GuidanceAction.UNKNOWN, engine.decide(scene).action)
    }

    @Test
    fun agedCameraFrameIsStaleWhenTheClockMovesOn() {
        val frame = DetectionFrame(
            objects = listOf(DetectedObject("chair", 0.92f, 0f, 0f, 160f, 160f)),
            imageWidth = 300,
            imageHeight = 200,
            mirrorHorizontally = false,
            inferenceLatencyMs = 30.0,
            fromRealDetector = true,
            completedAtElapsedMs = 1_000L,
        )
        val scene = SceneObservations.fromCamera(
            readiness = DetectorReadiness.READY,
            frame = frame,
            nowElapsedMs = 1_000L + NavigationConfig.STALE_FRAME_MS,
        )
        assertEquals(NavigationConfig.STALE_FRAME_MS, scene.frameAgeMs)
        assertEquals(GuidanceAction.UNKNOWN, engine.decide(scene).action)
    }

    @Test
    fun syntheticRobotFramePutsForwardOnTheHeadingAxis() {
        val ahead = SyntheticObstacleWorld.toRobotFrame(
            pose = PlanarPose(0.0, 0.0, PI / 2.0),
            obstacle = SyntheticObstacleWorld.MapObstacle("post", xMeters = 0.0, yMeters = 1.0),
        )
        assertEquals(1.0, ahead.forwardMeters, 1e-9)
        assertEquals(0.0, ahead.lateralMeters, 1e-9)

        val onTheRight = SyntheticObstacleWorld.toRobotFrame(
            pose = PlanarPose(0.0, 0.0, PI / 2.0),
            obstacle = SyntheticObstacleWorld.MapObstacle("post", xMeters = 1.0, yMeters = 0.0),
        )
        assertEquals(0.0, onTheRight.forwardMeters, 1e-9)
        assertTrue(onTheRight.lateralMeters < 0.0)
    }

    @Test
    fun illustrativeStraightPathStaysOnTheForwardAxis() {
        val poses = IllustrativePath.preview(SteeringCommand(0.4, 0.0))
        assertTrue(poses.last().xMeters > poses.first().xMeters)
        assertEquals(0.0, poses.last().yMeters, 1e-9)
    }

    @Test
    fun illustrativeLeftSteerGainsPositiveLateral() {
        val poses = IllustrativePath.preview(SteeringCommand(0.4, NavigationConfig.STEER_FULL_RADIANS))
        assertTrue(poses.last().yMeters > 0.0)
        assertTrue(poses.last().headingRadians > 0.0)
    }

    private fun strongCue(label: String, center: Float): DirectionalCue {
        return DirectionalCue(label, 0.95f, center, 0.25f)
    }

    private fun camera(
        cues: List<DirectionalCue> = emptyList(),
        age: Long? = 100L,
        real: Boolean = true,
        depth: Boolean = false,
        contradictory: Boolean = false,
        mode: PerceptionMode = PerceptionMode.CAMERA_2D,
    ): SceneObservation {
        return SceneObservation(
            cues = cues,
            mode = mode,
            realDetector = real,
            metricDepthAvailable = depth,
            frameAgeMs = age,
            contradictory = contradictory,
            summary = "test",
        )
    }

    private fun synthetic(vararg obstacles: KnownObstacle): SceneObservation {
        return SceneObservation(
            cues = emptyList(),
            mode = PerceptionMode.SYNTHETIC_WORLD,
            realDetector = false,
            metricDepthAvailable = true,
            frameAgeMs = 0L,
            knownObstacles = obstacles.toList(),
            catalogComplete = true,
            summary = "test map",
        )
    }

    private fun assertNoMetricClaim(decision: NavigationDecision) {
        assertFalse(decision.metricDepthUsed)
        assertFalse(decision.reason.contains("meter"))
        assertFalse(decision.reason.contains("Synthetic"))
        assertTrue(decision.confidence in 0f..NavigationConfig.MAX_CAMERA_CONFIDENCE)
    }
}
