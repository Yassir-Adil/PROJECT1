package com.triguide.ai.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests-first contract for [RawDepthCoverageStats] / [RawDepthCoverageSnapshot].
 *
 * Diagnostic observed-coverage aggregates over successfully decoded
 * [RawDepthSummary] pairs only. Counts are not accepted gate samples and not
 * unique depth estimates. Fractions are weighted over all decoded pixel totals.
 *
 * Codex recorded missing-helper compile RED before Cursor implemented the helper.
 */
class RawDepthCoverageStatsTest {

    private fun summary(
        sampled: Int,
        valid: Int,
        confident: Int,
        medianMm: Int? = null,
        p90Mm: Int? = null,
    ): RawDepthSummary = RawDepthSummary(
        sampledPixels = sampled,
        validDepthPixels = valid,
        confidentPixels = confident,
        medianDepthMm = medianMm,
        p90DepthMm = p90Mm,
    )

    @Test
    fun beforeAnyRecordFractionsAreMissingNotZero() {
        val stats = RawDepthCoverageStats()
        val snap = stats.snapshot()

        assertEquals(0L, snap.decodedPairs)
        assertEquals(0L, snap.sampledPixels)
        assertEquals(0L, snap.validDepthPixels)
        assertEquals(0L, snap.confidentPixels)
        assertEquals(0L, snap.pairsWithConfidentPixels)
        assertNull(snap.validFraction)
        assertNull(snap.confidentFraction)
        assertFalse(snap.capacityReached)
    }

    @Test
    fun observedAllZeroDepthIsZeroFractionNotMissing() {
        val stats = RawDepthCoverageStats()
        assertTrue(stats.record(summary(sampled = 64, valid = 0, confident = 0)))

        val snap = stats.snapshot()
        assertEquals(1L, snap.decodedPairs)
        assertEquals(64L, snap.sampledPixels)
        assertEquals(0L, snap.validDepthPixels)
        assertEquals(0L, snap.confidentPixels)
        assertEquals(0L, snap.pairsWithConfidentPixels)
        assertNotNull(snap.validFraction)
        assertNotNull(snap.confidentFraction)
        assertEquals(0.0, snap.validFraction!!, 0.0)
        assertEquals(0.0, snap.confidentFraction!!, 0.0)
        assertFalse(snap.capacityReached)
    }

    @Test
    fun weightedFractionsUseAllDecodedPixelTotalsNotPerPairAverage() {
        // Pair A alone would be validFraction=1.0; pair B alone 0.0.
        // Weighted over pixels must be 10/100 = 0.1, not the mean of pair fractions (0.5).
        val stats = RawDepthCoverageStats()
        assertTrue(stats.record(summary(sampled = 10, valid = 10, confident = 5, medianMm = 1_200)))
        assertTrue(stats.record(summary(sampled = 90, valid = 0, confident = 0)))

        val snap = stats.snapshot()
        assertEquals(2L, snap.decodedPairs)
        assertEquals(100L, snap.sampledPixels)
        assertEquals(10L, snap.validDepthPixels)
        assertEquals(5L, snap.confidentPixels)
        assertEquals(1L, snap.pairsWithConfidentPixels)
        assertEquals(0.1, snap.validFraction!!, 0.0)
        assertEquals(0.05, snap.confidentFraction!!, 0.0)
    }

    @Test
    fun sparseSummariesAccumulateAcrossDecodedPairs() {
        val stats = RawDepthCoverageStats()
        assertTrue(stats.record(summary(sampled = 1_000, valid = 3, confident = 1)))
        assertTrue(stats.record(summary(sampled = 1_000, valid = 7, confident = 0)))
        assertTrue(stats.record(summary(sampled = 500, valid = 0, confident = 0)))

        val snap = stats.snapshot()
        assertEquals(3L, snap.decodedPairs)
        assertEquals(2_500L, snap.sampledPixels)
        assertEquals(10L, snap.validDepthPixels)
        assertEquals(1L, snap.confidentPixels)
        assertEquals(1L, snap.pairsWithConfidentPixels)
        assertEquals(10.0 / 2_500.0, snap.validFraction!!, 0.0)
        assertEquals(1.0 / 2_500.0, snap.confidentFraction!!, 0.0)
    }

