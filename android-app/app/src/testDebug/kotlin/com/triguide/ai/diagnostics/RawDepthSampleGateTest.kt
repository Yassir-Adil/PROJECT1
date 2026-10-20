package com.triguide.ai.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED contract for [RawDepthSampleGate.accept] with same-Frame [RawDepthPairProof].
 * No cross-clock equality/lag: depth and confidence stamps are source-local only.
 */
class RawDepthSampleGateTest {

    private class OpenCloseable : AutoCloseable {
        override fun close() = Unit
    }

    private fun <R> withProof(
        frameTimestampNs: Long = 1_000_000_000L,
        block: (RawDepthPairProof) -> R,
    ): R =
        RawDepthPairScope.withFrame(
            frame = frameTimestampNs,
            timestampOf = { it },
            acquireDepth = { OpenCloseable() },
            acquireConfidence = { OpenCloseable() },
            block = { proof, _, _ -> block(proof) },
        )

    private fun admit(
        gate: RawDepthSampleGate,
        pair: RawDepthPairProof,
        depth: Long = pair.frameTimestampNs,
        confidence: Long = depth,
        received: Long = 10L,
        completed: Long = 20L,
        tracking: Boolean = true,
    ): String =
        gate.accept(
            pair = pair,
            depthTimestampNs = depth,
            confidenceTimestampNs = confidence,
            receivedElapsedNs = received,
            completedElapsedNs = completed,
            tracking = tracking,
        )

    @Test
    fun freshSameFrameTrackingPairIsAccepted() {
        val gate = RawDepthSampleGate()
        val result = withProof { pair -> admit(gate, pair) }
        assertEquals("accepted", result)
    }

    @Test
    fun unequalButValidDepthAndConfidenceStampsAreAccepted() {
        // Cross-clock numeric inequality is allowed; equality is not an SDK contract.
        val gate = RawDepthSampleGate()
        val result = withProof(frameTimestampNs = 1_000_000_000L) { pair ->
            admit(
                gate,
                pair,
                depth = 900_000_000L,
                confidence = 900_000_000L - 67_484_000L,
            )
        }
        assertEquals("accepted", result)
    }

    @Test
    fun largeFrameVersusDepthNumericGapDoesNotImposeCrossClockLag() {
        // Former maxSensorLagNs / depth_in_future policies must not return.
        val gate = RawDepthSampleGate()
        val acceptedNear = withProof(frameTimestampNs = 1_500_000_000L) { pair ->
            admit(gate, pair, depth = 1_000_000_000L, confidence = 1L)
        }
        assertEquals("accepted", acceptedNear)

        val gate2 = RawDepthSampleGate()
        val acceptedDepthAheadOfFrame = withProof(frameTimestampNs = 1_000_000_000L) { pair ->
            admit(gate2, pair, depth = 1_000_000_001L, confidence = 42L)
        }
        assertEquals("accepted", acceptedDepthAheadOfFrame)
    }

    @Test
    fun inactiveProofAfterScopeExitIsRejected() {
        val gate = RawDepthSampleGate()
        var stolen: RawDepthPairProof? = null
        withProof { pair ->
            assertTrue(pair.isActive)
            stolen = pair
        }
        assertFalse(stolen!!.isActive)
        assertEquals(
            "inactive_proof",
            gate.accept(
                pair = stolen!!,
                depthTimestampNs = 1_000_000_000L,
                confidenceTimestampNs = 1_000_000_000L,
                receivedElapsedNs = 10L,
                completedElapsedNs = 20L,
                tracking = true,
            ),
        )
    }

    @Test
    fun inactiveProofRejectionDoesNotPoisonLaterValidAdmission() {
        val gate = RawDepthSampleGate()
        var stolen: RawDepthPairProof? = null
        withProof { pair -> stolen = pair }
        assertEquals(
            "inactive_proof",
            gate.accept(
                pair = stolen!!,
                depthTimestampNs = 1_000_000_000L,
                confidenceTimestampNs = 1_000_000_000L,
                receivedElapsedNs = 10L,
                completedElapsedNs = 20L,
                tracking = true,
            ),
        )
        val accepted = withProof(frameTimestampNs = 1_000_000_000L) { pair ->
            admit(gate, pair)
        }
        assertEquals("accepted", accepted)
    }

