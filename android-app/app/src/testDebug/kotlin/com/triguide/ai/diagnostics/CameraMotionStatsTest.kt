package com.triguide.ai.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED contract for pure-CPU camera pose displacement stats.
 * Scalar fixtures only — no ARCore / SDK mocks, no raw coordinate logs.
 *
 * Target helpers (not implemented in this tests-first phase):
 * [CameraMotionSnapshot], [CameraMotionStats].
 *
 * Metrics are SDK pose displacement within uninterrupted tracking segments,
 * not physical calibrated movement, parallax readiness, or walkability.
 */
class CameraMotionStatsTest {

    @Test
    fun largeFiniteDisplacementAvoidsIntermediateSquareOverflow() {
        val stats = CameraMotionStats()
        assertEquals("accepted", stats.record(1L, true, 0.0, 0.0, 0.0))
        assertEquals("accepted", stats.record(2L, true, 1e200, 0.0, 0.0))
        assertEquals(1e200, stats.snapshot().maxSegmentDisplacementMeters!!, 1e185)
    }

    private fun snap(stats: CameraMotionStats): CameraMotionSnapshot = stats.snapshot()

    // --- initial / empty ---

    @Test
    fun initialSnapshotIsZeroNullAndNotStopped() {
        val stats = CameraMotionStats()
        val s = snap(stats)

        assertEquals(0, s.trackedSamples)
        assertEquals(0, s.trackingSegments)
        assertNull(s.maxSegmentDisplacementMeters)
        assertFalse(s.capacityReached)
        assertFalse(s.stopped)
        assertEquals(10_000, CameraMotionStats.MAX_TRACKED_SAMPLES)
    }

    // --- tracking displacement ---

    @Test
    fun firstTrackingSampleStartsSegmentWithZeroDisplacement() {
        val stats = CameraMotionStats()
        assertEquals("accepted", stats.record(1_000L, tracking = true, x = 1.0, y = 2.0, z = 3.0))

        val s = snap(stats)
        assertEquals(1, s.trackedSamples)
        assertEquals(1, s.trackingSegments)
        assertEquals(0.0, s.maxSegmentDisplacementMeters!!, 0.0)
        assertFalse(s.capacityReached)
        assertFalse(s.stopped)
    }

    @Test
    fun threeFourTwelveDisplacementIsThirteenMeters() {
        val stats = CameraMotionStats()
        assertEquals("accepted", stats.record(1L, tracking = true, x = 0.0, y = 0.0, z = 0.0))
        assertEquals("accepted", stats.record(2L, tracking = true, x = 3.0, y = 4.0, z = 12.0))

        val s = snap(stats)
        assertEquals(2, s.trackedSamples)
        assertEquals(1, s.trackingSegments)
        assertEquals(13.0, s.maxSegmentDisplacementMeters!!, 0.0)
    }

    @Test
    fun returnToOriginDoesNotAccumulatePathLength() {
        // Path length would be 6+8=14 if steps were summed; max from segment origin is 10.
        val stats = CameraMotionStats()
        assertEquals("accepted", stats.record(10L, tracking = true, x = 0.0, y = 0.0, z = 0.0))
        assertEquals("accepted", stats.record(20L, tracking = true, x = 6.0, y = 8.0, z = 0.0))
        assertEquals("accepted", stats.record(30L, tracking = true, x = 0.0, y = 0.0, z = 0.0))

        val s = snap(stats)
        assertEquals(3, s.trackedSamples)
        assertEquals(1, s.trackingSegments)
        assertEquals(10.0, s.maxSegmentDisplacementMeters!!, 0.0)
    }