    @Test
    fun countsEveryDecodedPairNotAcceptedSamplesOrUniqueEstimates() {
        // Two identical summaries still count as two decoded pairs; median/p90 are ignored.
        val stats = RawDepthCoverageStats()
        val twin = summary(sampled = 8, valid = 4, confident = 2, medianMm = 900, p90Mm = 1_100)
        assertTrue(stats.record(twin))
        assertTrue(stats.record(twin))

        val snap = stats.snapshot()
        assertEquals(2L, snap.decodedPairs)
        assertEquals(16L, snap.sampledPixels)
        assertEquals(8L, snap.validDepthPixels)
        assertEquals(4L, snap.confidentPixels)
        assertEquals(2L, snap.pairsWithConfidentPixels)
        assertEquals(0.5, snap.validFraction!!, 0.0)
        assertEquals(0.25, snap.confidentFraction!!, 0.0)
    }

    @Test
    fun malformedSampledCountIsRejectedWithoutMutation() {
        val stats = RawDepthCoverageStats()
        assertTrue(stats.record(summary(sampled = 4, valid = 2, confident = 1)))
        val before = stats.snapshot()

        assertFalse(stats.record(summary(sampled = 0, valid = 0, confident = 0)))
        assertFalse(stats.record(summary(sampled = -1, valid = 0, confident = 0)))
        assertFalse(stats.record(summary(sampled = 1_048_577, valid = 0, confident = 0)))
        assertFalse(stats.record(summary(sampled = Int.MAX_VALUE, valid = 0, confident = 0)))

        assertEquals(before, stats.snapshot())
    }

    @Test
    fun malformedPixelOrderingIsRejectedWithoutMutation() {
        val stats = RawDepthCoverageStats()
        assertTrue(stats.record(summary(sampled = 16, valid = 8, confident = 4)))
        val before = stats.snapshot()

        // Require 0 <= confident <= valid <= sampled.
        assertFalse(stats.record(summary(sampled = 16, valid = 17, confident = 0)))
        assertFalse(stats.record(summary(sampled = 16, valid = 8, confident = 9)))
        assertFalse(stats.record(summary(sampled = 16, valid = -1, confident = 0)))
        assertFalse(stats.record(summary(sampled = 16, valid = 8, confident = -1)))

        assertEquals(before, stats.snapshot())
    }

    @Test
    fun boundarySampledOneAndMaxAreAccepted() {
        val stats = RawDepthCoverageStats()
        assertTrue(stats.record(summary(sampled = 1, valid = 0, confident = 0)))
        assertTrue(stats.record(summary(sampled = 1_048_576, valid = 1_048_576, confident = 1_048_576)))

        val snap = stats.snapshot()
        assertEquals(2L, snap.decodedPairs)
        assertEquals(1L + 1_048_576L, snap.sampledPixels)
        assertEquals(1_048_576L, snap.validDepthPixels)
        assertEquals(1_048_576L, snap.confidentPixels)
        assertEquals(1L, snap.pairsWithConfidentPixels)
    }