    @Test
    fun repeatedOrReprojectedDepthOnNewFrameIsRejected() {
        val gate = RawDepthSampleGate()
        withProof(frameTimestampNs = 1_000_000_000L) { pair ->
            assertEquals("accepted", admit(gate, pair, depth = 1_000_000_000L))
        }
        val replayed = withProof(frameTimestampNs = 1_100_000_000L) { pair ->
            admit(gate, pair, depth = 1_000_000_000L, confidence = 999L)
        }
        assertEquals("depth_not_new", replayed)
    }

    @Test
    fun sameFrameTimestampAndBackwardFrameAreRejected() {
        val gate = RawDepthSampleGate()
        withProof(frameTimestampNs = 1_000_000_000L) { pair ->
            assertEquals("accepted", admit(gate, pair))
        }
        val duplicate = withProof(frameTimestampNs = 1_000_000_000L) { pair ->
            admit(gate, pair, depth = 1_100_000_000L)
        }
        assertEquals("camera_not_new", duplicate)
        val backward = withProof(frameTimestampNs = 900_000_000L) { pair ->
            admit(gate, pair, depth = 1_200_000_000L)
        }
        assertEquals("camera_not_new", backward)
    }

    @Test
    fun advancingDistinctFrameAndDepthPairsAreAccepted() {
        val gate = RawDepthSampleGate()
        withProof(frameTimestampNs = 1_000_000_000L) { pair ->
            assertEquals("accepted", admit(gate, pair, depth = 1_000_000_000L))
        }
        val next = withProof(frameTimestampNs = 1_100_000_000L) { pair ->
            admit(gate, pair, depth = 1_100_000_000L, confidence = 50L)
        }
        assertEquals("accepted", next)
    }

    @Test
    fun backwardDepthRelativeToLastAcceptedDepthIsRejected() {
        val gate = RawDepthSampleGate()
        withProof(frameTimestampNs = 1_000_000_000L) { pair ->
            assertEquals("accepted", admit(gate, pair, depth = 1_000_000_000L))
        }
        val outOfOrder = withProof(frameTimestampNs = 1_100_000_000L) { pair ->
            admit(gate, pair, depth = 900_000_000L, confidence = 1L)
        }
        assertEquals("depth_not_new", outOfOrder)
    }

    @Test
    fun invalidSourceStampRejectionDoesNotPoisonTheNextCorrectPair() {
        val gate = RawDepthSampleGate()
        val badConfidence = withProof { pair ->
            admit(gate, pair, depth = 1_000_000_000L, confidence = 0L)
        }
        assertEquals("invalid_sensor_timestamp", badConfidence)
        val badDepth = withProof(frameTimestampNs = 1_100_000_000L) { pair ->
            admit(gate, pair, depth = -1L, confidence = 1L)
        }
        assertEquals("invalid_sensor_timestamp", badDepth)
        val accepted = withProof(frameTimestampNs = 1_000_000_000L) { pair ->
            admit(gate, pair)
        }
        assertEquals("accepted", accepted)
    }

    @Test
    fun zeroAndNegativeSensorTimesAreRejected() {
        for (timestamp in listOf(0L, -1L, Long.MIN_VALUE)) {
            assertEquals(
                "invalid_sensor_timestamp",
                withProof { pair -> admit(RawDepthSampleGate(), pair, depth = timestamp) },
            )
            assertEquals(
                "invalid_sensor_timestamp",
                withProof { pair ->
                    admit(RawDepthSampleGate(), pair, depth = 1_000_000_000L, confidence = timestamp)
                },
            )
        }
    }

    @Test
    fun missingTrackingRejectsEvenPlausibleDepth() {
        assertEquals(
            "not_tracking",
            withProof { pair -> admit(RawDepthSampleGate(), pair, tracking = false) },
        )
    }

    @Test
    fun completionAgeHasAnInclusiveBoundInElapsedClockOnly() {
        assertEquals(
            "accepted",
            withProof { pair ->
                admit(RawDepthSampleGate(), pair, received = 100L, completed = 250_000_100L)
            },
        )
        assertEquals(
            "processing_stale",
            withProof { pair ->
                admit(RawDepthSampleGate(), pair, received = 100L, completed = 250_000_101L)
            },
        )
    }