    @Test
    fun maxDisplacementIsAggregateAcrossSegmentsNotLastOnly() {
        val stats = CameraMotionStats()
        assertEquals("accepted", stats.record(1L, tracking = true, x = 0.0, y = 0.0, z = 0.0))
        assertEquals("accepted", stats.record(2L, tracking = true, x = 5.0, y = 0.0, z = 0.0))
        assertEquals("not_tracking", stats.record(3L, tracking = false, x = 0.0, y = 0.0, z = 0.0))
        assertEquals("accepted", stats.record(4L, tracking = true, x = 100.0, y = 0.0, z = 0.0))
        assertEquals("accepted", stats.record(5L, tracking = true, x = 102.0, y = 0.0, z = 0.0))

        val s = snap(stats)
        assertEquals(4, s.trackedSamples)
        assertEquals(2, s.trackingSegments)
        // Segment1 max 5.0; segment2 max 2.0 → aggregate max 5.0
        assertEquals(5.0, s.maxSegmentDisplacementMeters!!, 0.0)
    }

    // --- timestamps ---

    @Test
    fun nonPositiveTimestampIsInvalidWithoutMutation() {
        val stats = CameraMotionStats()
        assertEquals("invalid_timestamp", stats.record(0L, tracking = true, x = 0.0, y = 0.0, z = 0.0))
        assertEquals("invalid_timestamp", stats.record(-1L, tracking = true, x = 1.0, y = 0.0, z = 0.0))
        assertEquals("invalid_timestamp", stats.record(0L, tracking = false, x = 9.0, y = 9.0, z = 9.0))

        assertEquals(snap(CameraMotionStats()), snap(stats))
    }

    @Test
    fun duplicateAndRollbackTimestampsAreOutOfOrderWithoutMutation() {
        val stats = CameraMotionStats()
        assertEquals("accepted", stats.record(100L, tracking = true, x = 0.0, y = 0.0, z = 0.0))
        val before = snap(stats)

        assertEquals("out_of_order", stats.record(100L, tracking = true, x = 3.0, y = 0.0, z = 0.0))
        assertEquals("out_of_order", stats.record(99L, tracking = true, x = 3.0, y = 0.0, z = 0.0))
        assertEquals("out_of_order", stats.record(100L, tracking = false, x = 0.0, y = 0.0, z = 0.0))

        assertEquals(before, snap(stats))
        assertEquals(0.0, snap(stats).maxSegmentDisplacementMeters!!, 0.0)
    }

    @Test
    fun rejectedPoseDoesNotAdvanceTimestampCursor() {
        val stats = CameraMotionStats()
        assertEquals("accepted", stats.record(50L, tracking = true, x = 0.0, y = 0.0, z = 0.0))
        val before = snap(stats)

        // Invalid pose at a newer stamp must not consume that stamp.
        assertEquals(
            "invalid_pose",
            stats.record(200L, tracking = true, x = Double.NaN, y = 0.0, z = 0.0),
        )
        assertEquals(before, snap(stats))

        // Stamp 150 is still strictly after last accepted (50).
        assertEquals("accepted", stats.record(150L, tracking = true, x = 3.0, y = 4.0, z = 0.0))
        assertEquals(2, snap(stats).trackedSamples)
        assertEquals(5.0, snap(stats).maxSegmentDisplacementMeters!!, 0.0)
    }

    // --- invalid poses ---

    @Test
    fun nonFiniteCoordinatesAreInvalidPoseWithoutMutation() {
        val stats = CameraMotionStats()
        assertEquals("accepted", stats.record(1L, tracking = true, x = 0.0, y = 0.0, z = 0.0))
        val before = snap(stats)

        assertEquals(
            "invalid_pose",
            stats.record(2L, tracking = true, x = Double.NaN, y = 0.0, z = 0.0),
        )
        assertEquals(
            "invalid_pose",
            stats.record(3L, tracking = true, x = 0.0, y = Double.POSITIVE_INFINITY, z = 0.0),
        )
        assertEquals(
            "invalid_pose",
            stats.record(4L, tracking = true, x = 0.0, y = 0.0, z = Double.NEGATIVE_INFINITY),
        )

        assertEquals(before, snap(stats))
    }

