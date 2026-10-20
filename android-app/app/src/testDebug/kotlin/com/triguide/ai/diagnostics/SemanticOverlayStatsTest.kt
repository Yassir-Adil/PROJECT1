package com.triguide.ai.diagnostics

import org.junit.Assert.*
import org.junit.Test

class SemanticOverlayStatsTest {
    @Test fun actualPaintCommandsRemainCountedIfFreshnessExpiresDuringPaint() {
        val stats=SemanticOverlayStats(); stats.publish(1,100,110)
        assertFalse(stats.draw(1,250_000_101,true,30))
        val reading=stats.snapshot()
        assertEquals(1,reading.submittedCalls); assertEquals(30,reading.submittedCells)
        assertEquals(1,reading.rejectedAfterPaintCalls); assertEquals(0,reading.canvasSamples)
    }
    @Test fun publicationClockRollbackDoesNotReplaceCurrentOrPoisonLaterStamp() {
        val stats=SemanticOverlayStats(); stats.publish(1,100,110)
        assertFalse(stats.publish(2,50,60))
        assertTrue(stats.draw(1,120,true,30))
        assertTrue(stats.publish(2,130,140))
    }
    @Test fun publishingInferenceIsNotEvidenceOfCanvasDrawing() {
        val stats=SemanticOverlayStats()
        assertTrue(stats.publish(1,100,110))
        assertEquals(1,stats.snapshot().publishedSamples)
        assertEquals(0,stats.snapshot().canvasSamples)
        stats.clear()
        assertEquals(1,stats.snapshot().clearedWithoutDraw)
    }
    @Test fun repeatedCanvasRepaintsCountOnlyOneDistinctSample() {
        val stats=SemanticOverlayStats(); stats.publish(1,100,110)
        assertTrue(stats.draw(1,120,true,30)); assertTrue(stats.draw(1,130,true,30))
        assertEquals(1,stats.snapshot().canvasSamples)
        assertEquals(2,stats.snapshot().canvasCalls)
        assertEquals(60,stats.snapshot().paintedCells)
        stats.clear(); assertEquals(0,stats.snapshot().clearedWithoutDraw)
    }
    @Test fun expiryAndInvalidationRejectDrawWithoutExtendingAge() {
        val stats=SemanticOverlayStats(); stats.publish(1,100,110)
        assertFalse(stats.draw(1,120,false,30))
        assertFalse(stats.draw(1,250_000_101,true,30))
        assertTrue(stats.draw(1,250_000_100,true,30))
    }
    @Test fun invalidAndUnpublishedOldSamplesCannotClaimRendering() {
        val stats=SemanticOverlayStats()
        assertFalse(stats.draw(1,100,true,20)); assertFalse(stats.publish(0,100,110))
        assertTrue(stats.publish(2,100,110)); assertFalse(stats.publish(2,100,110))
        assertFalse(stats.draw(1,120,true,20)); assertFalse(stats.draw(2,120,true,65))
        assertFalse(stats.draw(2,120,true,0)); assertEquals(0,stats.snapshot().canvasSamples)
    }
    @Test fun stopIsTerminalEvenForLateUiWork() {
        val stats=SemanticOverlayStats(); stats.publish(1,100,110); stats.stop()
        assertFalse(stats.publish(2,200,210)); assertFalse(stats.draw(1,120,true,30))
        assertEquals(1,stats.snapshot().clearedWithoutDraw)
    }
}
