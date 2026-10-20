package com.triguide.ai.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * RED contract for bounded pure-CPU session-plane relative-height profiling.
 * Primitive fixtures only — no ARCore / SDK mocks.
 *
 * Target helpers (not implemented in this tests-first phase):
 * [SurfaceHeightCandidate], [SurfaceHeightSnapshot], [SurfaceHeightProfile].
 * Reuses existing [SurfaceKind].
 *
 * Diagnostic only — signed cameraY-planeY range is not floor selection,
 * camera height, walkability, or navigation authority.
 */
class SurfaceHeightProfileTest {

    private fun candidate(
        kind: SurfaceKind = SurfaceKind.HORIZONTAL_UPWARD,
        centerWorldY: Double = 0.0,
        tracking: Boolean = true,
        subsumed: Boolean = false,
    ): SurfaceHeightCandidate =
        SurfaceHeightCandidate(
            kind = kind,
            centerWorldY = centerWorldY,
            tracking = tracking,
            subsumed = subsumed,
        )

    private fun summarize(
        candidates: Iterable<SurfaceHeightCandidate>,
        cameraTracking: Boolean = true,
        cameraWorldY: Double = 1.5,
    ): SurfaceHeightSnapshot =
        SurfaceHeightProfile.summarize(
            candidates = candidates,
            cameraTracking = cameraTracking,
            cameraWorldY = cameraWorldY,
        )

    private fun assertEmptySnapshot(
        snapshot: SurfaceHeightSnapshot,
        status: String,
    ) {
        assertEquals(status, snapshot.status)
        assertEquals(0, snapshot.inspected)
        assertEquals(0, snapshot.eligibleUpward)
        assertEquals(0, snapshot.ignored)
        assertEquals(0, snapshot.invalidHeight)
        assertNull(snapshot.minCameraAboveMeters)
        assertNull(snapshot.maxCameraAboveMeters)
        assertFalse(snapshot.truncated)
    }

    // --- gates: not_tracking / invalid_camera / empty ---

    @Test
    fun emptyWhileTrackingIsObservedWithNullRange() {
        val snapshot = summarize(emptyList(), cameraTracking = true, cameraWorldY = 1.2)
        assertEmptySnapshot(snapshot, status = "observed")
    }

    @Test
    fun cameraNotTrackingReturnsNotTrackingWithoutReadingIterableOrCameraY() {
        val iterable = CountingIterable(
            listOf(candidate(centerWorldY = 0.0)),
        )
        val snapshot = summarize(
            iterable,
            cameraTracking = false,
            cameraWorldY = Double.NaN,
        )
        assertEmptySnapshot(snapshot, status = "not_tracking")
        assertEquals(0, iterable.iteratorCalls)
        assertEquals(0, iterable.nextCalls)
        assertEquals(0, iterable.hasNextCalls)
    }

    @Test
    fun cameraNotTrackingDoesNotTouchThrowingIterable() {
        val exploding = object : Iterable<SurfaceHeightCandidate> {
            override fun iterator(): Iterator<SurfaceHeightCandidate> {
                fail("camera not tracking must not iterate candidates")
                throw AssertionError("unreachable")
            }
        }
        val snapshot = summarize(exploding, cameraTracking = false, cameraWorldY = 9.0)
        assertEmptySnapshot(snapshot, status = "not_tracking")
    }

    @Test
    fun nonFiniteCameraYWhileTrackingIsInvalidCameraWithoutIteration() {
        val exploding = object : Iterable<SurfaceHeightCandidate> {
            override fun iterator(): Iterator<SurfaceHeightCandidate> {
                fail("invalid cameraY must not iterate candidates")
                throw AssertionError("unreachable")
            }
        }
        for (badY in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val snapshot = summarize(exploding, cameraTracking = true, cameraWorldY = badY)
            assertEmptySnapshot(snapshot, status = "invalid_camera")
        }
    }