    @Test
    fun nonFiniteOriginDifferenceOrHypotIsInvalidPoseWithoutMutation() {
        val stats = CameraMotionStats()
        // Origin near the double extreme so a modest step overflows the difference/hypot.
        assertEquals(
            "accepted",
            stats.record(1L, tracking = true, x = Double.MAX_VALUE, y = 0.0, z = 0.0),
        )
        val before = snap(stats)

        assertEquals(
            "invalid_pose",
            stats.record(2L, tracking = true, x = -Double.MAX_VALUE, y = 0.0, z = 0.0),
        )
        assertEquals(before, snap(stats))
        assertEquals(1, snap(stats).trackedSamples)
        assertEquals(0.0, snap(stats).maxSegmentDisplacementMeters!!, 0.0)

        // A finite step still works because the rejected stamp did not advance the cursor.
        assertEquals(
            "accepted",
            stats.record(2L, tracking = true, x = Double.MAX_VALUE, y = 0.0, z = 0.0),
        )
        assertEquals(2, snap(stats).trackedSamples)
        assertEquals(0.0, snap(stats).maxSegmentDisplacementMeters!!, 0.0)
    }

    // --- paused / not tracking ---

    @Test
    fun pausedAcceptsTimestampClearsSegmentAndIgnoresCoordinates() {
        val stats = CameraMotionStats()
        assertEquals("accepted", stats.record(1L, tracking = true, x = 0.0, y = 0.0, z = 0.0))
        assertEquals("accepted", stats.record(2L, tracking = true, x = 3.0, y = 0.0, z = 0.0))

        assertEquals(
            "not_tracking",
            stats.record(3L, tracking = false, x = Double.NaN, y = Double.POSITIVE_INFINITY, z = -1.0),
        )

        val afterPause = snap(stats)
        assertEquals(2, afterPause.trackedSamples)
        assertEquals(1, afterPause.trackingSegments)
        assertEquals(3.0, afterPause.maxSegmentDisplacementMeters!!, 0.0)

        // Absolute jump after pause must not create a cross-segment displacement.
        assertEquals("accepted", stats.record(4L, tracking = true, x = 1_000.0, y = 0.0, z = 0.0))
        val afterResume = snap(stats)
        assertEquals(3, afterResume.trackedSamples)
        assertEquals(2, afterResume.trackingSegments)
        assertEquals(3.0, afterResume.maxSegmentDisplacementMeters!!, 0.0)
    }

    @Test
    fun repeatedPausedDoesNotCreateNewSegmentOrTrackedSample() {
        val stats = CameraMotionStats()
        assertEquals("not_tracking", stats.record(1L, tracking = false, x = 1.0, y = 2.0, z = 3.0))
        assertEquals("not_tracking", stats.record(2L, tracking = false, x = 9.0, y = 9.0, z = 9.0))
        assertEquals("not_tracking", stats.record(3L, tracking = false, x = 0.0, y = 0.0, z = 0.0))

        val s = snap(stats)
        assertEquals(0, s.trackedSamples)
        assertEquals(0, s.trackingSegments)
        assertNull(s.maxSegmentDisplacementMeters)
    }

    @Test
    fun pauseThenTrackStartsFreshSegmentAtNewOrigin() {
        val stats = CameraMotionStats()
        assertEquals("accepted", stats.record(1L, tracking = true, x = 0.0, y = 0.0, z = 0.0))
        assertEquals("not_tracking", stats.record(2L, tracking = false, x = 0.0, y = 0.0, z = 0.0))
        assertEquals("accepted", stats.record(3L, tracking = true, x = 10.0, y = 0.0, z = 0.0))
        assertEquals("accepted", stats.record(4L, tracking = true, x = 10.0, y = 0.0, z = 5.0))

        val s = snap(stats)
        assertEquals(3, s.trackedSamples)
        assertEquals(2, s.trackingSegments)
        assertEquals(5.0, s.maxSegmentDisplacementMeters!!, 0.0)
    }

    // --- capacity ---

