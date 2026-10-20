package com.triguide.ai.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * RED contract for bounded pure-CPU SDK plane inventory summarization.
 * Primitive fixtures only — no ARCore / SDK mocks.
 *
 * Target helpers (not implemented in this tests-first phase):
 * [SurfacePlaneTracking], [SurfacePlaneCandidate], [SurfaceInventorySnapshot],
 * [SurfaceInventoryPolicy]. Reuses existing [SurfaceKind].
 */
class SurfaceInventoryPolicyTest {

    private fun candidate(
        kind: SurfaceKind = SurfaceKind.VERTICAL_SURFACE,
        tracking: SurfacePlaneTracking = SurfacePlaneTracking.TRACKING,
        subsumed: Boolean = false,
    ): SurfacePlaneCandidate =
        SurfacePlaneCandidate(
            kind = kind,
            tracking = tracking,
            subsumed = subsumed,
        )

    private fun summarize(
        candidates: Iterable<SurfacePlaneCandidate>,
        cameraTracking: Boolean = true,
    ): SurfaceInventorySnapshot =
        SurfaceInventoryPolicy.summarize(
            candidates = candidates,
            cameraTracking = cameraTracking,
        )

    private fun assertAllZeroCounts(snapshot: SurfaceInventorySnapshot) {
        assertEquals(0, snapshot.inspected)
        assertEquals(false, snapshot.truncated)
        assertEquals(0, snapshot.trackingUpward)
        assertEquals(0, snapshot.trackingVertical)
        assertEquals(0, snapshot.trackingDownward)
        assertEquals(0, snapshot.paused)
        assertEquals(0, snapshot.stopped)
        assertEquals(0, snapshot.subsumed)
        assertEquals(0, snapshot.unknownKind)
    }

    // --- camera tracking gate / empty ---

    @Test
    fun emptyWhileTrackingIsObservedWithZeroCounts() {
        val snapshot = summarize(emptyList(), cameraTracking = true)
        assertEquals("observed", snapshot.status)
        assertAllZeroCounts(snapshot)
    }

    @Test
    fun cameraNotTrackingReturnsNotTrackingAndZerosWithoutIteration() {
        val iterable = CountingIterable(
            listOf(
                candidate(kind = SurfaceKind.HORIZONTAL_UPWARD),
                candidate(kind = SurfaceKind.VERTICAL_SURFACE),
            ),
        )
        val snapshot = summarize(iterable, cameraTracking = false)
        assertEquals("not_tracking", snapshot.status)
        assertAllZeroCounts(snapshot)
        assertEquals(0, iterable.iteratorCalls)
        assertEquals(0, iterable.nextCalls)
        assertEquals(0, iterable.hasNextCalls)
    }

    @Test
    fun cameraNotTrackingDoesNotTouchThrowingIterable() {
        val exploding = object : Iterable<SurfacePlaneCandidate> {
            override fun iterator(): Iterator<SurfacePlaneCandidate> {
                fail("camera not tracking must not iterate candidates")
                throw AssertionError("unreachable")
            }
        }
        val snapshot = summarize(exploding, cameraTracking = false)
        assertEquals("not_tracking", snapshot.status)
        assertAllZeroCounts(snapshot)
    }

    // --- precedence: subsumed > paused/stopped > orientation ---

    @Test
    fun mixedPrecedenceSubsumedExcludedBeforePausedStoppedAndOrientation() {
        val snapshot = summarize(
            listOf(
                candidate(
                    kind = SurfaceKind.HORIZONTAL_UPWARD,
                    tracking = SurfacePlaneTracking.TRACKING,
                    subsumed = true,
                ),
                candidate(
                    kind = SurfaceKind.VERTICAL_SURFACE,
                    tracking = SurfacePlaneTracking.PAUSED,
                    subsumed = false,
                ),
                candidate(
                    kind = SurfaceKind.HORIZONTAL_DOWNWARD,
                    tracking = SurfacePlaneTracking.STOPPED,
                    subsumed = false,
                ),
                candidate(
                    kind = SurfaceKind.VERTICAL_SURFACE,
                    tracking = SurfacePlaneTracking.TRACKING,
                    subsumed = false,
                ),
                candidate(
                    kind = SurfaceKind.HORIZONTAL_UPWARD,
                    tracking = SurfacePlaneTracking.TRACKING,
                    subsumed = false,
                ),
                candidate(
                    kind = SurfaceKind.HORIZONTAL_DOWNWARD,
                    tracking = SurfacePlaneTracking.TRACKING,
                    subsumed = false,
                ),
            ),
        )
        assertEquals("observed", snapshot.status)
        assertFalse(snapshot.truncated)
        assertEquals(6, snapshot.inspected)
        assertEquals(1, snapshot.subsumed)
        assertEquals(1, snapshot.paused)
        assertEquals(1, snapshot.stopped)
        assertEquals(1, snapshot.trackingUpward)
        assertEquals(1, snapshot.trackingVertical)
        assertEquals(1, snapshot.trackingDownward)
        assertEquals(0, snapshot.unknownKind)
    }