    // --- signed ranges / huge finite / overflow / NaN centers ---

    @Test
    fun positiveCameraAboveRangeUsesSignedDifferences() {
        // camera 2.0 above planes at 0.5 and 1.0 → diffs 1.5 and 1.0
        val snapshot = summarize(
            listOf(
                candidate(centerWorldY = 0.5),
                candidate(centerWorldY = 1.0),
            ),
            cameraWorldY = 2.0,
        )
        assertEquals("observed", snapshot.status)
        assertEquals(2, snapshot.inspected)
        assertEquals(2, snapshot.eligibleUpward)
        assertEquals(0, snapshot.ignored)
        assertEquals(0, snapshot.invalidHeight)
        assertEquals(1.0, snapshot.minCameraAboveMeters!!, 0.0)
        assertEquals(1.5, snapshot.maxCameraAboveMeters!!, 0.0)
        assertFalse(snapshot.truncated)
    }

    @Test
    fun negativeRangeWhenPlaneIsAboveCamera() {
        // plane at 3.0, camera at 1.0 → signed diff -2.0 (plane above camera)
        val snapshot = summarize(
            listOf(candidate(centerWorldY = 3.0)),
            cameraWorldY = 1.0,
        )
        assertEquals("observed", snapshot.status)
        assertEquals(1, snapshot.eligibleUpward)
        assertEquals(-2.0, snapshot.minCameraAboveMeters!!, 0.0)
        assertEquals(-2.0, snapshot.maxCameraAboveMeters!!, 0.0)
    }

    @Test
    fun mixedPositiveAndNegativeHeightsTrackFiniteMinMax() {
        val snapshot = summarize(
            listOf(
                candidate(centerWorldY = -1.0), // camera 0.5 → +1.5
                candidate(centerWorldY = 2.0), // camera 0.5 → -1.5
                candidate(centerWorldY = 0.0), // camera 0.5 → +0.5
            ),
            cameraWorldY = 0.5,
        )
        assertEquals(3, snapshot.eligibleUpward)
        assertEquals(-1.5, snapshot.minCameraAboveMeters!!, 0.0)
        assertEquals(1.5, snapshot.maxCameraAboveMeters!!, 0.0)
        assertEquals(3, snapshot.inspected)
        assertEquals(0, snapshot.ignored)
        assertEquals(0, snapshot.invalidHeight)
    }

    @Test
    fun finiteHugeDifferencesRemainEligible() {
        val snapshot = summarize(
            listOf(candidate(centerWorldY = 0.0)),
            cameraWorldY = 1e200,
        )
        assertEquals("observed", snapshot.status)
        assertEquals(1, snapshot.eligibleUpward)
        assertEquals(1e200, snapshot.minCameraAboveMeters!!, 1e185)
        assertEquals(1e200, snapshot.maxCameraAboveMeters!!, 1e185)
        assertEquals(0, snapshot.invalidHeight)
    }

    @Test
    fun overflowDifferenceCountsAsInvalidHeightNotEligible() {
        val snapshot = summarize(
            listOf(candidate(centerWorldY = -Double.MAX_VALUE)),
            cameraWorldY = Double.MAX_VALUE,
        )
        assertEquals("observed", snapshot.status)
        assertEquals(1, snapshot.inspected)
        assertEquals(0, snapshot.eligibleUpward)
        assertEquals(0, snapshot.ignored)
        assertEquals(1, snapshot.invalidHeight)
        assertNull(snapshot.minCameraAboveMeters)
        assertNull(snapshot.maxCameraAboveMeters)
    }

