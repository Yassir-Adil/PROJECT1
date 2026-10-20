package com.triguide.ai.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED contract for pure-CPU surface probe selection and viewport rays.
 * Primitive fixtures only — no ARCore / SDK mocks.
 *
 * Target helpers (not implemented in this tests-first phase):
 * [SurfaceKind], [SurfaceHitCandidate], [SurfaceProbeReading],
 * [SurfaceProbePolicy], [SurfaceProbeRay], [SurfaceProbeLayout].
 */
class SurfaceProbePolicyTest {

    private fun candidate(
        kind: SurfaceKind = SurfaceKind.VERTICAL_SURFACE,
        distanceMeters: Float = 1.0f,
        tracking: Boolean = true,
        subsumed: Boolean = false,
        insidePolygon: Boolean = true,
    ): SurfaceHitCandidate =
        SurfaceHitCandidate(
            kind = kind,
            distanceMeters = distanceMeters,
            tracking = tracking,
            subsumed = subsumed,
            insidePolygon = insidePolygon,
        )

    private fun select(
        candidates: List<SurfaceHitCandidate>,
        tracking: Boolean = true,
    ): SurfaceProbeReading =
        SurfaceProbePolicy.select(candidates = candidates, tracking = tracking)

    // --- session tracking gate ---

    @Test
    fun sessionNotTrackingReturnsUnknownRegardlessOfHits() {
        val hits = listOf(
            candidate(kind = SurfaceKind.VERTICAL_SURFACE, distanceMeters = 0.5f),
            candidate(kind = SurfaceKind.HORIZONTAL_UPWARD, distanceMeters = 1.2f),
        )
        val reading = select(hits, tracking = false)
        assertEquals(SurfaceKind.UNKNOWN, reading.kind)
        assertNull(reading.distanceMeters)
        assertEquals("not_tracking", reading.reason)
    }

    @Test
    fun sessionNotTrackingWinsOverTooManyHits() {
        val hits = List(65) { candidate(distanceMeters = 1f + it * 0.01f) }
        val reading = select(hits, tracking = false)
        assertEquals(SurfaceKind.UNKNOWN, reading.kind)
        assertNull(reading.distanceMeters)
        assertEquals("not_tracking", reading.reason)
    }

    // --- plane / candidate filtering ---

    @Test
    fun candidateNotTrackingIsFiltered() {
        val reading = select(
            listOf(
                candidate(tracking = false, distanceMeters = 0.4f),
                candidate(kind = SurfaceKind.HORIZONTAL_DOWNWARD, distanceMeters = 2.0f),
            ),
        )
        assertEquals(SurfaceKind.HORIZONTAL_DOWNWARD, reading.kind)
        assertEquals(2.0f, reading.distanceMeters)
        assertEquals("observed_surface", reading.reason)
    }

    @Test
    fun subsumedPlaneIsFiltered() {
        val reading = select(
            listOf(
                candidate(subsumed = true, distanceMeters = 0.3f),
                candidate(kind = SurfaceKind.VERTICAL_SURFACE, distanceMeters = 1.5f),
            ),
        )
        assertEquals(SurfaceKind.VERTICAL_SURFACE, reading.kind)
        assertEquals(1.5f, reading.distanceMeters)
        assertEquals("observed_surface", reading.reason)
    }

    @Test
    fun outsidePolygonHitIsFiltered() {
        val reading = select(
            listOf(
                candidate(insidePolygon = false, distanceMeters = 0.2f),
                candidate(kind = SurfaceKind.HORIZONTAL_UPWARD, distanceMeters = 0.9f),
            ),
        )
        assertEquals(SurfaceKind.HORIZONTAL_UPWARD, reading.kind)
        assertEquals(0.9f, reading.distanceMeters)
        assertEquals("observed_surface", reading.reason)
    }

    @Test
    fun unknownKindIsFiltered() {
        val reading = select(
            listOf(
                candidate(kind = SurfaceKind.UNKNOWN, distanceMeters = 0.1f),
                candidate(kind = SurfaceKind.VERTICAL_SURFACE, distanceMeters = 3.0f),
            ),
        )
        assertEquals(SurfaceKind.VERTICAL_SURFACE, reading.kind)
        assertEquals(3.0f, reading.distanceMeters)
        assertEquals("observed_surface", reading.reason)
    }

