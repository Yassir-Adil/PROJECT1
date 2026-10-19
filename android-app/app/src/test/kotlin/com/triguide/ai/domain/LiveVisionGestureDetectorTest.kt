package com.triguide.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveVisionGestureDetectorTest {
    private val detector = LiveVisionGestureDetector()

    @Test
    fun threeUpSwipesDescribe() {
        assertNull(detector.onSwipe(LiveVisionSwipeDirection.UP, 0L).gesture)
        assertNull(detector.onSwipe(LiveVisionSwipeDirection.UP, 100L).gesture)
        val third = detector.onSwipe(LiveVisionSwipeDirection.UP, 200L)
        assertTrue(third.completed)
        assertEquals(LiveVisionGesture.DESCRIBE, third.gesture)
    }

    @Test
    fun threeDownOpenMore() {
        repeat(2) { i ->
            assertNull(detector.onSwipe(LiveVisionSwipeDirection.DOWN, i * 100L).gesture)
        }
        assertEquals(
            LiveVisionGesture.OPEN_MORE,
            detector.onSwipe(LiveVisionSwipeDirection.DOWN, 250L).gesture,
        )
    }

    @Test
    fun threeRightSpeakStatusAndThreeLeftRestart() {
        assertEquals(
            LiveVisionGesture.SPEAK_STATUS,
            third(LiveVisionSwipeDirection.RIGHT).gesture,
        )
        assertEquals(
            LiveVisionGesture.RESTART_GUIDANCE,
            third(LiveVisionSwipeDirection.LEFT, start = 1_000L).gesture,
        )
    }

    @Test
    fun directionChangeResetsCount() {
        detector.onSwipe(LiveVisionSwipeDirection.UP, 0L)
        detector.onSwipe(LiveVisionSwipeDirection.UP, 50L)
        val switched = detector.onSwipe(LiveVisionSwipeDirection.DOWN, 100L)
        assertEquals(1, switched.countInGroup)
        assertFalse(switched.completed)
        assertNull(switched.gesture)
    }

    @Test
    fun gapTooLongResetsGroup() {
        detector.onSwipe(LiveVisionSwipeDirection.UP, 0L)
        detector.onSwipe(LiveVisionSwipeDirection.UP, 50L)
        val late = detector.onSwipe(LiveVisionSwipeDirection.UP, LiveVisionGestureDetector.MAX_GAP_MS + 100L)
        assertEquals(1, late.countInGroup)
        assertNull(late.gesture)
    }

    private fun third(direction: LiveVisionSwipeDirection, start: Long = 0L): LiveVisionSwipeMark {
        detector.onSwipe(direction, start)
        detector.onSwipe(direction, start + 80L)
        return detector.onSwipe(direction, start + 160L)
    }
}
