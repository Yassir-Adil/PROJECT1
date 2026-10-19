package com.triguide.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelativeDepthRuntimePolicyTest {
    @Test
    fun depthHazardCanRaiseHold() {
        val depth = evidence(RelativeDepthEvidenceState.WALL_OR_LARGE_OBSTACLE)

        val result = RelativeDepthRiskGate.merge(
            detectorHold = false,
            depthEvidence = depth,
        )

        assertTrue(result.holdRequired)
        assertTrue(result.depthRaisedRisk)
        assertEquals("relative_depth_hold_only", result.reason)
    }

    @Test
    fun normalDepthCannotCreateClearanceOrLowerDetectorHold() {
        val normal = evidence(RelativeDepthEvidenceState.DIAGNOSTIC_NO_HAZARD)

        val keepDetectorHold = RelativeDepthRiskGate.merge(true, normal)
        val noDetectorHold = RelativeDepthRiskGate.merge(false, normal)

        assertTrue(keepDetectorHold.holdRequired)
        assertFalse(keepDetectorHold.depthRaisedRisk)
        assertFalse(noDetectorHold.holdRequired)
        assertFalse(noDetectorHold.depthRaisedRisk)
        assertEquals("relative_depth_did_not_lower_risk", noDetectorHold.reason)
    }

    @Test
    fun missingDepthPreservesExistingHold() {
        val result = RelativeDepthRiskGate.merge(
            detectorHold = true,
            depthEvidence = null,
        )

        assertTrue(result.holdRequired)
        assertFalse(result.depthRaisedRisk)
        assertEquals("detector_hold_preserved", result.reason)
    }

    @Test
    fun cadenceGateBoundsDepthWorkWithoutAQueue() {
        val gate = RelativeDepthInferenceCadenceGate(minimumIntervalMs = 250L)

        assertTrue(gate.tryAdmit(1_000L))
        assertFalse(gate.tryAdmit(1_100L))
        assertFalse(gate.tryAdmit(1_249L))
        assertTrue(gate.tryAdmit(1_250L))
        assertFalse(gate.tryAdmit(1_200L))
        gate.reset()
        assertTrue(gate.tryAdmit(1_200L))
    }

    @Test
    fun cadenceGateRejectsInvalidIntervalAndClock() {
        var threw = false
        try {
            RelativeDepthInferenceCadenceGate(minimumIntervalMs = 0L)
        } catch (_: IllegalArgumentException) {
            threw = true
        }

        val gate = RelativeDepthInferenceCadenceGate()
        assertTrue(threw)
        assertFalse(gate.tryAdmit(-1L))
    }

    @Test
    fun cadenceGateReportsClockRollbackWithoutPoisoningNextAdmission() {
        val gate = RelativeDepthInferenceCadenceGate(minimumIntervalMs = 250L)

        assertTrue(gate.evaluate(1_000L).admitted)

        val rollback = gate.evaluate(900L)
        assertFalse(rollback.admitted)
        assertEquals(
            RelativeDepthInferenceCadenceGate.RelativeDepthCadenceReason.CLOCK_ROLLBACK,
            rollback.reason,
        )
        assertEquals(1_000L, rollback.previousAcceptedElapsedMs)

        val next = gate.evaluate(1_250L)
        assertTrue(next.admitted)
        assertEquals(
            RelativeDepthInferenceCadenceGate.RelativeDepthCadenceReason.ACCEPTED,
            next.reason,
        )
    }

    @Test
    fun invalidClockDoesNotReplaceTheLastAcceptedTimestamp() {
        val gate = RelativeDepthInferenceCadenceGate(minimumIntervalMs = 250L)
        gate.tryAdmit(1_000L)

        val invalid = gate.evaluate(-1L)
        assertFalse(invalid.admitted)
        assertEquals(
            RelativeDepthInferenceCadenceGate.RelativeDepthCadenceReason.INVALID_CLOCK,
            invalid.reason,
        )
        assertEquals(1_000L, invalid.previousAcceptedElapsedMs)
        assertTrue(gate.tryAdmit(1_250L))
    }

    private fun evidence(state: RelativeDepthEvidenceState) = RelativeDepthEvidence(
        state = state,
        issue = RelativeDepthEvidenceIssue.NONE,
        frameId = 1L,
        frameElapsedMs = 1_000L,
        source = "test",
        centerNearScore = 0.8f,
        lowerFloorScore = 0.2f,
        detail = "test evidence",
    )
}