    @Test
    fun nonFiniteZeroAndNegativeDistancesAreFiltered() {
        val reading = select(
            listOf(
                candidate(distanceMeters = Float.NaN),
                candidate(distanceMeters = Float.POSITIVE_INFINITY),
                candidate(distanceMeters = Float.NEGATIVE_INFINITY),
                candidate(distanceMeters = 0f),
                candidate(distanceMeters = -0.5f),
                candidate(kind = SurfaceKind.HORIZONTAL_DOWNWARD, distanceMeters = 1.25f),
            ),
        )
        assertEquals(SurfaceKind.HORIZONTAL_DOWNWARD, reading.kind)
        assertEquals(1.25f, reading.distanceMeters)
        assertEquals("observed_surface", reading.reason)
    }

    @Test
    fun sparseMixOfInvalidPlanesYieldsNoValidSurfaceWhenNoneRemain() {
        val reading = select(
            listOf(
                candidate(kind = SurfaceKind.UNKNOWN, distanceMeters = 1f),
                candidate(tracking = false, distanceMeters = 1f),
                candidate(subsumed = true, distanceMeters = 1f),
                candidate(insidePolygon = false, distanceMeters = 1f),
                candidate(distanceMeters = Float.NaN),
                candidate(distanceMeters = 0f),
                candidate(distanceMeters = -1f),
            ),
        )
        assertEquals(SurfaceKind.UNKNOWN, reading.kind)
        assertNull(reading.distanceMeters)
        assertEquals("no_valid_surface", reading.reason)
    }

    @Test
    fun emptyCandidatesYieldNoValidSurface() {
        val reading = select(emptyList())
        assertEquals(SurfaceKind.UNKNOWN, reading.kind)
        assertNull(reading.distanceMeters)
        assertEquals("no_valid_surface", reading.reason)
    }

    // --- nearest selection / bounds ---

    @Test
    fun nearestValidDistanceIsSelectedIndependentOfInputOrder() {
        val near = candidate(kind = SurfaceKind.VERTICAL_SURFACE, distanceMeters = 0.75f)
        val mid = candidate(kind = SurfaceKind.HORIZONTAL_UPWARD, distanceMeters = 1.5f)
        val far = candidate(kind = SurfaceKind.HORIZONTAL_DOWNWARD, distanceMeters = 4.0f)

        val farFirst = select(listOf(far, mid, near))
        assertEquals(SurfaceKind.VERTICAL_SURFACE, farFirst.kind)
        assertEquals(0.75f, farFirst.distanceMeters)
        assertEquals("observed_surface", farFirst.reason)

        val nearFirst = select(listOf(near, far, mid))
        assertEquals(SurfaceKind.VERTICAL_SURFACE, nearFirst.kind)
        assertEquals(0.75f, nearFirst.distanceMeters)

        val midFirst = select(listOf(mid, near, far))
        assertEquals(SurfaceKind.VERTICAL_SURFACE, midFirst.kind)
        assertEquals(0.75f, midFirst.distanceMeters)
    }

    @Test
    fun invalidCloserHitsDoNotBeatFartherValidCandidate() {
        val reading = select(
            listOf(
                candidate(subsumed = true, distanceMeters = 0.1f),
                candidate(insidePolygon = false, distanceMeters = 0.2f),
                candidate(kind = SurfaceKind.UNKNOWN, distanceMeters = 0.3f),
                candidate(distanceMeters = Float.POSITIVE_INFINITY),
                candidate(kind = SurfaceKind.HORIZONTAL_UPWARD, distanceMeters = 2.5f),
            ),
        )
        assertEquals(SurfaceKind.HORIZONTAL_UPWARD, reading.kind)
        assertEquals(2.5f, reading.distanceMeters)
        assertEquals("observed_surface", reading.reason)
    }

    @Test
    fun exactlySixtyFourCandidatesAreAcceptedWhenValid() {
        val hits = List(64) { index ->
            candidate(
                kind = SurfaceKind.VERTICAL_SURFACE,
                distanceMeters = 10f - index * 0.01f,
            )
        }
        val reading = select(hits)
        assertEquals(SurfaceKind.VERTICAL_SURFACE, reading.kind)
        assertEquals(10f - 63 * 0.01f, reading.distanceMeters)
        assertEquals("observed_surface", reading.reason)
    }