    @Test
    fun capacityReachedExactlyAtMaxTrackedSamplesAndThenImmutable() {
        val stats = CameraMotionStats()
        val max = CameraMotionStats.MAX_TRACKED_SAMPLES
        assertEquals(10_000, max)

        repeat(max) { index ->
            val ts = (index + 1).toLong()
            assertEquals(
                "accepted",
                stats.record(ts, tracking = true, x = index.toDouble(), y = 0.0, z = 0.0),
            )
        }

        val full = snap(stats)
        assertEquals(max, full.trackedSamples)
        assertEquals(1, full.trackingSegments)
        assertTrue(full.capacityReached)
        // Origin at 0; last sample at max-1 → displacement max-1
        assertEquals((max - 1).toDouble(), full.maxSegmentDisplacementMeters!!, 0.0)

        assertEquals("capacity", stats.record(max + 1L, tracking = true, x = 0.0, y = 0.0, z = 0.0))
        assertEquals("capacity", stats.record(max + 2L, tracking = false, x = 0.0, y = 0.0, z = 0.0))
        assertEquals(
            "capacity",
            stats.record(max + 3L, tracking = true, x = Double.NaN, y = 0.0, z = 0.0),
        )

        assertEquals(full, snap(stats))
    }

    // --- stop ---

    @Test
    fun stopIsTerminalIdempotentAndSnapshotsRemainReadable() {
        val stats = CameraMotionStats()
        assertEquals("accepted", stats.record(1L, tracking = true, x = 0.0, y = 0.0, z = 0.0))
        assertEquals("accepted", stats.record(2L, tracking = true, x = 0.0, y = 3.0, z = 4.0))
        val before = snap(stats)

        stats.stop()
        stats.stop()

        assertTrue(snap(stats).stopped)
        assertEquals("stopped", stats.record(3L, tracking = true, x = 10.0, y = 0.0, z = 0.0))
        assertEquals("stopped", stats.record(4L, tracking = false, x = 0.0, y = 0.0, z = 0.0))

        val after = snap(stats)
        assertEquals(before.trackedSamples, after.trackedSamples)
        assertEquals(before.trackingSegments, after.trackingSegments)
        assertEquals(before.maxSegmentDisplacementMeters, after.maxSegmentDisplacementMeters)
        assertEquals(before.capacityReached, after.capacityReached)
        assertTrue(after.stopped)
        assertEquals(5.0, after.maxSegmentDisplacementMeters!!, 0.0)
    }

    @Test
    fun stopAfterCapacityStillRejectedAndIdempotent() {
        val stats = CameraMotionStats()
        val max = CameraMotionStats.MAX_TRACKED_SAMPLES
        repeat(max) { index ->
            assertEquals(
                "accepted",
                stats.record((index + 1).toLong(), tracking = true, x = 0.0, y = 0.0, z = 0.0),
            )
        }
        assertTrue(snap(stats).capacityReached)
        assertEquals("capacity", stats.record(max + 1L, tracking = true, x = 1.0, y = 0.0, z = 0.0))

        stats.stop()
        stats.stop()
        assertEquals("stopped", stats.record(max + 2L, tracking = true, x = 2.0, y = 0.0, z = 0.0))

        val s = snap(stats)
        assertEquals(max, s.trackedSamples)
        assertTrue(s.capacityReached)
        assertTrue(s.stopped)
        assertEquals(0.0, s.maxSegmentDisplacementMeters!!, 0.0)
    }

    @Test
    fun snapshotIsImmutableAfterLaterSuccessfulRecords() {
        val stats = CameraMotionStats()
        assertEquals("accepted", stats.record(1L, tracking = true, x = 0.0, y = 0.0, z = 0.0))
        val first = snap(stats)

        assertEquals("accepted", stats.record(2L, tracking = true, x = 3.0, y = 4.0, z = 12.0))
        val second = snap(stats)

        assertEquals(1, first.trackedSamples)
        assertEquals(1, first.trackingSegments)
        assertEquals(0.0, first.maxSegmentDisplacementMeters!!, 0.0)
        assertFalse(first.capacityReached)
        assertFalse(first.stopped)

        assertEquals(2, second.trackedSamples)
        assertEquals(1, second.trackingSegments)
        assertEquals(13.0, second.maxSegmentDisplacementMeters!!, 0.0)
    }
}
