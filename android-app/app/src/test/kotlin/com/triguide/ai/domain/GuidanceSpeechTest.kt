package com.triguide.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceSpeechTest {
    @Test
    fun disclaimerIsSpokenOnceAtSessionStart() {
        val tracker = SessionSpeechTracker()
        val first = tracker.guidanceStartLines()
        assertEquals(
            listOf("disclaimer", "guidance-on", "gesture-hint"),
            first.map { it.id },
        )
        assertTrue(first.first().english.startsWith("Research and Simulation Mode."))
        assertTrue(GuidanceSpeech.gestureHint.english.contains("fingerprint button cannot control"))
        assertTrue(GuidanceSpeech.gestureHint.english.contains("Triple-tap"))
        assertTrue(GuidanceSpeech.gestureHint.arabic.contains("ثلاث نقرات"))
        assertTrue(GuidanceSpeech.GESTURE_ON_SCREEN.contains("Triple-tap the screen to restart guidance"))

        tracker.disclaimerDelivered = true
        assertEquals(listOf("guidance-on"), tracker.guidanceStartLines().map { it.id })
        assertEquals("Camera started. Guidance on.", tracker.guidanceStartLines().single().english)
    }

    @Test
    fun introHoldIsLongerWhenTheDisclaimerIsIncluded() {
        val tracker = SessionSpeechTracker()
        val first = tracker.guidanceStartLines()
        tracker.suppressAfter(first, nowElapsedMs = 1_000L)
        assertEquals(1_000L + GuidanceSpeech.FIRST_INTRO_SUPPRESS_MS, tracker.suppressNavigationUntilMs)

        tracker.disclaimerDelivered = true
        val restart = tracker.guidanceStartLines()
        tracker.suppressAfter(restart, nowElapsedMs = 5_000L)
        assertEquals(5_000L + GuidanceSpeech.RESTART_SUPPRESS_MS, tracker.suppressNavigationUntilMs)
    }

    @Test
    fun aCommittedTurnIsNotDroppedDuringTheIntroduction() {
        val turn = VoiceCues.turn(GuidanceAction.LEFT)
        val obstacle = VoiceCues.obstacle()
        val camera = VoiceCues.cameraUnavailable()
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
        assertNull(
            navigationCueForSpeech(
                cue = camera,
                nowElapsedMs = 3_000L,
                guidanceStartedAtMs = 0L,
                suppressNavigationUntilMs = 16_000L,
            ),
        )
        assertEquals(
            turn,
            navigationCueForSpeech(
                cue = turn,
                nowElapsedMs = 16_000L,
                guidanceStartedAtMs = 0L,
                suppressNavigationUntilMs = 16_000L,
            ),
        )
        assertFalse(trackerWouldRepeatDisclaimer())
    }

    @Test
    fun cameraUnavailableStaysQuietDuringStartupGrace() {
        assertNull(
            navigationCueForSpeech(
                cue = VoiceCues.cameraUnavailable(),
                nowElapsedMs = VoiceConfig.CAMERA_START_GRACE_MS - 1,
                guidanceStartedAtMs = 0L,
                suppressNavigationUntilMs = 0L,
            ),
        )
        assertEquals(
            VoiceCues.cameraUnavailable(),
            navigationCueForSpeech(
                cue = VoiceCues.cameraUnavailable(),
                nowElapsedMs = VoiceConfig.CAMERA_START_GRACE_MS,
                guidanceStartedAtMs = 0L,
                suppressNavigationUntilMs = 0L,
            ),
        )
    }

    private fun trackerWouldRepeatDisclaimer(): Boolean {
        val tracker = SessionSpeechTracker()
        tracker.disclaimerDelivered = true
        return tracker.guidanceStartLines().any { it.id == "disclaimer" }
    }
}
