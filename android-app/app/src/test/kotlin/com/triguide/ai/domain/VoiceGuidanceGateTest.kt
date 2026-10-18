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
    fun sameCriticalCueSpeaksAgainOnlyAfterItsCooldown() {
        val cue = VoiceCues.obstacle()
        assertTrue(gate.consider(input(cue, now = 0L)) is SpeechDirective.Speak)
        gate.onUtteranceFinished(400L)
        assertTrue(gate.consider(input(cue, now = VoiceConfig.COOLDOWN_CRITICAL_MS - 1)) is SpeechDirective.Silent)
        val again = gate.consider(input(cue, now = VoiceConfig.COOLDOWN_CRITICAL_MS))
        assertTrue(again is SpeechDirective.Speak)
        assertFalse((again as SpeechDirective.Speak).interrupt)
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
        val left = engine.decide(camera(cues = listOf(DirectionalCue("chair", 0.9f, 0.15f, 0.20f))))
        assertEquals(VoiceAlertKind.TURN_SUGGESTION, VoiceCues.fromDecision(left)?.kind)
        assertEquals("turn-right", VoiceCues.fromDecision(left)?.dedupeKey)

        val center = engine.decide(camera(cues = listOf(DirectionalCue("person", 0.9f, 0.50f, 0.20f))))
        assertEquals(VoiceAlertKind.OBSTACLE_DETECTED, VoiceCues.fromDecision(center)?.kind)

        val empty = engine.decide(camera())
        assertEquals(VoiceAlertKind.NAVIGATION_UNCERTAIN, VoiceCues.fromDecision(empty)?.kind)

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
        assertEquals("turn-straight", VoiceCues.fromDecision(clear)?.dedupeKey)
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
        assertEquals("turn-straight", VoiceCues.fromDecision(committed)?.dedupeKey)
    }

    @Test
    fun sampleMentionsResearchAndIsNotASafetyProof() {
        val english = VoiceCues.sample(ResolvedVoiceLanguage.ENGLISH)
        assertTrue(english.contains("Research and simulation"))
        assertTrue(english.contains("not a safety proof"))
        assertTrue(VoiceCues.sample(ResolvedVoiceLanguage.ARABIC).isNotBlank())
    }

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