    @Test
    fun subsumedPausedOrStoppedStillCountOnlyAsSubsumed() {
        val snapshot = summarize(
            listOf(
                candidate(
                    kind = SurfaceKind.HORIZONTAL_UPWARD,
                    tracking = SurfacePlaneTracking.PAUSED,
                    subsumed = true,
                ),
                candidate(
                    kind = SurfaceKind.VERTICAL_SURFACE,
                    tracking = SurfacePlaneTracking.STOPPED,
                    subsumed = true,
                ),
                candidate(
                    kind = SurfaceKind.UNKNOWN,
                    tracking = SurfacePlaneTracking.TRACKING,
                    subsumed = true,
                ),
            ),
        )
        assertEquals("observed", snapshot.status)
        assertEquals(3, snapshot.inspected)
        assertEquals(3, snapshot.subsumed)
        assertEquals(0, snapshot.paused)
        assertEquals(0, snapshot.stopped)
        assertEquals(0, snapshot.trackingUpward)
        assertEquals(0, snapshot.trackingVertical)
        assertEquals(0, snapshot.trackingDownward)
        assertEquals(0, snapshot.unknownKind)
    }

    @Test
    fun pausedAndStoppedAreExcludedFromOrientationCounts() {
        val snapshot = summarize(
            listOf(
                candidate(
                    kind = SurfaceKind.HORIZONTAL_UPWARD,
                    tracking = SurfacePlaneTracking.PAUSED,
                ),
                candidate(
                    kind = SurfaceKind.VERTICAL_SURFACE,
                    tracking = SurfacePlaneTracking.STOPPED,
                ),
                candidate(
                    kind = SurfaceKind.HORIZONTAL_DOWNWARD,
                    tracking = SurfacePlaneTracking.PAUSED,
                ),
            ),
        )
        assertEquals("observed", snapshot.status)
        assertEquals(3, snapshot.inspected)
        assertEquals(2, snapshot.paused)
        assertEquals(1, snapshot.stopped)
        assertEquals(0, snapshot.trackingUpward)
        assertEquals(0, snapshot.trackingVertical)
        assertEquals(0, snapshot.trackingDownward)
        assertEquals(0, snapshot.unknownKind)
        assertEquals(0, snapshot.subsumed)
    }

    // --- unknown kind ---

    @Test
    fun trackingUnknownKindIncrementsUnknownNotOrientation() {
        val snapshot = summarize(
            listOf(
                candidate(kind = SurfaceKind.UNKNOWN, tracking = SurfacePlaneTracking.TRACKING),
                candidate(
                    kind = SurfaceKind.VERTICAL_SURFACE,
                    tracking = SurfacePlaneTracking.TRACKING,
                ),
                candidate(kind = SurfaceKind.UNKNOWN, tracking = SurfacePlaneTracking.TRACKING),
            ),
        )
        assertEquals("observed", snapshot.status)
        assertEquals(3, snapshot.inspected)
        assertEquals(2, snapshot.unknownKind)
        assertEquals(0, snapshot.trackingUpward)
        assertEquals(1, snapshot.trackingVertical)
        assertEquals(0, snapshot.trackingDownward)
        assertEquals(0, snapshot.paused)
        assertEquals(0, snapshot.stopped)
        assertEquals(0, snapshot.subsumed)
    }

    @Test
    fun pausedOrStoppedUnknownDoesNotIncrementUnknownKind() {
        val snapshot = summarize(
            listOf(
                candidate(kind = SurfaceKind.UNKNOWN, tracking = SurfacePlaneTracking.PAUSED),
                candidate(kind = SurfaceKind.UNKNOWN, tracking = SurfacePlaneTracking.STOPPED),
            ),
        )
        assertEquals("observed", snapshot.status)
        assertEquals(2, snapshot.inspected)
        assertEquals(1, snapshot.paused)
        assertEquals(1, snapshot.stopped)
        assertEquals(0, snapshot.unknownKind)
        assertEquals(0, snapshot.trackingUpward)
        assertEquals(0, snapshot.trackingVertical)
        assertEquals(0, snapshot.trackingDownward)
    }

    // --- MAX_PLANES exactly / over / lazy bound ---