    @Test
    fun moreThanSixtyFourCandidatesReturnTooManyHits() {
        val hits = List(65) { candidate(distanceMeters = 1f + it * 0.01f) }
        val reading = select(hits)
        assertEquals(SurfaceKind.UNKNOWN, reading.kind)
        assertNull(reading.distanceMeters)
        assertEquals("too_many_hits", reading.reason)
    }

    // --- semantic honesty ---

    @Test
    fun horizontalUpwardIsObservedSurfaceNotInferredFloor() {
        val reading = select(
            listOf(candidate(kind = SurfaceKind.HORIZONTAL_UPWARD, distanceMeters = 1.1f)),
        )
        assertEquals(SurfaceKind.HORIZONTAL_UPWARD, reading.kind)
        assertEquals(1.1f, reading.distanceMeters)
        assertEquals("observed_surface", reading.reason)

        val text = "${reading.kind}${reading.reason}"
        assertFalse(text.contains("floor", ignoreCase = true))
        assertFalse(text.contains("clear", ignoreCase = true))
        assertFalse(text.contains("walkable", ignoreCase = true))
    }

    @Test
    fun verticalSurfaceIsObservedSurfaceNotConfirmedWall() {
        val reading = select(
            listOf(candidate(kind = SurfaceKind.VERTICAL_SURFACE, distanceMeters = 0.8f)),
        )
        assertEquals(SurfaceKind.VERTICAL_SURFACE, reading.kind)
        assertEquals("observed_surface", reading.reason)
        val text = "${reading.kind}${reading.reason}"
        assertFalse(text.contains("wall", ignoreCase = true))
        assertFalse(text.contains("clear", ignoreCase = true))
    }

    @Test
    fun surfaceKindEnumExposesExactContractValues() {
        assertEquals(
            listOf(
                SurfaceKind.UNKNOWN,
                SurfaceKind.VERTICAL_SURFACE,
                SurfaceKind.HORIZONTAL_UPWARD,
                SurfaceKind.HORIZONTAL_DOWNWARD,
            ),
            SurfaceKind.entries.toList(),
        )
    }

    // --- viewport rays ---

    @Test
    fun invalidViewportReturnsEmptyRayList() {
        assertTrue(SurfaceProbeLayout.points(width = 0, height = 720).isEmpty())
        assertTrue(SurfaceProbeLayout.points(width = 1280, height = 0).isEmpty())
        assertTrue(SurfaceProbeLayout.points(width = -1, height = 720).isEmpty())
        assertTrue(SurfaceProbeLayout.points(width = 1280, height = -8).isEmpty())
        assertTrue(SurfaceProbeLayout.points(width = 0, height = 0).isEmpty())
    }

    @Test
    fun pointsReturnExactlyFourNamedViewPixelRays() {
        val width = 200
        val height = 400
        val rays = SurfaceProbeLayout.points(width = width, height = height)
        assertEquals(4, rays.size)

        assertEquals(
            listOf("center", "lower_left", "lower_center", "lower_right"),
            rays.map(SurfaceProbeRay::name),
        )

        assertEquals(SurfaceProbeRay("center", 0.5f * width, 0.5f * height), rays[0])
        assertEquals(SurfaceProbeRay("lower_left", 0.25f * width, 0.8f * height), rays[1])
        assertEquals(SurfaceProbeRay("lower_center", 0.5f * width, 0.8f * height), rays[2])
        assertEquals(SurfaceProbeRay("lower_right", 0.75f * width, 0.8f * height), rays[3])
    }

    @Test
    fun viewportMappingUsesViewPixelsNotNormalizedFractionsAsCoordinates() {
        val rays = SurfaceProbeLayout.points(width = 1920, height = 1080)
        assertEquals(4, rays.size)
        assertEquals(960f, rays[0].xPx)
        assertEquals(540f, rays[0].yPx)
        assertEquals(480f, rays[1].xPx)
        assertEquals(864f, rays[1].yPx)
        assertEquals(960f, rays[2].xPx)
        assertEquals(864f, rays[2].yPx)
        assertEquals(1440f, rays[3].xPx)
        assertEquals(864f, rays[3].yPx)
        rays.forEach { ray ->
            assertTrue(ray.xPx > 1f || ray.yPx > 1f)
        }
    }
}
