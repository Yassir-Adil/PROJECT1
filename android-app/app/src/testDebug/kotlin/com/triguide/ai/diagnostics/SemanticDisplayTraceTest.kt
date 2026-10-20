package com.triguide.ai.diagnostics

import org.junit.Assert.*
import org.junit.Test

class SemanticDisplayTraceTest {
    @Test fun nearDeadlineUiDoesNotClaimAnyViewOrDrawEntry() {
        val trace = SemanticDisplayTrace()
        trace.ui(0, 239_720_308, 241_000_000, SemanticDisplayDecision.ALLOWED)
        val value = trace.snapshot()
        assertEquals(1, value.uiEntries)
        assertEquals(0, value.viewEntries)
        assertEquals(0, value.drawEntries)
        assertEquals(241.0, value.uiAgeP50Ms!!, 0.00001)
        assertEquals(1.279692, value.queueP50Ms!!, 0.00001)
        assertEquals(9.0, value.minimumUiBudgetMs!!, 0.00001)
    }

    @Test fun beforePaintExpiryIsDistinctFromSubmittedPaintCounters() {
        val trace = SemanticDisplayTrace()
        trace.ui(0, 239_720_308, 241_000_000, SemanticDisplayDecision.ALLOWED)
        trace.view(SemanticDisplayDecision.ALLOWED)
        trace.draw(SemanticDisplayDecision.EXPIRED)
        val value = trace.snapshot()
        assertEquals(1, value.drawEntries)
        assertEquals(1, value.drawRejected)
        assertEquals(mapOf(SemanticDisplayDecision.EXPIRED to 1), value.drawReasons)
        // Trace entry is not drawRect, Canvas submission, or human visibility.
        assertEquals(0, SemanticOverlayStats().snapshot().submittedCalls)
    }

    @Test fun uiViewAndDrawReasonsAreSeparated() {
        val trace = SemanticDisplayTrace()
        trace.ui(100, 110, 120, SemanticDisplayDecision.REVISION_CHANGED)
        trace.view(SemanticDisplayDecision.PUBLICATION_REJECTED)
        trace.draw(SemanticDisplayDecision.INVALID_BOUNDS)
        trace.draw(SemanticDisplayDecision.EMPTY_PAINT)
        val value = trace.snapshot()
        assertEquals(1, value.uiRejected)
        assertEquals(1, value.viewRejected)
        assertEquals(2, value.drawRejected)
        assertEquals(1, value.uiReasons[SemanticDisplayDecision.REVISION_CHANGED])
        assertEquals(1, value.viewReasons[SemanticDisplayDecision.PUBLICATION_REJECTED])
        assertEquals(2, value.drawReasons.size)
    }

    @Test fun invalidTimingDoesNotPoisonFutureTimingsOrChangeDecisions() {
        val trace = SemanticDisplayTrace()
        trace.ui(100, 90, 120, SemanticDisplayDecision.CLOCK_ROLLBACK)
        trace.ui(100, 110, 105, SemanticDisplayDecision.CLOCK_ROLLBACK)
        trace.ui(-1, 100, 120, SemanticDisplayDecision.INVALID_TIMESTAMP)
        assertNull(trace.snapshot().queueP50Ms)
        trace.ui(0, 10_000_000, 20_000_000, SemanticDisplayDecision.ALLOWED)
        assertEquals(1, trace.snapshot().timingSamples)
        assertEquals(20.0, trace.snapshot().uiAgeP50Ms!!, 0.0)
    }

    @Test fun timingStorageIsBoundedAndSampleCountIsExplicit() {
        val trace = SemanticDisplayTrace()
        repeat(200) { trace.ui(0, 10_000_000, 20_000_000, SemanticDisplayDecision.ALLOWED) }
        val value = trace.snapshot()
        assertEquals(200, value.uiEntries)
        assertEquals(60, value.timingSamples)
        assertEquals(20.0, value.uiAgeP90Ms!!, 0.0)
        assertEquals(10.0, value.queueP90Ms!!, 0.0)
    }

    @Test fun stopFreezesTraceAndDoesNotAuthorizeLateWork() {
        val trace = SemanticDisplayTrace()
        trace.ui(0, 10, 20, SemanticDisplayDecision.ALLOWED)
        trace.stop()
        val stopped = trace.snapshot()
        trace.ui(0, 10, 20, SemanticDisplayDecision.STOPPED)
        trace.view(SemanticDisplayDecision.ALLOWED)
        trace.draw(SemanticDisplayDecision.ALLOWED)
        assertEquals(stopped, trace.snapshot())
    }
}
