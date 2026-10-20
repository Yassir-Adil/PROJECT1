package com.triguide.ai.diagnostics

import org.junit.Assert.*
import org.junit.Test

class SemanticRenderSessionTest {
    @Test fun expiryIsTerminalEvenIfClockLaterReturnsInsideLifetime() {
        val session = SemanticRenderSession(100, 3)
        assertFalse(session.allowDraw(3_000_000_101))
        assertFalse(session.allowDraw(200))
    }

    @Test fun clockRollbackCannotReenterAnEarlierPaintButDoesNotPoisonNextValidTime() {
        val session = SemanticRenderSession(0, 3)
        assertTrue(session.allowDraw(100))
        assertFalse(session.allowDraw(99))
        assertTrue(session.allowDraw(101))
    }
    @Test fun syntheticPatternHasExplicitBoundedLifetimeAndRejectsClockRollback() {
        val session = SemanticRenderSession(100, 5)
        assertFalse(session.allowDraw(99))
        assertTrue(session.allowDraw(100))
        assertTrue(session.allowDraw(5_000_000_100))
        assertFalse(session.allowDraw(5_000_000_101))
    }

    @Test fun terminalStopRejectsAllLateDrawsWithoutRestart() {
        val session = SemanticRenderSession(0, 3)
        assertTrue(session.allowDraw(10))
        session.stop()
        assertFalse(session.allowDraw(10))
        assertFalse(session.allowDraw(0))
    }

    @Test fun durationAndTimestampAreValidatedNotSilentlyClamped() {
        for (duration in listOf(Int.MIN_VALUE, -1, 0, 2, 16, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { SemanticRenderSession(0, duration) }
        }
        assertThrows(IllegalArgumentException::class.java) { SemanticRenderSession(-1, 5) }
        assertTrue(SemanticRenderSession(0, 15).allowDraw(0))
    }
}