    @Test
    fun clockRollbackAndNegativeElapsedClockAreRejected() {
        assertEquals(
            "invalid_elapsed_clock",
            withProof { pair ->
                admit(RawDepthSampleGate(), pair, received = 20L, completed = 19L)
            },
        )
        assertEquals(
            "invalid_elapsed_clock",
            withProof { pair -> admit(RawDepthSampleGate(), pair, received = -1L) },
        )
    }

    @Test
    fun noSubtractionOfSensorEpochAndElapsedEpochIsRequired() {
        assertEquals(
            "accepted",
            withProof(frameTimestampNs = Long.MAX_VALUE - 1) { pair ->
                admit(
                    RawDepthSampleGate(),
                    pair,
                    depth = Long.MAX_VALUE - 1,
                    confidence = 1L,
                    received = 1L,
                    completed = 2L,
                )
            },
        )
    }

    @Test
    fun elapsedClockCannotGoBackwardsBetweenAcceptedPairs() {
        val gate = RawDepthSampleGate()
        withProof(frameTimestampNs = 1_000_000_000L) { pair ->
            assertEquals("accepted", admit(gate, pair, received = 10L, completed = 20L))
        }
        assertEquals(
            "invalid_elapsed_clock",
            withProof(frameTimestampNs = 1_100_000_000L) { pair ->
                admit(gate, pair, depth = 1_100_000_000L, received = 1L, completed = 2L)
            },
        )
        assertEquals(
            "accepted",
            withProof(frameTimestampNs = 1_100_000_000L) { pair ->
                admit(gate, pair, depth = 1_100_000_000L, received = 20L, completed = 21L)
            },
        )
    }

    @Test
    fun trackingLossRejectionDoesNotPoisonLaterValidAdmission() {
        val gate = RawDepthSampleGate()
        assertEquals(
            "not_tracking",
            withProof { pair -> admit(gate, pair, tracking = false) },
        )
        assertEquals(
            "accepted",
            withProof { pair -> admit(gate, pair) },
        )
    }

    @Test
    fun processingStaleRejectionDoesNotPoisonLaterValidAdmission() {
        val gate = RawDepthSampleGate()
        assertEquals(
            "processing_stale",
            withProof { pair ->
                admit(gate, pair, received = 100L, completed = 250_000_101L)
            },
        )
        assertEquals(
            "accepted",
            withProof { pair -> admit(gate, pair, received = 10L, completed = 20L) },
        )
    }

    @Test
    fun stopIsTerminalAndIdempotent() {
        val gate = RawDepthSampleGate()
        gate.stop()
        gate.stop()
        assertEquals(
            "stopped",
            withProof { pair -> admit(gate, pair) },
        )
    }

    @Test
    fun stopRejectionDoesNotRequirePoisoningSemanticsBeyondTerminalStop() {
        val gate = RawDepthSampleGate()
        withProof { pair -> assertEquals("accepted", admit(gate, pair)) }
        gate.stop()
        assertEquals(
            "stopped",
            withProof(frameTimestampNs = 2_000_000_000L) { pair ->
                admit(gate, pair, depth = 2_000_000_000L)
            },
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeAgeConfigurationIsRejected() {
        RawDepthSampleGate(maxProcessingAgeNs = -1)
    }

    @Test
    fun defaultProcessingAgeRemainsTwoHundredFiftyMilliseconds() {
        // Boundary uses the documented 250ms default; maxSensorLagNs must not exist.
        assertEquals(
            "accepted",
            withProof { pair ->
                admit(RawDepthSampleGate(), pair, received = 0L, completed = 250_000_000L)
            },
        )
        assertEquals(
            "processing_stale",
            withProof { pair ->
                admit(RawDepthSampleGate(), pair, received = 0L, completed = 250_000_001L)
            },
        )
    }

    @Test
    fun acceptNeverReturnsRemovedCrossClockReasons() {
        val gate = RawDepthSampleGate()
        val reasons = mutableListOf<String>()
        reasons += withProof(frameTimestampNs = 2_000_000_000L) { pair ->
            admit(gate, pair, depth = 1L, confidence = Long.MAX_VALUE)
        }
        reasons += withProof(frameTimestampNs = 2_100_000_000L) { pair ->
            admit(gate, pair, depth = 2L, confidence = 3L)
        }
        assertFalse(reasons.contains("confidence_mismatch"))
        assertFalse(reasons.contains("sensor_stale"))
        assertFalse(reasons.contains("depth_in_future"))
        assertEquals("accepted", reasons[0])
        assertEquals("accepted", reasons[1])
    }
}
