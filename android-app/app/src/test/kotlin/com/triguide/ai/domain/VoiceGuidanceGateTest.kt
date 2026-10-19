package com.triguide.ai.domain

import com.triguide.ai.sim.PlanarPose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceGuidanceGateTest {
    private val gate = VoiceGuidanceGate()
    private val engine = NavigationDecisionEngine()

    @Test
    fun repeatedFramesSpeakOnceInsideTheCooldown() {
        val cue = VoiceCues.turn(GuidanceAction.LEFT)
        val first = gate.consider(input(cue, now = 0L))
        assertTrue(first is SpeechDirective.Speak)
        repeat(40) { index ->
            val again = gate.consider(input(cue, now = 50L * (index + 1)))
            assertTrue(again is SpeechDirective.Silent)
        }
    }

    @Test
    fun stableBlockedHazardRemindsOnlyAfterThirtySeconds() {
        val cue = VoiceCues.obstacle()
        assertTrue(gate.consider(input(cue, now = 0L)) is SpeechDirective.Speak)
        gate.onUtteranceFinished(400L)
        // Former 5 s critical cooldown must stay silent for a persistent episode.
        assertTrue(gate.consider(input(cue, now = 5_000L)) is SpeechDirective.Silent)
        assertTrue(
            gate.consider(input(cue, now = VoiceConfig.HAZARD_REMINDER_MS - 1)) is SpeechDirective.Silent,
        )
        val reminder = gate.consider(input(cue, now = VoiceConfig.HAZARD_REMINDER_MS))
        assertTrue(reminder is SpeechDirective.Speak)
        assertFalse((reminder as SpeechDirective.Speak).interrupt)
    }

    @Test
    fun stableBlockedSixtySecondsYieldsOneReminder() {
        val cue = aheadCue()
        assertTrue(gate.consider(input(cue, now = 0L)) is SpeechDirective.Speak)
        gate.onUtteranceFinished(400L)
        var speaks = 0
        var t = 50L
        // Exclusive of the exact 60 s boundary (second reminder cadence).
        while (t < 60_000L) {
            if (gate.consider(input(cue, now = t)) is SpeechDirective.Speak) {
                speaks++
                gate.onUtteranceFinished(t + 400L)
            }
            t += 50L
        }
        assertEquals(1, speaks)
    }

    @Test
    fun alternatingCameraAheadAndBlockedKeysStayOneEpisodeForSixtySeconds() {
        // Legacy distinct keys still flutter in logs; gate must collapse them into one episode.
        val ahead = aheadCue()
        val blocked = blockedCue()
        assertEquals(VoiceCues.HAZARD_KEY_AHEAD, ahead.dedupeKey)
        assertEquals(VoiceCues.HAZARD_KEY_BLOCKED, blocked.dedupeKey)
        assertTrue(ahead.dedupeKey != blocked.dedupeKey)

        assertTrue(gate.consider(input(ahead, now = 0L)) is SpeechDirective.Speak)
        gate.onUtteranceFinished(400L)

        var speaks = 0
        var t = 50L
        var useAhead = false
        while (t < 60_000L) {
            val cue = if (useAhead) ahead else blocked
            useAhead = !useAhead
            if (gate.consider(input(cue, now = t)) is SpeechDirective.Speak) {
                speaks++
                gate.onUtteranceFinished(t + 400L)
            }
            t += 50L
        }
        // One 30 s reminder only — key flutter must not add speaks.
        assertEquals(1, speaks)
    }

    @Test
    fun cameraHoldDecisionsAlternatingSidesDoNotExtraSpeak() {
        val centerHold = VoiceCues.fromDecision(
            engine.decide(camera(cues = listOf(DirectionalCue("chair", 0.90f, 0.50f, 0.55f)))),
        )
        val bothHold = VoiceCues.fromDecision(
            engine.decide(
                camera(
                    cues = listOf(
                        DirectionalCue("chair", 0.90f, 0.30f, 0.55f),
                        DirectionalCue("table", 0.90f, 0.70f, 0.55f),
                    ),
                ),
            ),
        )
        assertEquals(VoiceAlertKind.OBSTACLE_DETECTED, centerHold?.kind)
        assertEquals(VoiceAlertKind.OBSTACLE_DETECTED, bothHold?.kind)
        assertEquals(VoiceCues.HAZARD_KEY_CAMERA_BLOCKED, centerHold?.dedupeKey)
        assertEquals(VoiceCues.HAZARD_KEY_CAMERA_BLOCKED, bothHold?.dedupeKey)

        assertTrue(gate.consider(input(centerHold, now = 0L)) is SpeechDirective.Speak)
        gate.onUtteranceFinished(400L)
        var speaks = 0
        var t = 50L
        var useCenter = false
        while (t < 60_000L) {
            val cue = if (useCenter) centerHold else bothHold
            useCenter = !useCenter
            if (gate.consider(input(cue, now = t)) is SpeechDirective.Speak) {
                speaks++
                gate.onUtteranceFinished(t + 400L)
            }
            t += 50L
        }
        assertEquals(1, speaks)
    }

    @Test
    fun recoveryThenReEntryAnnouncesAgainImmediately() {
        val cue = aheadCue()
        assertTrue(gate.consider(input(cue, now = 0L)) is SpeechDirective.Speak)
        gate.onUtteranceFinished(300L)
        // Clear the episode with non-hazard ticks.
        assertTrue(gate.consider(input(cue = null, now = 500L)) is SpeechDirective.Silent)
        assertTrue(
            gate.consider(
                input(cue = null, now = 500L + VoiceConfig.HAZARD_CLEAR_MS),
            ) is SpeechDirective.Silent,
        )
        val reentered = gate.consider(
            input(cue, now = 500L + VoiceConfig.HAZARD_CLEAR_MS + 50L),
        )
        assertTrue(reentered is SpeechDirective.Speak)
        assertEquals("Obstacle ahead.", (reentered as SpeechDirective.Speak).text)
    }

    @Test
    fun startupWithImmediateObstacleSpeaksOnceThenStaysQuiet() {
        val cue = aheadCue()
        val first = gate.consider(input(cue, now = 0L))
        assertTrue(first is SpeechDirective.Speak)
        gate.onUtteranceFinished(400L)
        // Matches the noisy Redmi opening window (~16 s) that previously spoke three times.
        for (t in 1_000L..16_000L step 50L) {
            assertTrue(
                "unexpected speak at $t",
                gate.consider(input(cue, now = t)) is SpeechDirective.Silent,
            )
        }
    }

    @Test
    fun obstacleInterruptsAnActiveTurn() {
        val turn = gate.consider(input(VoiceCues.turn(GuidanceAction.LEFT), now = 0L))
        assertTrue(turn is SpeechDirective.Speak)
        assertFalse((turn as SpeechDirective.Speak).interrupt)

        val obstacle = gate.consider(input(VoiceCues.obstacle(), now = 200L))
        assertTrue(obstacle is SpeechDirective.Speak)
        assertTrue((obstacle as SpeechDirective.Speak).interrupt)
        assertEquals(VoiceAlertKind.OBSTACLE_DETECTED, obstacle.kind)
        assertEquals("Obstacle detected. Hold.", obstacle.text)
    }

    @Test
    fun criticalObstacleInterruptsEveryLowerPriorityObservationCue() {
        val lowerPriorityCues = listOf(
            VoiceCues.turn(GuidanceAction.LEFT),
            VoiceCues.uncertain(),
            VoiceCues.paused(),
            VoiceCues.sceneBrief("Person observed.", "تم رصد شخص."),
        )

        lowerPriorityCues.forEachIndexed { index, cue ->
            val isolatedGate = VoiceGuidanceGate()
            val first = isolatedGate.consider(input(cue, now = 0L))
            assertTrue("lower-priority cue did not speak at index $index", first is SpeechDirective.Speak)

            val urgent = isolatedGate.consider(input(VoiceCues.obstacle(), now = 200L))
            assertTrue("obstacle did not speak at index $index", urgent is SpeechDirective.Speak)
            assertTrue("obstacle did not interrupt at index $index", (urgent as SpeechDirective.Speak).interrupt)
            assertEquals(VoiceAlertKind.OBSTACLE_DETECTED, urgent.kind)
        }
    }

    @Test
    fun cameraInterruptsADifferentCriticalCue() {
        assertTrue(gate.consider(input(VoiceCues.obstacle(), now = 0L)) is SpeechDirective.Speak)
        val camera = gate.consider(input(VoiceCues.cameraUnavailable(), now = 200L))
        assertTrue(camera is SpeechDirective.Speak)
        assertTrue((camera as SpeechDirective.Speak).interrupt)
        assertEquals(VoiceAlertKind.CAMERA_UNAVAILABLE, camera.kind)
    }

    @Test
    fun lowerCueWaitsWhileAHigherCueIsSpeaking() {
        assertTrue(gate.consider(input(VoiceCues.obstacle(), now = 0L)) is SpeechDirective.Speak)
        val turn = gate.consider(input(VoiceCues.turn(GuidanceAction.RIGHT), now = 200L))
        assertTrue(turn is SpeechDirective.Silent)
    }

    @Test
    fun differentTurnWaitsForTheMinimumGapAfterTheUtteranceEnds() {
        assertTrue(gate.consider(input(VoiceCues.turn(GuidanceAction.LEFT), now = 0L)) is SpeechDirective.Speak)
        gate.onUtteranceFinished(400L)
        assertTrue(
            gate.consider(input(VoiceCues.turn(GuidanceAction.RIGHT), now = 500L)) is SpeechDirective.Silent,
        )
        val later = gate.consider(input(VoiceCues.turn(GuidanceAction.RIGHT), now = VoiceConfig.MIN_GAP_MS))
        assertTrue(later is SpeechDirective.Speak)
        assertEquals("Turn right.", (later as SpeechDirective.Speak).text)
    }

    @Test
    fun criticalCueDoesNotWaitForTheMinimumGap() {
        assertTrue(gate.consider(input(VoiceCues.turn(GuidanceAction.LEFT), now = 0L)) is SpeechDirective.Speak)
        gate.onUtteranceFinished(300L)
        val obstacle = gate.consider(input(VoiceCues.obstacle(), now = 400L))
        assertTrue(obstacle is SpeechDirective.Speak)
    }

    @Test
    fun quietModeKeepsOnlyCriticalCues() {
        val quiet = VoiceSettings(speechEnabled = true, quietMode = true)
        assertTrue(gate.consider(input(VoiceCues.turn(GuidanceAction.LEFT), now = 0L, settings = quiet)) is SpeechDirective.Silent)
        assertTrue(gate.consider(input(VoiceCues.uncertain(), now = 0L, settings = quiet)) is SpeechDirective.Silent)
        assertTrue(gate.consider(input(VoiceCues.paused(), now = 0L, settings = quiet)) is SpeechDirective.Silent)
        assertTrue(gate.consider(input(VoiceCues.obstacle(), now = 0L, settings = quiet)) is SpeechDirective.Speak)
        val fresh = VoiceGuidanceGate()
        assertTrue(
            fresh.consider(input(VoiceCues.cameraUnavailable(), now = 0L, settings = quiet)) is SpeechDirective.Speak,
        )
    }

    @Test
    fun speechOffSuppressesEveryCue() {
        val off = VoiceSettings(speechEnabled = false)
        assertTrue(gate.consider(input(VoiceCues.obstacle(), now = 0L, settings = off)) is SpeechDirective.Silent)
        assertTrue(gate.consider(input(VoiceCues.cameraUnavailable(), now = 0L, settings = off)) is SpeechDirective.Silent)
    }

    @Test
    fun nullCueDoesNotSpeak() {
        assertTrue(gate.consider(input(cue = null, now = 0L)) is SpeechDirective.Silent)
    }

    @Test
    fun speechRateIsClampedOntoTheDirective() {
        val fast = gate.consider(input(VoiceCues.obstacle(), now = 0L, settings = VoiceSettings(speechRate = 3f)))
        assertEquals(VoiceConfig.RATE_MAX, (fast as SpeechDirective.Speak).speechRate)
        val slowGate = VoiceGuidanceGate()
        val slow = slowGate.consider(input(VoiceCues.obstacle(), now = 0L, settings = VoiceSettings(speechRate = 0.1f)))
        assertEquals(VoiceConfig.RATE_MIN, (slow as SpeechDirective.Speak).speechRate)
    }

    @Test
    fun arabicResolutionSelectsTheArabicPhrase() {
        val spoken = gate.consider(
            input(VoiceCues.obstacle(), now = 0L, language = ResolvedVoiceLanguage.ARABIC),
        ) as SpeechDirective.Speak
        assertEquals("عقبة مكتشفة. توقف.", spoken.text)
        assertEquals(ResolvedVoiceLanguage.ARABIC, spoken.language)
    }

    @Test
    fun pausedSpeaksOnceAndCanQueueBehindAnActiveTurn() {
        assertTrue(gate.consider(input(VoiceCues.turn(GuidanceAction.STRAIGHT), now = 0L)) is SpeechDirective.Speak)
        val paused = gate.consider(input(VoiceCues.paused(), now = 100L))
        assertTrue(paused is SpeechDirective.Speak)
        assertFalse((paused as SpeechDirective.Speak).interrupt)
        assertEquals("Simulation paused.", paused.text)
        assertTrue(gate.consider(input(VoiceCues.paused(), now = 150L)) is SpeechDirective.Silent)
    }

    @Test
    fun rejectedUtteranceDoesNotRetryOnTheNextFrame() {
        assertTrue(gate.consider(input(VoiceCues.obstacle(), now = 0L)) is SpeechDirective.Speak)
        gate.abandonLastUtterance(0L)
        assertTrue(gate.consider(input(VoiceCues.obstacle(), now = 50L)) is SpeechDirective.Silent)
        val retried = gate.consider(input(VoiceCues.obstacle(), now = VoiceConfig.UNAVAILABLE_RETRY_MS))
        assertTrue(retried is SpeechDirective.Speak)
    }

    @Test
    fun decisionsMapToStableCues() {
        val left = engine.decide(camera(cues = listOf(DirectionalCue("chair", 0.9f, 0.30f, 0.20f))))
        val leftCue = VoiceCues.fromDecision(left)
        assertEquals(VoiceAlertKind.OBSTACLE_DETECTED, leftCue?.kind)
        assertEquals(VoiceCues.HAZARD_KEY_CAMERA_BLOCKED, leftCue?.dedupeKey)
        assertEquals("Obstacle ahead.", leftCue?.english)

        val center = engine.decide(camera(cues = listOf(DirectionalCue("person", 0.9f, 0.50f, 0.20f))))
        assertEquals(VoiceAlertKind.OBSTACLE_DETECTED, VoiceCues.fromDecision(center)?.kind)
        assertEquals(VoiceCues.HAZARD_KEY_CAMERA_BLOCKED, VoiceCues.fromDecision(center)?.dedupeKey)
        assertEquals("Obstacle ahead.", VoiceCues.fromDecision(center)?.english)

        val both = engine.decide(
            camera(
                cues = listOf(
                    DirectionalCue("chair", 0.9f, 0.30f, 0.55f),
                    DirectionalCue("table", 0.9f, 0.70f, 0.55f),
                ),
            ),
        )
        val bothCue = VoiceCues.fromDecision(both)
        assertEquals(VoiceCues.HAZARD_KEY_CAMERA_BLOCKED, bothCue?.dedupeKey)
        assertEquals("Path blocked.", bothCue?.english)

        val empty = engine.decide(camera())
        assertEquals(PathRiskLevel.UNKNOWN, empty.pathRisk)
        assertEquals(GuidanceAction.UNKNOWN, empty.action)
        assertEquals(0.0, empty.linearSpeedMps, 0.0)
        assertNull(VoiceCues.fromDecision(empty))

        val stale = engine.decide(camera(frameAgeMs = NavigationConfig.STALE_FRAME_MS))
        assertEquals(VoiceAlertKind.CAMERA_UNAVAILABLE, VoiceCues.fromDecision(stale)?.kind)

        val missing = engine.decide(
            SceneObservation(
                cues = emptyList(),
                mode = PerceptionMode.UNAVAILABLE,
                realDetector = false,
                metricDepthAvailable = false,
                frameAgeMs = null,
            ),
        )
        assertEquals(VoiceAlertKind.CAMERA_UNAVAILABLE, VoiceCues.fromDecision(missing)?.kind)

        assertNull(VoiceCues.fromDecision(NavigationDecisions.manual()))

        val clear = engine.decide(
            SceneObservation(
                cues = emptyList(),
                mode = PerceptionMode.SYNTHETIC_WORLD,
                realDetector = false,
                metricDepthAvailable = true,
                frameAgeMs = null,
                catalogComplete = true,
            ),
        )
        assertEquals(PathRiskLevel.CLEAR_OBSERVED, clear.pathRisk)
        assertNull(VoiceCues.fromDecision(clear))
    }

    @Test
    fun regressedCameraMovementDecisionNeverBecomesSpokenMovement() {
        listOf(
            GuidanceAction.LEFT,
            GuidanceAction.SLIGHT_LEFT,
            GuidanceAction.RIGHT,
            GuidanceAction.SLIGHT_RIGHT,
            GuidanceAction.STRAIGHT,
        ).forEach { action ->
            val regressed = NavigationDecisions.fromAction(
                action = action,
                reason = "Synthetic regression input: detector box only.",
                confidence = 0.60f,
                basedOnRealVision = true,
                metricDepthUsed = false,
                perceptionLabel = NavigationConfig.CAMERA_LABEL,
                subjectLabel = "chair",
                subjectSide = "left",
                pathRisk = PathRiskLevel.BLOCKED,
            )
            val cue = VoiceCues.fromDecision(regressed)
            assertEquals(VoiceAlertKind.OBSTACLE_DETECTED, cue?.kind)
            assertEquals(VoiceCues.HAZARD_KEY_CAMERA_BLOCKED, cue?.dedupeKey)
            assertFalse(cue!!.english.lowercase().contains("turn"))
            assertFalse(cue.english.lowercase().contains("walk"))
            assertFalse(cue.arabic.contains("انعطف"))
        }

        val falseClear = NavigationDecisions.fromAction(
            action = GuidanceAction.RIGHT,
            reason = "Camera boxes only.",
            confidence = 0.50f,
            basedOnRealVision = true,
            metricDepthUsed = false,
            perceptionLabel = NavigationConfig.CAMERA_LABEL,
            pathRisk = PathRiskLevel.CLEAR_OBSERVED,
        )
        assertNull(VoiceCues.fromDecision(falseClear))
    }

    @Test
    fun aCommittedTurnQueuedBehindHoldIsSpokenOnceTheGapAllowsIt() {
        val hold = VoiceCues.fromDecision(
            engine.decide(camera(cues = listOf(DirectionalCue("sink", 0.90f, 0.50f, 0.20f)))),
        )
        assertEquals("Obstacle ahead.", hold?.english)
        assertTrue(gate.consider(input(hold, now = 0L)) is SpeechDirective.Speak)

        // Camera boxes alone must not suggest a turn; exercise the speech gate with an already-committed cue.
        val turn = VoiceCues.turn(GuidanceAction.SLIGHT_LEFT)
        assertTrue(gate.consider(input(turn, now = 200L)) is SpeechDirective.Silent)
        gate.onUtteranceFinished(400L)
        assertTrue(gate.consider(input(hold, now = 500L)) is SpeechDirective.Silent)

        val spoken = gate.consider(
            input(turn, now = VoiceConfig.MIN_GAP_MS, language = ResolvedVoiceLanguage.ARABIC),
        )
        assertTrue(spoken is SpeechDirective.Speak)
        val text = (spoken as SpeechDirective.Speak).text
        assertEquals("انعطف يساراً قليلاً.", text)
        assertEquals(ResolvedVoiceLanguage.ARABIC, spoken.language)
    }

    @Test
    fun movingBlockedBoxDoesNotBypassObstacleSpeechCooldown() {
        val center = VoiceCues.fromDecision(
            engine.decide(camera(cues = listOf(DirectionalCue("sink", 0.90f, 0.50f, 0.20f)))),
        )
        val side = VoiceCues.fromDecision(
            engine.decide(camera(cues = listOf(DirectionalCue("sink", 0.90f, 0.70f, 0.20f)))),
        )

        assertEquals(VoiceCues.HAZARD_KEY_CAMERA_BLOCKED, center?.dedupeKey)
        assertEquals(center?.dedupeKey, side?.dedupeKey)
        assertTrue(gate.consider(input(center, now = 0L)) is SpeechDirective.Speak)
        assertTrue(gate.consider(input(side, now = 200L)) is SpeechDirective.Silent)
    }

    @Test
    fun smootherWarmupIsSilentUntilTheDirectionCommits() {
        val session = GuidanceSession()
        val scene = SyntheticObstacleWorld.observe(PlanarPose(0.0, 3.0, 0.0))
        val first = session.onSynthetic(scene)
        val second = session.onSynthetic(scene)
        assertEquals(GuidanceAction.HOLD, first.action)
        assertEquals(GuidanceAction.HOLD, second.action)
        assertNull(VoiceCues.fromDecision(first))
        assertNull(VoiceCues.fromDecision(second))
        val committed = session.onSynthetic(scene)
        assertEquals(GuidanceAction.STRAIGHT, committed.action)
        assertEquals(PathRiskLevel.CLEAR_OBSERVED, committed.pathRisk)
        // Clear corridor stays silent by default.
        assertNull(VoiceCues.fromDecision(committed))
    }

    @Test
    fun sampleMentionsResearchAndIsNotASafetyProof() {
        val english = VoiceCues.sample(ResolvedVoiceLanguage.ENGLISH)
        assertTrue(english.contains("Research and simulation"))
        assertTrue(english.contains("not a safety proof"))
        assertTrue(VoiceCues.sample(ResolvedVoiceLanguage.ARABIC).isNotBlank())
    }

    @Test
    fun unknownPerceptionSourceCannotProduceTurnSpeech() {
        val unknownSource = NavigationDecisions.fromAction(
            action = GuidanceAction.RIGHT,
            reason = "Unverified source supplied a turn.",
            confidence = 0.8f,
            basedOnRealVision = false,
            metricDepthUsed = false,
            perceptionLabel = NavigationConfig.NONE_LABEL,
            pathRisk = PathRiskLevel.BLOCKED,
        )
        val cue = VoiceCues.fromDecision(unknownSource)
        assertEquals(VoiceAlertKind.OBSTACLE_DETECTED, cue?.kind)
        assertFalse(cue!!.english.lowercase().contains("turn"))
        assertFalse(cue.english.lowercase().contains("walk"))
    }

    private fun aheadCue(): VoiceCue = VoiceCue(
        kind = VoiceAlertKind.OBSTACLE_DETECTED,
        dedupeKey = VoiceCues.HAZARD_KEY_AHEAD,
        english = "Obstacle ahead.",
        arabic = "في عائق قدامك.",
    )

    private fun blockedCue(): VoiceCue = VoiceCue(
        kind = VoiceAlertKind.OBSTACLE_DETECTED,
        dedupeKey = VoiceCues.HAZARD_KEY_BLOCKED,
        english = "Path blocked.",
        arabic = "الطريق قدامك مسدود.",
    )

    private fun input(
        cue: VoiceCue?,
        now: Long,
        settings: VoiceSettings = VoiceSettings(),
        language: ResolvedVoiceLanguage = ResolvedVoiceLanguage.ENGLISH,
    ): VoiceGateInput {
        return VoiceGateInput(
            cue = cue,
            settings = settings,
            nowElapsedMs = now,
            resolvedLanguage = language,
        )
    }

    private fun camera(
        cues: List<DirectionalCue> = emptyList(),
        frameAgeMs: Long? = 100L,
    ): SceneObservation {
        return SceneObservation(
            cues = cues,
            mode = PerceptionMode.CAMERA_2D,
            realDetector = true,
            metricDepthAvailable = false,
            frameAgeMs = frameAgeMs,
        )
    }
}