    @Test
    fun eligibleNonFiniteCenterWorldYIsInvalidHeight() {
        val snapshot = summarize(
            listOf(
                candidate(centerWorldY = Double.NaN),
                candidate(centerWorldY = Double.POSITIVE_INFINITY),
                candidate(centerWorldY = Double.NEGATIVE_INFINITY),
                candidate(centerWorldY = 0.25),
            ),
            cameraWorldY = 1.0,
        )
        assertEquals("observed", snapshot.status)
        assertEquals(4, snapshot.inspected)
        assertEquals(1, snapshot.eligibleUpward)
        assertEquals(0, snapshot.ignored)
        assertEquals(3, snapshot.invalidHeight)
        assertEquals(0.75, snapshot.minCameraAboveMeters!!, 0.0)
        assertEquals(0.75, snapshot.maxCameraAboveMeters!!, 0.0)
    }

    // --- ignored precedence (invalid Y on non-eligible never invalidHeight) ---

    @Test
    fun nonEligibleCandidatesAreIgnoredEvenWithInvalidCenterY() {
        val snapshot = summarize(
            listOf(
                candidate(
                    kind = SurfaceKind.VERTICAL_SURFACE,
                    centerWorldY = Double.NaN,
                    tracking = true,
                    subsumed = false,
                ),
                candidate(
                    kind = SurfaceKind.HORIZONTAL_UPWARD,
                    centerWorldY = Double.POSITIVE_INFINITY,
                    tracking = false,
                    subsumed = false,
                ),
                candidate(
                    kind = SurfaceKind.HORIZONTAL_UPWARD,
                    centerWorldY = Double.NEGATIVE_INFINITY,
                    tracking = true,
                    subsumed = true,
                ),
                candidate(
                    kind = SurfaceKind.HORIZONTAL_DOWNWARD,
                    centerWorldY = 0.0,
                    tracking = true,
                    subsumed = false,
                ),
                candidate(
                    kind = SurfaceKind.UNKNOWN,
                    centerWorldY = 1.0,
                    tracking = true,
                    subsumed = false,
                ),
                candidate(centerWorldY = 0.0), // sole eligible
            ),
            cameraWorldY = 1.5,
        )
        assertEquals("observed", snapshot.status)
        assertEquals(6, snapshot.inspected)
        assertEquals(1, snapshot.eligibleUpward)
        assertEquals(5, snapshot.ignored)
        assertEquals(0, snapshot.invalidHeight)
        assertEquals(1.5, snapshot.minCameraAboveMeters!!, 0.0)
        assertEquals(1.5, snapshot.maxCameraAboveMeters!!, 0.0)
        assertEquals(
            snapshot.ignored + snapshot.invalidHeight + snapshot.eligibleUpward,
            snapshot.inspected,
        )
    }

    // --- MAX_PLANES / lazy truncation ---

    @Test
    fun maxPlanesConstantIsTwoHundredFiftySix() {
        assertEquals(256, SurfaceHeightProfile.MAX_PLANES)
    }

    @Test
    fun exactlyMaxPlanesIsObservedNotTruncated() {
        val planes = List(SurfaceHeightProfile.MAX_PLANES) { index ->
            candidate(centerWorldY = index * 0.01)
        }
        val cameraY = 5.0
        val snapshot = summarize(planes, cameraWorldY = cameraY)
        assertEquals("observed", snapshot.status)
        assertFalse(snapshot.truncated)
        assertEquals(SurfaceHeightProfile.MAX_PLANES, snapshot.inspected)
        assertEquals(SurfaceHeightProfile.MAX_PLANES, snapshot.eligibleUpward)
        assertEquals(0, snapshot.ignored)
        assertEquals(0, snapshot.invalidHeight)
        val expectedMin = cameraY - ((SurfaceHeightProfile.MAX_PLANES - 1) * 0.01)
        val expectedMax = cameraY - 0.0
        assertEquals(expectedMin, snapshot.minCameraAboveMeters!!, 1e-9)
        assertEquals(expectedMax, snapshot.maxCameraAboveMeters!!, 1e-9)
    }

