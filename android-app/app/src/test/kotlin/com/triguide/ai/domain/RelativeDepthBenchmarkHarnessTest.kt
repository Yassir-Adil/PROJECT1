package com.triguide.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelativeDepthBenchmarkHarnessTest {
    @Test
    fun defaultSyntheticBenchmarkPassesAndRemainsDiagnosticOnly() {
        val report = RelativeDepthBenchmarkHarness.run()

        assertTrue(report.allPassed)
        assertEquals(11, report.caseCount)
        assertEquals(11, report.passedCaseCount)
        assertEquals(2, report.riskRaisedCount)
        assertEquals(5, report.holdRequiredCount)
        assertEquals(3, report.stateCounts.getValue(RelativeDepthEvidenceState.INVALID))
        assertEquals(2, report.stateCounts.getValue(RelativeDepthEvidenceState.STALE))
        assertTrue(report.deterministicSignature.contains("center_wall:WALL_OR_LARGE_OBSTACLE"))
        assertFalse(report.deterministicSignature.contains("CLEAR"))
        assertFalse(report.deterministicSignature.contains("STRAIGHT"))
    }

    @Test
    fun normalDepthNeverClearsDetectorHoldInBenchmarkMatrix() {
        val report = RelativeDepthBenchmarkHarness.run()
        val row = report.results.single { it.id == "normal_floor_cannot_clear_detector_hold" }

        assertTrue(row.passed)
        assertEquals(true, row.holdRequired)
        assertEquals("detector_hold_preserved", row.riskGateReason)
        assertFalse(row.actualRaisesRisk)
    }

    @Test
    fun wallDepthRaisesHoldWithoutDetectorHazard() {
        val report = RelativeDepthBenchmarkHarness.run()
        val row = report.results.single { it.id == "center_wall" }

        assertTrue(row.passed)
        assertEquals(true, row.holdRequired)
        assertEquals("relative_depth_hold_only", row.riskGateReason)
    }

    @Test
    fun repeatedRunsHaveTheSameStableSignature() {
        val first = RelativeDepthBenchmarkHarness.run()
        val second = RelativeDepthBenchmarkHarness.run()

        assertEquals(first.deterministicSignature, second.deterministicSignature)
        assertEquals(first.stateCounts, second.stateCounts)
        assertEquals(first.toDiagnosticTsv(), second.toDiagnosticTsv())
    }

    @Test
    fun diagnosticTsvIsStableAndContainsOnlyPolicyOutcomes() {
        val report = RelativeDepthBenchmarkHarness.run()
        val tsv = report.toDiagnosticTsv()

        assertTrue(
            tsv.startsWith(
                "case_id\tactual_state\tactual_issue\trisk_raised\thold_required\trisk_gate_reason\tpassed\n",
            ),
        )
        assertTrue(
            tsv.contains(
                "center_wall\tWALL_OR_LARGE_OBSTACLE\tNONE\ttrue\ttrue\trelative_depth_hold_only\ttrue",
            ),
        )
        assertTrue(tsv.contains("hold_required_count=5"))
        assertFalse(tsv.contains("image"))
        assertFalse(tsv.contains("CLEAR"))
        assertFalse(tsv.contains("safe"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateCaseIdsAreRejected() {
        val cases = RelativeDepthBenchmarkHarness.defaultCases()
        RelativeDepthBenchmarkHarness.run(cases + cases.first())
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankCaseIdsAreRejected() {
        RelativeDepthBenchmarkHarness.run(
            listOf(
                RelativeDepthBenchmarkCase(
                    id = " ",
                    frame = null,
                    nowElapsedMs = 0L,
                    expectedState = RelativeDepthEvidenceState.UNAVAILABLE,
                    expectedIssue = RelativeDepthEvidenceIssue.NO_FRAME,
                    expectedRaisesRisk = false,
                ),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun tsvUnsafeCaseIdsAreRejected() {
        RelativeDepthBenchmarkHarness.run(
            listOf(
                RelativeDepthBenchmarkCase(
                    id = "bad\tcase",
                    frame = null,
                    nowElapsedMs = 0L,
                    expectedState = RelativeDepthEvidenceState.UNAVAILABLE,
                    expectedIssue = RelativeDepthEvidenceIssue.NO_FRAME,
                    expectedRaisesRisk = false,
                ),
            ),
        )
    }
}
