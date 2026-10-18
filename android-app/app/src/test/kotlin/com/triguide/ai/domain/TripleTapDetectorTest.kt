package com.triguide.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripleTapDetectorTest {
    private val detector = TripleTapDetector()

    @Test
    fun threeTapsInsideTheGapCompleteTheGesture() {
        assertFalse(detector.onTap(0L).completed)
        assertFalse(detector.onTap(200L).completed)
        val third = detector.onTap(400L)
        assertTrue(third.completed)
        assertEquals(TripleTapDetector.REQUIRED_TAPS, third.countInGroup)
    }

    @Test
    fun aTapOnTheGapBoundaryStillCounts() {
        assertFalse(detector.onTap(0L).completed)
        assertFalse(detector.onTap(TripleTapDetector.MAX_GAP_MS).completed)
        assertTrue(detector.onTap(TripleTapDetector.MAX_GAP_MS * 2).completed)
    }

    @Test
    fun aGapLongerThanTheWindowStartsOver() {
        assertEquals(1, detector.onTap(0L).countInGroup)
        val restarted = detector.onTap(TripleTapDetector.MAX_GAP_MS + 1)
        assertFalse(restarted.completed)
        assertEquals(1, restarted.countInGroup)
        assertEquals(2, detector.onTap(TripleTapDetector.MAX_GAP_MS + 200).countInGroup)
        assertTrue(detector.onTap(TripleTapDetector.MAX_GAP_MS + 400).completed)
    }

    @Test
    fun twoTapsDoNotRestartGuidance() {
        assertFalse(detector.onTap(1_000L).completed)
        val second = detector.onTap(1_200L)
        assertFalse(second.completed)
        assertEquals(2, second.countInGroup)
    }

    @Test
    fun theGroupClearsAfterACompletedGesture() {
        detector.onTap(0L)
        detector.onTap(100L)
        assertTrue(detector.onTap(200L).completed)
        val next = detector.onTap(300L)
        assertFalse(next.completed)
        assertEquals(1, next.countInGroup)
    }

    @Test
    fun aClockStepBackwardStartsOver() {
        detector.onTap(500L)
        val mark = detector.onTap(499L)
        assertFalse(mark.completed)
        assertEquals(1, mark.countInGroup)
    }

    @Test
    fun resetDropsAPartialGroup() {
        detector.onTap(0L)
        detector.onTap(100L)
        detector.reset()
        assertEquals(1, detector.onTap(150L).countInGroup)
    }

    @Test
    fun theWindowStaysInsideTheRequestedRange() {
        assertTrue(TripleTapDetector.MAX_GAP_MS in 600L..800L)
        assertEquals(3, TripleTapDetector.REQUIRED_TAPS)
    }
}