    @Test
    fun lazyUnboundedIterableConsumesAtMostMaxPlanesAndNeverNextBeyond() {
        var nextCalls = 0
        var hasNextCalls = 0
        val lazy = object : Iterable<SurfaceHeightCandidate> {
            override fun iterator(): Iterator<SurfaceHeightCandidate> =
                object : Iterator<SurfaceHeightCandidate> {
                    private var produced = 0

                    override fun hasNext(): Boolean {
                        hasNextCalls++
                        return true
                    }

                    override fun next(): SurfaceHeightCandidate {
                        nextCalls++
                        produced++
                        if (produced > SurfaceHeightProfile.MAX_PLANES) {
                            fail("must not read beyond MAX_PLANES; peek via hasNext only")
                        }
                        return candidate(centerWorldY = 0.0)
                    }
                }
        }
        val snapshot = summarize(lazy, cameraWorldY = 1.0)
        assertEquals("observed", snapshot.status)
        assertTrue(snapshot.truncated)
        assertEquals(SurfaceHeightProfile.MAX_PLANES, snapshot.inspected)
        assertEquals(SurfaceHeightProfile.MAX_PLANES, snapshot.eligibleUpward)
        assertEquals(SurfaceHeightProfile.MAX_PLANES, nextCalls)
        assertTrue(
            "expected at least one hasNext peek after the bound, got $hasNextCalls",
            hasNextCalls >= SurfaceHeightProfile.MAX_PLANES + 1,
        )
        assertEquals(1.0, snapshot.minCameraAboveMeters!!, 0.0)
        assertEquals(1.0, snapshot.maxCameraAboveMeters!!, 0.0)
    }

    @Test
    fun truncatedDoesNotConsumeTheExtraCandidateViaNext() {
        val counting = CountingIterable(
            List(SurfaceHeightProfile.MAX_PLANES + 1) {
                candidate(centerWorldY = -0.5)
            },
        )
        val snapshot = summarize(counting, cameraWorldY = 0.0)
        assertEquals("observed", snapshot.status)
        assertTrue(snapshot.truncated)
        assertEquals(SurfaceHeightProfile.MAX_PLANES, snapshot.inspected)
        assertEquals(SurfaceHeightProfile.MAX_PLANES, counting.nextCalls)
        assertTrue(counting.hasNextCalls >= 1)
        assertEquals(0.5, snapshot.minCameraAboveMeters!!, 0.0)
        assertEquals(0.5, snapshot.maxCameraAboveMeters!!, 0.0)
    }

    // --- permutation / repeat / inspected identity ---

    @Test
    fun permutationPreservesCountsAndMinMax() {
        val a = candidate(kind = SurfaceKind.VERTICAL_SURFACE, centerWorldY = 9.0)
        val b = candidate(centerWorldY = 0.0)
        val c = candidate(centerWorldY = 2.0)
        val d = candidate(centerWorldY = Double.NaN) // eligible kind → invalidHeight
        val e = candidate(
            kind = SurfaceKind.HORIZONTAL_UPWARD,
            centerWorldY = 1.0,
            subsumed = true,
        )
        val orders = listOf(
            listOf(a, b, c, d, e),
            listOf(e, d, c, b, a),
            listOf(c, a, e, b, d),
            listOf(d, b, e, a, c),
        )
        val snapshots = orders.map { summarize(it, cameraWorldY = 1.0) }
        for (snapshot in snapshots) {
            assertEquals(snapshots.first(), snapshot)
            assertEquals("observed", snapshot.status)
            assertEquals(5, snapshot.inspected)
            assertEquals(2, snapshot.eligibleUpward)
            assertEquals(2, snapshot.ignored)
            assertEquals(1, snapshot.invalidHeight)
            assertEquals(-1.0, snapshot.minCameraAboveMeters!!, 0.0) // 1.0 - 2.0
            assertEquals(1.0, snapshot.maxCameraAboveMeters!!, 0.0) // 1.0 - 0.0
            assertEquals(
                snapshot.ignored + snapshot.invalidHeight + snapshot.eligibleUpward,
                snapshot.inspected,
            )
        }
    }