    @Test
    fun maxPlanesConstantIsTwoHundredFiftySix() {
        assertEquals(256, SurfaceInventoryPolicy.MAX_PLANES)
    }

    @Test
    fun exactlyMaxPlanesIsObservedNotTruncated() {
        val planes = List(SurfaceInventoryPolicy.MAX_PLANES) { index ->
            when (index % 3) {
                0 -> candidate(kind = SurfaceKind.HORIZONTAL_UPWARD)
                1 -> candidate(kind = SurfaceKind.VERTICAL_SURFACE)
                else -> candidate(kind = SurfaceKind.HORIZONTAL_DOWNWARD)
            }
        }
        val snapshot = summarize(planes)
        assertEquals("observed", snapshot.status)
        assertFalse(snapshot.truncated)
        assertEquals(SurfaceInventoryPolicy.MAX_PLANES, snapshot.inspected)
        val expectedUp = (0 until SurfaceInventoryPolicy.MAX_PLANES).count { it % 3 == 0 }
        val expectedVertical = (0 until SurfaceInventoryPolicy.MAX_PLANES).count { it % 3 == 1 }
        val expectedDown = (0 until SurfaceInventoryPolicy.MAX_PLANES).count { it % 3 == 2 }
        assertEquals(expectedUp, snapshot.trackingUpward)
        assertEquals(expectedVertical, snapshot.trackingVertical)
        assertEquals(expectedDown, snapshot.trackingDownward)
        assertEquals(0, snapshot.paused)
        assertEquals(0, snapshot.stopped)
        assertEquals(0, snapshot.subsumed)
        assertEquals(0, snapshot.unknownKind)
    }

    @Test
    fun moreThanMaxPlanesIsBoundedTruncatedAndInspectsOnlyMax() {
        val planes = List(SurfaceInventoryPolicy.MAX_PLANES + 3) { index ->
            candidate(kind = SurfaceKind.VERTICAL_SURFACE)
        }
        val snapshot = summarize(planes)
        assertEquals("bounded", snapshot.status)
        assertTrue(snapshot.truncated)
        assertEquals(SurfaceInventoryPolicy.MAX_PLANES, snapshot.inspected)
        assertEquals(SurfaceInventoryPolicy.MAX_PLANES, snapshot.trackingVertical)
        assertEquals(0, snapshot.trackingUpward)
        assertEquals(0, snapshot.trackingDownward)
        assertEquals(0, snapshot.paused)
        assertEquals(0, snapshot.stopped)
        assertEquals(0, snapshot.subsumed)
        assertEquals(0, snapshot.unknownKind)
    }

    @Test
    fun lazyUnboundedIterableConsumesAtMostMaxPlanesAndPeeksOneExtra() {
        var nextCalls = 0
        var hasNextCalls = 0
        val lazy = object : Iterable<SurfacePlaneCandidate> {
            override fun iterator(): Iterator<SurfacePlaneCandidate> =
                object : Iterator<SurfacePlaneCandidate> {
                    private var produced = 0

                    override fun hasNext(): Boolean {
                        hasNextCalls++
                        // Unbounded supply: always more available.
                        return true
                    }

                    override fun next(): SurfacePlaneCandidate {
                        nextCalls++
                        produced++
                        if (produced > SurfaceInventoryPolicy.MAX_PLANES) {
                            fail("must not read beyond MAX_PLANES; peek via hasNext only")
                        }
                        return candidate(kind = SurfaceKind.HORIZONTAL_UPWARD)
                    }
                }
        }
        val snapshot = summarize(lazy)
        assertEquals("bounded", snapshot.status)
        assertTrue(snapshot.truncated)
        assertEquals(SurfaceInventoryPolicy.MAX_PLANES, snapshot.inspected)
        assertEquals(SurfaceInventoryPolicy.MAX_PLANES, snapshot.trackingUpward)
        assertEquals(SurfaceInventoryPolicy.MAX_PLANES, nextCalls)
        assertTrue(
            "expected at least one hasNext peek after the bound, got $hasNextCalls",
            hasNextCalls >= SurfaceInventoryPolicy.MAX_PLANES + 1,
        )
    }

    @Test
    fun truncatedDoesNotConsumeTheExtraCandidateViaNext() {
        val counting = CountingIterable(
            List(SurfaceInventoryPolicy.MAX_PLANES + 1) {
                candidate(kind = SurfaceKind.VERTICAL_SURFACE)
            },
        )
        val snapshot = summarize(counting)
        assertEquals("bounded", snapshot.status)
        assertTrue(snapshot.truncated)
        assertEquals(SurfaceInventoryPolicy.MAX_PLANES, snapshot.inspected)
        assertEquals(SurfaceInventoryPolicy.MAX_PLANES, counting.nextCalls)
        assertTrue(counting.hasNextCalls >= 1)
    }

