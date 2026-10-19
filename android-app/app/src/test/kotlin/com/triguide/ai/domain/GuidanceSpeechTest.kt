package com.triguide.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceSpeechTest {
    @Test
    fun openSpeaksExactlyOneGuidanceOnStatusLine() {
        val tracker = SessionSpeechTracker()
        val first = tracker.guidanceStartLines()
        assertEquals(listOf("guidance-on"), first.map { it.id })
        assertEquals("Guidance on.", first.single().english)
        assertEquals("التوجيه يعمل.", first.single().arabic)
        assertFalse(first.any { it.id == "disclaimer" })
        assertFalse(first.any { it.id == "gesture-hint" })
        assertFalse(first.any { it.id == "whats-ahead" })
        // Rhetorical prompt remains available but is not the automatic open path.
        assertEquals("What's ahead?", GuidanceSpeech.whatsAhead.english)
        assertTrue(GuidanceSpeech.disclaimer.english.startsWith("Research and Simulation Mode."))
        assertTrue(GuidanceSpeech.GESTURE_ON_SCREEN.contains("Triple-tap"))
    }

    @Test
    fun introHoldIsShortAfterTheOpenStatus() {
        val tracker = SessionSpeechTracker()
        val first = tracker.guidanceStartLines()
        tracker.suppressAfter(first, nowElapsedMs = 1_000L)
        assertEquals(1_000L + GuidanceSpeech.FIRST_INTRO_SUPPRESS_MS, tracker.suppressNavigationUntilMs)
    }

    @Test
    fun hazardAtOpenSkipsTheStatusLine() {
        val tracker = SessionSpeechTracker()
        tracker.markStartupSkippedForHazard(nowElapsedMs = 2_000L)
        assertTrue(tracker.startupLineDelivered)
        assertEquals(2_000L, tracker.suppressNavigationUntilMs)
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
    fun cameraSpeechGateAnnouncesUnavailableOnceAndRecoveryOnce() {
        val gate = CameraSpeechGate()
        assertNull(gate.cueForTransition(CameraLifecyclePhase.STARTING))
        assertNull(gate.cueForTransition(CameraLifecyclePhase.STARTING))
        assertNull(gate.cueForTransition(CameraLifecyclePhase.STREAMING))
        val failed = gate.cueForTransition(CameraLifecyclePhase.FAILED)
        assertEquals(VoiceAlertKind.CAMERA_UNAVAILABLE, failed?.kind)
        assertNull(gate.cueForTransition(CameraLifecyclePhase.FAILED))
        val restored = gate.cueForTransition(CameraLifecyclePhase.STREAMING)
        assertEquals(VoiceAlertKind.SESSION_NOTICE, restored?.kind)
        assertEquals("Camera ready.", restored?.english)
        assertNull(gate.cueForTransition(CameraLifecyclePhase.STREAMING))
        assertNull(gate.cueForTransition(CameraLifecyclePhase.STARTING))
        // A new unavailable episode may announce again.
        val failedAgain = gate.cueForTransition(CameraLifecyclePhase.FAILED)
        assertEquals(VoiceAlertKind.CAMERA_UNAVAILABLE, failedAgain?.kind)
    }

    @Test
    fun cameraUnavailableStaysLatchedAcrossFailedStartingRetries() {
        val gate = CameraSpeechGate()
        // Clean first open: starting → streaming must not invent a recovery line.
        assertNull(gate.cueForTransition(CameraLifecyclePhase.IDLE))
        assertNull(gate.cueForTransition(CameraLifecyclePhase.STARTING))
        assertNull(gate.cueForTransition(CameraLifecyclePhase.STREAMING))

        val firstFail = gate.cueForTransition(CameraLifecyclePhase.FAILED)
        assertEquals(VoiceAlertKind.CAMERA_UNAVAILABLE, firstFail?.kind)
        assertEquals("Camera unavailable.", firstFail?.english)

        // Retry phases must not clear the episode or re-speak unavailable.
        assertNull(gate.cueForTransition(CameraLifecyclePhase.STARTING))
        assertNull(gate.cueForTransition(CameraLifecyclePhase.IDLE))
        assertNull(gate.cueForTransition(CameraLifecyclePhase.STARTING))
        assertNull(gate.cueForTransition(CameraLifecyclePhase.FAILED))
        assertNull(gate.cueForTransition(CameraLifecyclePhase.PERMISSION_REQUIRED))
        assertNull(gate.cueForTransition(CameraLifecyclePhase.STARTING))
        assertNull(gate.cueForTransition(CameraLifecyclePhase.FAILED))

        val restored = gate.cueForTransition(CameraLifecyclePhase.STREAMING)
        assertEquals(VoiceAlertKind.SESSION_NOTICE, restored?.kind)
        assertEquals("Camera ready.", restored?.english)
        assertNull(gate.cueForTransition(CameraLifecyclePhase.STREAMING))
        assertNull(gate.cueForTransition(CameraLifecyclePhase.STARTING))

        // After real recovery, a later failure is a new episode.
        val secondFail = gate.cueForTransition(CameraLifecyclePhase.FAILED)
        assertEquals(VoiceAlertKind.CAMERA_UNAVAILABLE, secondFail?.kind)
        assertNull(gate.cueForTransition(CameraLifecyclePhase.STARTING))
        assertNull(gate.cueForTransition(CameraLifecyclePhase.FAILED))
    }
}