    @Test
    fun capacityBoundRejectsFurtherRecordsAndSetsCapacityReached() {
        val stats = RawDepthCoverageStats(maxPairs = 2)
        assertTrue(stats.record(summary(sampled = 2, valid = 1, confident = 0)))
        assertFalse(stats.snapshot().capacityReached)

        assertTrue(stats.record(summary(sampled = 2, valid = 2, confident = 1)))
        val full = stats.snapshot()
        assertEquals(2L, full.decodedPairs)
        assertTrue(full.capacityReached)

        assertFalse(stats.record(summary(sampled = 2, valid = 1, confident = 1)))
        val afterReject = stats.snapshot()
        assertEquals(full, afterReject)
        assertEquals(2L, afterReject.decodedPairs)
        assertEquals(4L, afterReject.sampledPixels)
        assertEquals(3L, afterReject.validDepthPixels)
        assertEquals(1L, afterReject.confidentPixels)
        assertEquals(1L, afterReject.pairsWithConfidentPixels)
        assertTrue(afterReject.capacityReached)
    }

    @Test
    fun defaultMaxPairsIsTenThousand() {
        val stats = RawDepthCoverageStats()
        repeat(10_000) { index ->
            assertTrue(
                "expected accept at index $index",
                stats.record(summary(sampled = 1, valid = 0, confident = 0)),
            )
        }
        val full = stats.snapshot()
        assertEquals(10_000L, full.decodedPairs)
        assertTrue(full.capacityReached)
        assertFalse(stats.record(summary(sampled = 1, valid = 0, confident = 0)))
        assertEquals(10_000L, stats.snapshot().decodedPairs)
    }

    @Test
    fun snapshotIsImmutableAfterLaterSuccessfulRecords() {
        val stats = RawDepthCoverageStats()
        assertTrue(stats.record(summary(sampled = 10, valid = 4, confident = 2)))
        val first = stats.snapshot()

        assertTrue(stats.record(summary(sampled = 30, valid = 6, confident = 3)))
        val second = stats.snapshot()

        assertEquals(1L, first.decodedPairs)
        assertEquals(10L, first.sampledPixels)
        assertEquals(4L, first.validDepthPixels)
        assertEquals(2L, first.confidentPixels)
        assertEquals(1L, first.pairsWithConfidentPixels)
        assertEquals(0.4, first.validFraction!!, 0.0)
        assertEquals(0.2, first.confidentFraction!!, 0.0)
        assertFalse(first.capacityReached)

        assertEquals(2L, second.decodedPairs)
        assertEquals(40L, second.sampledPixels)
        assertEquals(10L, second.validDepthPixels)
        assertEquals(5L, second.confidentPixels)
        assertEquals(2L, second.pairsWithConfidentPixels)
        assertEquals(0.25, second.validFraction!!, 0.0)
        assertEquals(0.125, second.confidentFraction!!, 0.0)
    }

    @Test
    fun stopIsTerminalAndIdempotent() {
        val stats = RawDepthCoverageStats()
        assertTrue(stats.record(summary(sampled = 5, valid = 2, confident = 1)))
        val beforeStop = stats.snapshot()

        stats.stop()
        stats.stop()

        assertFalse(stats.record(summary(sampled = 5, valid = 3, confident = 2)))
        val afterStop = stats.snapshot()
        assertEquals(beforeStop, afterStop)
        assertEquals(1L, afterStop.decodedPairs)
        assertFalse(afterStop.capacityReached)
    }

    @Test
    fun stopAfterCapacityRemainsRejectedAndIdempotent() {
        val stats = RawDepthCoverageStats(maxPairs = 1)
        assertTrue(stats.record(summary(sampled = 3, valid = 1, confident = 1)))
        assertTrue(stats.snapshot().capacityReached)
        assertFalse(stats.record(summary(sampled = 3, valid = 1, confident = 0)))

        stats.stop()
        stats.stop()
        assertFalse(stats.record(summary(sampled = 3, valid = 2, confident = 2)))

        val snap = stats.snapshot()
        assertEquals(1L, snap.decodedPairs)
        assertEquals(3L, snap.sampledPixels)
        assertEquals(1L, snap.validDepthPixels)
        assertEquals(1L, snap.confidentPixels)
        assertEquals(1L, snap.pairsWithConfidentPixels)
        assertTrue(snap.capacityReached)
    }
}
