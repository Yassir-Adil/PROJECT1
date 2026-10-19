package com.triguide.ai.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RawDepthSampleGateTest {
    private fun sample(gate: RawDepthSampleGate, camera: Long = 1_000_000_000L,
        depth: Long = camera, confidence: Long = depth, received: Long = 10L,
        completed: Long = 20L, tracking: Boolean = true) = gate.accept(
        camera, depth, confidence, received, completed, tracking)

    @Test fun freshAlignedTrackingPairIsAccepted() {
        assertEquals("accepted", sample(RawDepthSampleGate()))
    }
    @Test fun oldRawDataCannotBeCountedAgainOnAnotherCameraFrame() {
        val gate = RawDepthSampleGate()
        assertEquals("accepted", sample(gate))
        assertNotEquals("accepted", sample(gate, camera = 1_100_000_000L, depth = 1_000_000_000L))
    }
    @Test fun sameCameraFrameAndBackwardCameraFrameAreRejected() {
        val gate = RawDepthSampleGate()
        sample(gate)
        assertNotEquals("accepted", sample(gate))
        assertNotEquals("accepted", sample(gate, camera = 900_000_000L))
    }
    @Test fun advancingDistinctPairsAreAccepted() {
        val gate = RawDepthSampleGate()
        sample(gate)
        assertEquals("accepted", sample(gate, camera = 1_100_000_000L))
    }
    @Test fun outOfOrderDepthAndMismatchedConfidenceAreRejected() {
        val gate = RawDepthSampleGate()
        sample(gate)
        assertNotEquals("accepted", sample(gate, camera = 1_100_000_000L, depth = 900_000_000L))
        assertNotEquals("accepted", sample(gate, camera = 1_200_000_000L, confidence = 1_199_999_999L))
    }
    @Test fun rejectionDoesNotPoisonTheNextCorrectPair() {
        val gate = RawDepthSampleGate()
        assertNotEquals("accepted", sample(gate, confidence = 99))
        assertEquals("accepted", sample(gate))
    }
    @Test fun missingTrackingRejectsEvenPlausibleDepth() {
        assertNotEquals("accepted", sample(RawDepthSampleGate(), tracking = false))
    }
    @Test fun zeroNegativeAndFutureSensorTimesAreRejected() {
        for (timestamp in listOf(0L, -1L, Long.MIN_VALUE)) {
            assertNotEquals("accepted", sample(RawDepthSampleGate(), camera = timestamp))
            assertNotEquals("accepted", sample(RawDepthSampleGate(), depth = timestamp))
        }
        assertNotEquals("accepted", sample(RawDepthSampleGate(), depth = 1_000_000_001L))
    }
    @Test fun sensorLagHasAnInclusiveBoundInItsOwnClockEpoch() {
        assertEquals("accepted", sample(RawDepthSampleGate(), camera = 1_500_000_000L, depth = 1_000_000_000L))
        assertNotEquals("accepted", sample(RawDepthSampleGate(), camera = 1_500_000_001L, depth = 1_000_000_000L))
    }
    @Test fun completionAgeHasAnInclusiveBoundInElapsedClockOnly() {
        assertEquals("accepted", sample(RawDepthSampleGate(), received = 100, completed = 250_000_100L))
        assertNotEquals("accepted", sample(RawDepthSampleGate(), received = 100, completed = 250_000_101L))
    }
    @Test fun clockRollbackAndNegativeElapsedClockAreRejected() {
        assertNotEquals("accepted", sample(RawDepthSampleGate(), received = 20, completed = 19))
        assertNotEquals("accepted", sample(RawDepthSampleGate(), received = -1))
    }
    @Test fun noSubtractionOfSensorEpochAndElapsedEpochIsRequired() {
        assertEquals("accepted", sample(RawDepthSampleGate(), camera = Long.MAX_VALUE - 1,
            received = 1, completed = 2))
    }
    @Test fun elapsedClockCannotGoBackwardsBetweenAcceptedPairs() {
        val gate = RawDepthSampleGate()
        sample(gate)
        assertNotEquals("accepted", sample(gate, camera = 1_100_000_000L, received = 1, completed = 2))
        assertEquals("accepted", sample(gate, camera = 1_100_000_000L, received = 20, completed = 21))
    }
    @Test fun stopIsTerminalAndIdempotent() {
        val gate = RawDepthSampleGate()
        gate.stop(); gate.stop()
        assertEquals("stopped", sample(gate))
    }
    @Test(expected = IllegalArgumentException::class)
    fun negativeAgeConfigurationIsRejected() { RawDepthSampleGate(maxProcessingAgeNs = -1) }
    @Test(expected = IllegalArgumentException::class)
    fun negativeLagConfigurationIsRejected() { RawDepthSampleGate(maxSensorLagNs = -1) }
}