    @Test
    fun repeatedSummarizeIsStatelessAndEqual() {
        val planes = listOf(
            candidate(centerWorldY = -0.25),
            candidate(kind = SurfaceKind.HORIZONTAL_DOWNWARD, centerWorldY = 3.0),
            candidate(centerWorldY = Double.POSITIVE_INFINITY),
            candidate(centerWorldY = 4.0),
        )
        val first = summarize(planes, cameraWorldY = 1.0)
        val second = summarize(planes, cameraWorldY = 1.0)
        assertEquals(first, second)
        assertEquals("observed", first.status)
        assertEquals(4, first.inspected)
        assertEquals(2, first.eligibleUpward)
        assertEquals(1, first.ignored)
        assertEquals(1, first.invalidHeight)
        assertEquals(-3.0, first.minCameraAboveMeters!!, 0.0) // 1 - 4
        assertEquals(1.25, first.maxCameraAboveMeters!!, 0.0) // 1 - (-0.25)
        assertFalse(first.truncated)
    }

    @Test
    fun snapshotFieldsNeverImplyFloorCameraHeightOrClearance() {
        val snapshots = listOf(
            summarize(emptyList(), cameraTracking = true, cameraWorldY = 1.0),
            summarize(emptyList(), cameraTracking = false, cameraWorldY = 1.0),
            summarize(emptyList(), cameraTracking = true, cameraWorldY = Double.NaN),
            summarize(
                listOf(candidate(centerWorldY = 0.0)),
                cameraTracking = true,
                cameraWorldY = 1.5,
            ),
            summarize(
                List(SurfaceHeightProfile.MAX_PLANES + 1) {
                    candidate(centerWorldY = 0.0)
                },
                cameraWorldY = 1.0,
            ),
        )
        for (snapshot in snapshots) {
            val text = buildString {
                append(snapshot.status)
                append(snapshot.inspected)
                append(snapshot.eligibleUpward)
                append(snapshot.ignored)
                append(snapshot.invalidHeight)
                append(snapshot.minCameraAboveMeters)
                append(snapshot.maxCameraAboveMeters)
                append(snapshot.truncated)
            }
            assertFalse(text.contains("floor", ignoreCase = true))
            assertFalse(text.contains("wall", ignoreCase = true))
            assertFalse(text.contains("clear", ignoreCase = true))
            assertFalse(text.contains("walkable", ignoreCase = true))
            assertFalse(text.contains("safe", ignoreCase = true))
            assertFalse(text.contains("camera_height", ignoreCase = true))
        }
        assertEquals("observed", snapshots[0].status)
        assertEquals("not_tracking", snapshots[1].status)
        assertEquals("invalid_camera", snapshots[2].status)
        assertEquals(1, snapshots[3].eligibleUpward)
        assertTrue(snapshots[4].truncated)
        assertEquals("observed", snapshots[4].status)
    }

    /**
     * Counts iterator / next / hasNext usage so not-tracking and bound tests
     * can prove no iteration or at-most-MAX consumption without SDK objects.
     */
    private class CountingIterable(
        private val items: List<SurfaceHeightCandidate>,
    ) : Iterable<SurfaceHeightCandidate> {
        var iteratorCalls: Int = 0
            private set
        var nextCalls: Int = 0
            private set
        var hasNextCalls: Int = 0
            private set

        override fun iterator(): Iterator<SurfaceHeightCandidate> {
            iteratorCalls++
            val inner = items.iterator()
            return object : Iterator<SurfaceHeightCandidate> {
                override fun hasNext(): Boolean {
                    hasNextCalls++
                    return inner.hasNext()
                }

                override fun next(): SurfaceHeightCandidate {
                    nextCalls++
                    return inner.next()
                }
            }
        }
    }
}
