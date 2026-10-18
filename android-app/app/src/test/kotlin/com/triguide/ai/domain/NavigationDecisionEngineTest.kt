package com.triguide.ai.domain

import com.triguide.ai.robot.SimulatedTricycleController
import com.triguide.ai.sim.PlanarPose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class NavigationDecisionEngineTest {
    private val engine = NavigationDecisionEngine()

    @Test
    fun obstacleOnTheLeftSteersSlightlyRight() {
        val decision = engine.decide(camera(cues = listOf(strongCue("chair", center = 0.15f))))
        assertEquals(GuidanceAction.SLIGHT_RIGHT, decision.action)
        assertTrue(decision.steeringRadians < 0.0)
        assertEquals(NavigationConfig.STEER_SLIGHT_RADIANS, -decision.steeringRadians, 1e-9)
        assertEquals(NavigationConfig.SPEED_SLIGHT_MPS, decision.linearSpeedMps, 1e-9)
        assertEquals("chair", decision.subjectLabel)
        assertEquals("left", decision.subjectSide)
        assertTrue(decision.basedOnRealVision)
        assertFalse(decision.metricDepthUsed)
        assertTrue(decision.reason.contains("left of the image"))
        assertTrue(decision.reason.contains("Steering slightly right"))
        assertTrue(decision.reason.contains("Depth is not measured"))
        assertTrue(decision.reason.contains("not a distance"))
        assertNoMetricClaim(decision)
    }

    @Test
    fun obstacleOnTheRightSteersSlightlyLeft() {
        val decision = engine.decide(camera(cues = listOf(strongCue("person", center = 0.86f))))
        assertEquals(GuidanceAction.SLIGHT_LEFT, decision.action)
        assertTrue(decision.steeringRadians > 0.0)
        assertTrue(decision.reason.contains("right of the image"))
        assertTrue(decision.reason.contains("Steering slightly left"))
        assertEquals("person", decision.subjectLabel)
        assertEquals("right", decision.subjectSide)
        assertNoMetricClaim(decision)
    }

    @Test
    fun aLargeBoxFarFromTheMiddleIsStillASlightTurn() {
        val decision = engine.decide(
            camera(cues = listOf(DirectionalCue("person", 0.90f, 0.12f, 0.45f))),
        )
        assertEquals(GuidanceAction.SLIGHT_RIGHT, decision.action)
        assertTrue(decision.reason.contains("not a distance"))
        assertNoMetricClaim(decision)
    }

    @Test
    fun aBoxInsideTheOldMiddleThirdButLeftOfTheBlockingBandSteersRight() {
        val decision = engine.decide(
            camera(cues = listOf(DirectionalCue("person", 0.80f, 0.38f, 0.08f))),
        )
        assertEquals(GuidanceAction.RIGHT, decision.action)
        assertEquals("left", decision.subjectSide)
        assertTrue(decision.reason.contains("Steering right"))
        assertFalse(decision.reason.contains("slightly"))
        assertNoMetricClaim(decision)
    }

    @Test
    fun smallEdgeBoxIsASlightTurn() {
        val decision = engine.decide(
            camera(cues = listOf(DirectionalCue("cup", 0.80f, 0.10f, 0.04f))),
        )
        assertEquals(GuidanceAction.SLIGHT_RIGHT, decision.action)
        assertEquals(-NavigationConfig.STEER_SLIGHT_RADIANS, decision.steeringRadians, 1e-9)
        assertNoMetricClaim(decision)
    }

    @Test
    fun boxNearTheCenterBandIsAFullTurn() {
        val decision = engine.decide(
            camera(cues = listOf(DirectionalCue("cup", 0.80f, 0.36f, 0.04f))),
        )
        assertEquals(GuidanceAction.RIGHT, decision.action)
        assertFalse(decision.reason.contains("slightly"))
    }

    @Test
    fun centerObstacleHoldsWithoutChoosingASide() {
        val decision = engine.decide(camera(cues = listOf(strongCue("person", center = 0.50f))))
        assertEquals(GuidanceAction.HOLD, decision.action)
        assertEquals(0.0, decision.linearSpeedMps, 0.0)
        assertEquals(0.0, decision.steeringRadians, 0.0)
        assertEquals("person", decision.subjectLabel)
        assertEquals("center", decision.subjectSide)
        assertTrue(decision.reason.contains("center of the image"))
        assertTrue(decision.reason.contains("No confirmed passage"))
        assertNoMetricClaim(decision)
    }

    @Test
    fun emptyFrameIsNotAConfirmedPassage() {
        val decision = engine.decide(camera())
        assertEquals(GuidanceAction.HOLD, decision.action)
        assertEquals(0.0, decision.linearSpeedMps, 0.0)
        assertTrue(decision.reason.contains("No confirmed passage"))
        assertTrue(decision.reason.contains("Depth is not measured"))
        assertFalse(decision.metricDepthUsed)
        assertNotEquals(GuidanceAction.STRAIGHT, decision.action)
    }

    @Test
    fun bothSidesWithoutADominantBoxHold() {
        val decision = engine.decide(
            camera(
                cues = listOf(
                    strongCue("chair", center = 0.15f),
                    strongCue("table", center = 0.86f),
                ),
            ),
        )
        assertEquals(GuidanceAction.HOLD, decision.action)
        assertTrue(decision.reason.contains("both sides"))
        assertEquals("Objects on both sides. Hold.", VoiceCues.fromDecision(decision)?.english)
        assertNoMetricClaim(decision)
    }

    @Test
    fun dominantLeftWithAWeakRightBoxTurnsSlightlyRight() {
        val decision = engine.decide(
            camera(
                cues = listOf(
                    DirectionalCue("chair", 0.80f, 0.18f, 0.25f),
                    DirectionalCue("bottle", 0.60f, 0.82f, 0.20f),
                ),
            ),
        )
        assertEquals(GuidanceAction.SLIGHT_RIGHT, decision.action)
        assertEquals("chair", decision.subjectLabel)
        assertEquals("left", decision.subjectSide)
        val spoken = VoiceCues.fromDecision(decision)
        assertEquals("Chair on the left. Turn slightly right. Not a measured gap.", spoken?.english)
        assertFalse(spoken!!.english.contains("both sides"))
        assertTrue(spoken.arabic.contains("كرسي"))
        assertTrue(spoken.arabic.contains("اليسار"))
        assertNoMetricClaim(decision)
    }

    @Test
    fun strongLeftAndStrongRightHold() {
        val decision = engine.decide(
            camera(
                cues = listOf(
                    DirectionalCue("chair", 0.80f, 0.18f, 0.25f),
                    DirectionalCue("table", 0.80f, 0.84f, 0.20f),
                ),
            ),
        )
        assertEquals(GuidanceAction.HOLD, decision.action)
        assertEquals(0.0, decision.linearSpeedMps, 0.0)
        assertEquals(0.0, decision.steeringRadians, 0.0)
        assertEquals("both", decision.subjectSide)
        assertTrue(decision.reason.contains("both sides"))
        val spoken = VoiceCues.fromDecision(decision)
        assertEquals("Objects on both sides. Hold.", spoken?.english)
        assertFalse(spoken!!.english.contains("Turn"))
        assertNoMetricClaim(decision)
    }

    @Test
    fun centerBoxHolds() {
        val decision = engine.decide(camera(cues = listOf(DirectionalCue("chair", 0.90f, 0.50f, 0.22f))))
        assertEquals(GuidanceAction.HOLD, decision.action)
        assertEquals("chair", decision.subjectLabel)
        assertEquals("center", decision.subjectSide)
        assertEquals("Chair in the center. Hold.", VoiceCues.fromDecision(decision)?.english)
        assertNoMetricClaim(decision)
    }

    @Test
    fun manyWeakerRightBoxesDoNotCancelADominantLeftChair() {
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
        assertEquals(GuidanceAction.RIGHT, decision.action)
        assertEquals("chair", decision.subjectLabel)
        assertEquals("left", decision.subjectSide)
        val spoken = VoiceCues.fromDecision(decision)
        assertEquals("Chair on the left. Turn right. Not a measured gap.", spoken?.english)
        assertFalse(spoken!!.english.contains("both sides"))
        assertNoMetricClaim(decision)
    }

    @Test
    fun aClearlyLargestChairIsNamedOverASmallerHigherScoreCup() {
        val decision = engine.decide(
            camera(
                cues = listOf(
                    DirectionalCue("chair", 0.36f, 0.16f, 0.40f),
                    DirectionalCue("cup", 0.95f, 0.24f, 0.16f),
                ),
            ),
        )
        assertEquals(GuidanceAction.SLIGHT_RIGHT, decision.action)
        assertEquals("chair", decision.subjectLabel)
        assertEquals("Chair on the left. Turn slightly right. Not a measured gap.", VoiceCues.fromDecision(decision)?.english)
        assertNoMetricClaim(decision)
    }

    @Test
    fun leftThirdPersonTurnsRightEvenWhenAWeakBoxIsInTheCenter() {
        val decision = engine.decide(
            camera(
                cues = listOf(
                    DirectionalCue("person", 0.90f, 0.20f, 0.30f),
                    DirectionalCue("bottle", 0.40f, 0.50f, 0.02f),
                ),
            ),
        )
        assertEquals(GuidanceAction.SLIGHT_RIGHT, decision.action)
        assertEquals("person", decision.subjectLabel)
        assertEquals("left", decision.subjectSide)
        val spoken = VoiceCues.fromDecision(decision)
        assertEquals("Person on the left. Turn slightly right. Not a measured gap.", spoken?.english)
        assertFalse(spoken!!.english.contains("center"))
        assertTrue(spoken.arabic.contains("اليسار"))
        assertFalse(spoken.arabic.contains("الوسط"))
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
        assertEquals("Chair in the center. Hold.", spoken?.english)
        assertFalse(spoken!!.english.contains("left"))
        assertTrue(spoken.arabic.contains("الوسط"))
        assertFalse(spoken.arabic.contains("اليسار"))
        assertNoMetricClaim(decision)
    }

    @Test
    fun aMuchStrongerSideStillSteersAway() {
        val decision = engine.decide(
            camera(
                cues = listOf(
                    DirectionalCue("couch", 0.95f, 0.12f, 0.40f),
                    DirectionalCue("cup", 0.55f, 0.90f, 0.02f),
                ),
            ),
        )
        assertEquals(GuidanceAction.SLIGHT_RIGHT, decision.action)
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
        assertEquals(GuidanceAction.HOLD, below.action)
        assertEquals(0.0, below.steeringRadians, 0.0)

        val atFloor = engine.decide(
            camera(
                cues = listOf(
                    DirectionalCue("chair", DetectorConfig.SCORE_THRESHOLD, 0.36f, 0.25f),
                ),
            ),
        )
        assertEquals(GuidanceAction.RIGHT, atFloor.action)
    }

    @Test
    fun aZeroAreaCueIsNotAPassage() {
        val decision = engine.decide(
            camera(cues = listOf(DirectionalCue("speck", 0.99f, 0.10f, 0f))),
        )
        assertEquals(GuidanceAction.HOLD, decision.action)
        assertEquals(0.0, decision.steeringRadians, 0.0)
        assertTrue(decision.reason.contains("No confirmed passage"))
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
        assertEquals(GuidanceAction.RIGHT, fresh.action)

        val stale = engine.decide(
            camera(cues = listOf(strongCue("chair", 0.36f)), age = NavigationConfig.STALE_FRAME_MS),
        )
        assertEquals(GuidanceAction.HOLD, stale.action)
        assertEquals(0.0, stale.linearSpeedMps, 0.0)
        assertTrue(stale.reason.contains("stale"))
    }

    @Test
    fun missingTimestampDoesNotSteer() {
        val decision = engine.decide(camera(cues = listOf(strongCue("chair", 0.15f)), age = null))
        assertEquals(GuidanceAction.HOLD, decision.action)
        assertTrue(decision.reason.contains("timestamp"))
        assertEquals(0.0, decision.steeringRadians, 0.0)
    }

    @Test
    fun cameraModeNeverGoesStraight() {
        val scenes = listOf(
            camera(),
            camera(cues = listOf(strongCue("chair", 0.15f))),
            camera(cues = listOf(strongCue("chair", 0.86f))),
            camera(cues = listOf(strongCue("chair", 0.50f))),
            camera(cues = listOf(strongCue("a", 0.15f), strongCue("b", 0.86f))),
            camera(cues = listOf(strongCue("chair", 0.15f)), age = 5_000L),
            camera(real = false),
        )
        scenes.forEach { scene ->
            assertNotEquals(GuidanceAction.STRAIGHT, engine.decide(scene).action)
        }
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
        val leftSide = camera(cues = listOf(strongCue("chair", 0.15f)))
        val rightSide = camera(cues = listOf(strongCue("chair", 0.86f)))
        listOf(leftSide, rightSide, leftSide, rightSide, leftSide, rightSide).forEach { scene ->
            val decision = smoother.update(scene)
            assertEquals(GuidanceAction.HOLD, decision.action)
            assertEquals(0.0, decision.linearSpeedMps, 0.0)
            assertEquals(0.0, decision.steeringRadians, 0.0)
        }
    }

    @Test
    fun stableDirectionCommitsAndAReversalWaits() {
        val smoother = TemporalDecisionSmoother()
        val leftSide = camera(cues = listOf(strongCue("chair", 0.36f)))
        val rightSide = camera(cues = listOf(strongCue("chair", 0.64f)))
        repeat(NavigationConfig.CONFIRM_FRAMES - 1) {
            assertEquals(GuidanceAction.HOLD, smoother.update(leftSide).action)
        }
        assertEquals(GuidanceAction.RIGHT, smoother.update(leftSide).action)

        repeat(NavigationConfig.FLIP_FRAMES - 1) {
            assertEquals(GuidanceAction.RIGHT, smoother.update(rightSide).action)
        }
        assertEquals(GuidanceAction.LEFT, smoother.update(rightSide).action)
    }

    @Test
    fun magnitudeChangeAlsoWaits() {
        val smoother = TemporalDecisionSmoother()
        val strong = camera(cues = listOf(strongCue("chair", 0.36f)))
        val slight = camera(cues = listOf(DirectionalCue("cup", 0.80f, 0.10f, 0.04f)))
        repeat(NavigationConfig.CONFIRM_FRAMES) { smoother.update(strong) }
        assertEquals(GuidanceAction.RIGHT, smoother.update(slight).action)
        assertEquals(GuidanceAction.RIGHT, smoother.update(slight).action)
        assertEquals(GuidanceAction.SLIGHT_RIGHT, smoother.update(slight).action)
    }

    @Test
    fun staleFrameClearsACommittedTurn() {
        val smoother = TemporalDecisionSmoother()
        val fresh = camera(cues = listOf(strongCue("chair", 0.36f)))
        repeat(NavigationConfig.CONFIRM_FRAMES) { smoother.update(fresh) }
        val stale = smoother.update(fresh.copy(frameAgeMs = NavigationConfig.STALE_FRAME_MS))
        assertEquals(GuidanceAction.HOLD, stale.action)
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
    fun robotWheelFollowsAConfirmedCameraDecision() {
        val smoother = TemporalDecisionSmoother(engine)
        val robot = SimulatedTricycleController()
        val scene = camera(cues = listOf(strongCue("chair", 0.36f)))
        var decision = smoother.update(scene)
        repeat(NavigationConfig.CONFIRM_FRAMES - 1) {
            decision = smoother.update(scene)
        }
        assertEquals(GuidanceAction.RIGHT, decision.action)
        robot.setCommand(decision.toSteeringCommand())
        assertEquals(decision.steeringRadians, robot.state.value.steeringRadians, 1e-9)
        assertTrue(robot.state.value.steeringRadians < 0.0)
        repeat(15) { robot.tick(0.1) }
        assertTrue(robot.state.value.pose.headingRadians < 0.0)
        assertTrue(robot.state.value.pose.xMeters > 0.0)
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
        assertEquals(GuidanceAction.SLIGHT_LEFT, engine.decide(scene).action)
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
        assertEquals(GuidanceAction.HOLD, engine.decide(scene).action)
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
