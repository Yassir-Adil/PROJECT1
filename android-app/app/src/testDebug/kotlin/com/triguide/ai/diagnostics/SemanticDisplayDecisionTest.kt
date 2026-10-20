package com.triguide.ai.diagnostics

import org.junit.Assert.*
import org.junit.Test

class SemanticDisplayDecisionTest {
    @Test fun temporaryMissingBoundsCanRetryButExpiredOrInvalidatedResultsMustClear() {
        assertFalse(SemanticDisplayDecision.ALLOWED.clearsPublication)
        assertFalse(SemanticDisplayDecision.INVALID_BOUNDS.clearsPublication)
        for (decision in SemanticDisplayDecision.entries.filter {
            it != SemanticDisplayDecision.ALLOWED && it != SemanticDisplayDecision.INVALID_BOUNDS
        }) assertTrue(decision.clearsPublication)
    }
    @Test fun reasonsDistinguishTrackingGeometryAndTerminalStop() {
        val state = SemanticFusionPublication()
        assertEquals(SemanticDisplayDecision.NOT_TRACKING, state.decision(0, 100, 101))
        state.trackingChanged(true)
        val revision = state.revision()
        assertEquals(SemanticDisplayDecision.ALLOWED, state.decision(revision, 100, 101))
        state.invalidateGeometry()
        assertEquals(SemanticDisplayDecision.REVISION_CHANGED, state.decision(revision, 100, 101))
        state.stop()
        assertEquals(SemanticDisplayDecision.STOPPED, state.decision(state.revision(), 100, 101))
    }

    @Test fun originalReceiptDeadlineAndClockFailuresHaveExactReasons() {
        val state = SemanticFusionPublication().apply { trackingChanged(true) }
        val revision = state.revision()
        assertEquals(SemanticDisplayDecision.INVALID_TIMESTAMP, state.decision(revision, -1, 100))
        assertEquals(SemanticDisplayDecision.CLOCK_ROLLBACK, state.decision(revision, 100, 99))
        assertEquals(SemanticDisplayDecision.ALLOWED, state.decision(revision, 100, 239_720_408))
        assertEquals(SemanticDisplayDecision.ALLOWED, state.decision(revision, 100, 250_000_100))
        assertEquals(SemanticDisplayDecision.EXPIRED, state.decision(revision, 100, 250_000_101))
    }

    @Test fun booleanCompatibilityRemainsIdenticalAcrossDecisionBoundaries() {
        val state = SemanticFusionPublication().apply { trackingChanged(true) }
        val revision = state.revision()
        for (now in listOf(-1L, 99, 100, 239_720_408, 250_000_100, 250_000_101)) {
            assertEquals(state.canPublish(revision, 100, now),
                state.decision(revision, 100, now) == SemanticDisplayDecision.ALLOWED)
        }
    }
}
