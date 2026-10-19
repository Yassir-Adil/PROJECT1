package com.triguide.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelativeDepthEvidenceTest {
    @Test
    fun blankCenterIsDiagnosticObstacleOnly() {
        val result = RelativeDepthEvidenceAnalyzer.evaluate(
            frame(values = List(64) { 0.9f }),
            nowElapsedMs = 1_100L,
        )

        assertEquals(RelativeDepthEvidenceState.WALL_OR_LARGE_OBSTACLE, result.state)
        assertTrue(result.raisesRisk)
        assertFalse(result.detail.contains("clearance established"))
    }

    @Test
    fun normalFloorDoesNotCreateClearance() {
        val values = buildList {
            repeat(8) { row -> repeat(8) { add(0.15f + row * 0.06f) } }
        }
        val result = RelativeDepthEvidenceAnalyzer.evaluate(
            frame(values = values),
            nowElapsedMs = 1_100L,
        )

        assertEquals(RelativeDepthEvidenceState.DIAGNOSTIC_NO_HAZARD, result.state)
        assertFalse(result.raisesRisk)
        assertTrue(result.detail.contains("clearance is not established"))
    }

    @Test
    fun abruptFloorDiscontinuityRaisesDiagnosticRisk() {
        val values = buildList {
            repeat(8) { row ->
                val value = when (row) {
                    2, 3, 4 -> 0.8f
                    else -> 0.2f
                }
                repeat(8) { add(value) }
            }
        }
        val result = RelativeDepthEvidenceAnalyzer.evaluate(
            frame(values = values),
            nowElapsedMs = 1_100L,
        )

        assertEquals(RelativeDepthEvidenceState.POSSIBLE_DROP, result.state)
        assertTrue(result.raisesRisk)
    }

    @Test
    fun staleAndOutOfOrderFramesFailClosed() {
        val stale = RelativeDepthEvidenceAnalyzer.evaluate(frame(), 1_600L)
        val outOfOrder = RelativeDepthEvidenceAnalyzer.evaluate(frame(frameId = 4), 1_100L, previousFrameId = 4L)

        assertEquals(RelativeDepthEvidenceState.STALE, stale.state)
        assertEquals(RelativeDepthEvidenceIssue.EXPIRED_FRAME, stale.issue)
        assertEquals(RelativeDepthEvidenceState.STALE, outOfOrder.state)
        assertEquals(RelativeDepthEvidenceIssue.NON_MONOTONIC_FRAME, outOfOrder.issue)
    }

    @Test
    fun malformedAndEmptyFramesNeverBecomeEvidence() {
        val empty = RelativeDepthEvidenceAnalyzer.evaluate(null, 1_100L)
        val malformed = RelativeDepthEvidenceAnalyzer.evaluate(
            frame(width = 3, height = 3, values = List(9) { 0.5f }),
            1_100L,
        )
        val nonFinite = RelativeDepthEvidenceAnalyzer.evaluate(
            frame(values = List(64) { if (it == 7) Float.NaN else 0.5f }),
            1_100L,
        )

        assertEquals(RelativeDepthEvidenceState.UNAVAILABLE, empty.state)
        assertEquals(RelativeDepthEvidenceIssue.NO_FRAME, empty.issue)
        assertEquals(RelativeDepthEvidenceState.INVALID, malformed.state)
        assertEquals(RelativeDepthEvidenceState.INVALID, nonFinite.state)
        assertFalse(empty.raisesRisk)
    }

    private fun frame(
        frameId: Long = 1L,
        frameElapsedMs: Long = 1_000L,
        width: Int = 8,
        height: Int = 8,
        values: List<Float> = List(width * height) { 0.2f },
    ) = RelativeDepthFrame(frameId, frameElapsedMs, width, height, values)
}