    // --- deterministic counts ---

    @Test
    fun deterministicCountsAreStableAcrossRepeatedSummarize() {
        val planes = listOf(
            candidate(kind = SurfaceKind.HORIZONTAL_UPWARD),
            candidate(kind = SurfaceKind.VERTICAL_SURFACE),
            candidate(kind = SurfaceKind.HORIZONTAL_DOWNWARD),
            candidate(kind = SurfaceKind.UNKNOWN),
            candidate(
                kind = SurfaceKind.VERTICAL_SURFACE,
                tracking = SurfacePlaneTracking.PAUSED,
            ),
            candidate(
                kind = SurfaceKind.HORIZONTAL_UPWARD,
                tracking = SurfacePlaneTracking.STOPPED,
            ),
            candidate(
                kind = SurfaceKind.HORIZONTAL_DOWNWARD,
                subsumed = true,
            ),
        )
        val first = summarize(planes)
        val second = summarize(planes)
        assertEquals(first, second)
        assertEquals("observed", first.status)
        assertEquals(7, first.inspected)
        assertEquals(1, first.trackingUpward)
        assertEquals(1, first.trackingVertical)
        assertEquals(1, first.trackingDownward)
        assertEquals(1, first.unknownKind)
        assertEquals(1, first.paused)
        assertEquals(1, first.stopped)
        assertEquals(1, first.subsumed)
        assertFalse(first.truncated)
    }

    @Test
    fun surfacePlaneTrackingEnumExposesExactContractValues() {
        assertEquals(
            listOf(
                SurfacePlaneTracking.TRACKING,
                SurfacePlaneTracking.PAUSED,
                SurfacePlaneTracking.STOPPED,
            ),
            SurfacePlaneTracking.entries.toList(),
        )
    }

    // --- diagnostic-only limitations (no floor / wall / clearance) ---

    @Test
    fun snapshotStatusNeverImpliesFloorWallOrClearance() {
        val snapshots = listOf(
            summarize(emptyList(), cameraTracking = true),
            summarize(emptyList(), cameraTracking = false),
            summarize(
                listOf(candidate(kind = SurfaceKind.HORIZONTAL_UPWARD)),
                cameraTracking = true,
            ),
            summarize(
                listOf(candidate(kind = SurfaceKind.VERTICAL_SURFACE)),
                cameraTracking = true,
            ),
            summarize(
                List(SurfaceInventoryPolicy.MAX_PLANES + 1) {
                    candidate(kind = SurfaceKind.HORIZONTAL_UPWARD)
                },
            ),
        )
        for (snapshot in snapshots) {
            val text = buildString {
                append(snapshot.status)
                append(snapshot.inspected)
                append(snapshot.truncated)
                append(snapshot.trackingUpward)
                append(snapshot.trackingVertical)
                append(snapshot.trackingDownward)
                append(snapshot.paused)
                append(snapshot.stopped)
                append(snapshot.subsumed)
                append(snapshot.unknownKind)
            }
            assertFalse(text.contains("floor", ignoreCase = true))
            assertFalse(text.contains("wall", ignoreCase = true))
            assertFalse(text.contains("clear", ignoreCase = true))
            assertFalse(text.contains("walkable", ignoreCase = true))
            assertFalse(text.contains("safe", ignoreCase = true))
        }
        assertEquals("observed", snapshots[2].status)
        assertEquals(1, snapshots[2].trackingUpward)
        assertEquals(1, snapshots[3].trackingVertical)
        assertEquals("bounded", snapshots[4].status)
    }

    /**
     * Counts iterator / next / hasNext usage so not-tracking and bound tests
     * can prove no iteration or at-most-MAX consumption without SDK objects.
     */
    private class CountingIterable(
        private val items: List<SurfacePlaneCandidate>,
    ) : Iterable<SurfacePlaneCandidate> {
        var iteratorCalls: Int = 0
            private set
        var nextCalls: Int = 0
            private set
        var hasNextCalls: Int = 0
            private set

        override fun iterator(): Iterator<SurfacePlaneCandidate> {
            iteratorCalls++
            val inner = items.iterator()
            return object : Iterator<SurfacePlaneCandidate> {
                override fun hasNext(): Boolean {
                    hasNextCalls++
                    return inner.hasNext()
                }

                override fun next(): SurfacePlaneCandidate {
                    nextCalls++
                    return inner.next()
                }
            }
        }
    }
}
