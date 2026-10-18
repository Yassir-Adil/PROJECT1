package com.triguide.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceSpeechTest {
    @Test
    fun openSpeaksExactlyOneWhatsAheadLine() {
        val tracker = SessionSpeechTracker()
        val first = tracker.guidanceStartLines()
        assertEquals(listOf("whats-ahead"), first.map { it.id })
        assertEquals("What's ahead?", first.single().english)
        assertEquals("قدامك في شنو؟", first.single().arabic)
        assertFalse(first.any { it.id == "disclaimer" })
        assertFalse(first.any { it.id == "gesture-hint" })
        // Still available for on-screen copy, not for the open speaker path.
        assertTrue(GuidanceSpeech.disclaimer.english.startsWith("Research and Simulation Mode."))
        assertTrue(GuidanceSpeech.GESTURE_ON_SCREEN.contains("Triple-tap"))
    }

    @Test
    fun introHoldIsShortAfterTheOpenQuestion() {
        val tracker = SessionSpeechTracker()
        val first = tracker.guidanceStartLines()
        tracker.suppressAfter(first, nowElapsedMs = 1_000L)
        assertEquals(1_000L + GuidanceSpeech.FIRST_INTRO_SUPPRESS_MS, tracker.suppressNavigationUntilMs)

        val restart = tracker.guidanceStartLines()
        tracker.suppressAfter(restart, nowElapsedMs = 5_000L)
        assertEquals(5_000L + GuidanceSpeech.FIRST_INTRO_SUPPRESS_MS, tracker.suppressNavigationUntilMs)
    }

    @Test
    fun aCommittedTurnAndObstacleStillPassDuringTheIntroduction() {
        val turn = VoiceCues.turn(GuidanceAction.LEFT)
        val obstacle = VoiceCues.obstacle()
        assertEquals(
            turn,
            navigationCueForSpeech(
                cue = turn,
                nowElapsedMs = 1_000L,
                guidanceStartedAtMs = 0L,
                suppressNavigationUntilMs = 16_000L,
            ),
        )
        assertNull(
            navigationCueForSpeech(
                cue = VoiceCues.uncertain(),
                nowElapsedMs = 1_000L,
                guidanceStartedAtMs = 0L,
                suppressNavigationUntilMs = 16_000L,
            ),
        )
        assertEquals(
            obstacle,
            navigationCueForSpeech(
                cue = obstacle,
                nowElapsedMs = 1_000L,
                guidanceStartedAtMs = 0L,
                suppressNavigationUntilMs = 16_000L,
            ),
        )
    }

    @Test
    fun cameraUnavailableNeverRidesTheNavigationTick() {
        assertNull(
            navigationCueForSpeech(
                cue = VoiceCues.cameraUnavailable(),
                nowElapsedMs = VoiceConfig.CAMERA_START_GRACE_MS - 1,
                guidanceStartedAtMs = 0L,
                suppressNavigationUntilMs = 0L,
            ),
        )
        assertNull(
            navigationCueForSpeech(
                cue = VoiceCues.cameraUnavailable(),
                nowElapsedMs = VoiceConfig.CAMERA_START_GRACE_MS + 5_000L,
                guidanceStartedAtMs = 0L,
                suppressNavigationUntilMs = 0L,
            ),
        )
    }

    @Test
    fun cameraSpeechGateIsSilentWhileStartingAndOnlyFiresOnceOnFailure() {
        val gate = CameraSpeechGate()
        assertNull(gate.cueForTransition(CameraLifecyclePhase.STARTING))
        assertNull(gate.cueForTransition(CameraLifecyclePhase.STARTING))
        assertNull(gate.cueForTransition(CameraLifecyclePhase.STREAMING))
        val failed = gate.cueForTransition(CameraLifecyclePhase.FAILED)
        assertEquals(VoiceAlertKind.CAMERA_UNAVAILABLE, failed?.kind)
        assertNull(gate.cueForTransition(CameraLifecyclePhase.FAILED))
        assertNull(gate.cueForTransition(CameraLifecyclePhase.STREAMING))
        assertNull(gate.cueForTransition(CameraLifecyclePhase.STARTING))
    }
}
