package com.triguide.ai.diagnostics

import org.junit.Assert.*
import org.junit.Test

class SemanticViewportTest {
    @Test fun sameSizeHalfTurnStillInvalidatesMapping() {
        val state = SemanticViewport()
        assertTrue(state.changed(0, 100, 200))
        assertFalse(state.changed(0, 100, 200))
        assertTrue(state.changed(2, 100, 200))
        assertFalse(state.changed(2, 100, 200))
        assertTrue(state.changed(2, 200, 100))
    }
    @Test fun invalidDimensionsDoNotPoisonNextConfiguration() {
        val state = SemanticViewport()
        assertFalse(state.changed(0, 0, 200))
        assertFalse(state.changed(4, 100, 200))
        assertTrue(state.changed(0, 100, 200))
    }
}
