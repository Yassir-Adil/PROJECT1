package com.triguide.ai.diagnostics

import org.junit.Assert.*
import org.junit.Test

class SemanticFusionMappingTest {
    @Test fun landscapeLetterboxMapsIntoContentNotPadding() {
        assertEquals(64 * 512, SemanticFusionMapping.modelIndex(640, 480, 0.0, 0.0))
        assertEquals(256 * 512 + 256, SemanticFusionMapping.modelIndex(640, 480, 0.5, 0.5))
        assertEquals(447 * 512 + 511, SemanticFusionMapping.modelIndex(640, 480, 0.999999, 0.999999))
    }
    @Test fun portraitLetterboxHasHorizontalPadding() {
        assertEquals(64, SemanticFusionMapping.modelIndex(480, 640, 0.0, 0.0))
        assertEquals(511 * 512 + 447, SemanticFusionMapping.modelIndex(480, 640, 0.999999, 0.999999))
    }
    @Test fun noGuessingOutsideOrOnNormalizedExclusiveBoundary() {
        for (v in listOf(-0.01, 1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(SemanticFusionMapping.modelIndex(640, 480, v, 0.5))
            assertNull(SemanticFusionMapping.modelIndex(640, 480, 0.5, v))
        }
        assertNull(SemanticFusionMapping.modelIndex(0, 480, 0.5, 0.5))
        assertNull(SemanticFusionMapping.modelIndex(Int.MAX_VALUE, Int.MAX_VALUE, 0.5, 0.5))
    }
    @Test fun depthPixelSnapsButNeverClampsInvalidSdkCoordinates() {
        assertEquals(Pair(80, 45), SemanticFusionMapping.depthPixel(160, 90, 0.5, 0.5))
        assertEquals(Pair(159, 89), SemanticFusionMapping.depthPixel(160, 90, 0.999999, 0.999999))
        assertNull(SemanticFusionMapping.depthPixel(160, 90, 1.0, 0.5))
        assertNull(SemanticFusionMapping.depthPixel(160, 90, 0.5, -0.001))
    }
    @Test fun transformedCoordinatesCanEncodeRotationAndCropWithoutAspectAssumption() {
        // SDK maps a portrait view point into a different CPU image coordinate.
        // The mapper consumes that actual transformed result, not view x/y.
        assertEquals(160 * 512 + 384, SemanticFusionMapping.modelIndex(640, 480, 0.75, 0.25))
    }
    @Test fun displayFreshnessIncludesProcessingNotJustCompletionAge() {
        assertTrue(SemanticFusionMapping.fresh(100, 350, true, true, 250))
        assertFalse(SemanticFusionMapping.fresh(100, 351, true, true, 250))
        assertFalse(SemanticFusionMapping.fresh(100, 99, true, true, 250))
        assertFalse(SemanticFusionMapping.fresh(100, 101, false, true, 250))
        assertFalse(SemanticFusionMapping.fresh(100, 101, true, false, 250))
        assertFalse(SemanticFusionMapping.fresh(-1, 0, true, true, 250))
    }

    @Test fun trackingLossGeometryAndStopInvalidateInFlightPublication() {
        val state = SemanticFusionPublication()
        val initial = state.revision()
        assertFalse(state.canPublish(initial, 100, 101))
        state.trackingChanged(true)
        val tracked = state.revision()
        assertTrue(state.canPublish(tracked, 100, 101))
        assertFalse(state.canPublish(initial, 100, 101))
        state.invalidateGeometry()
        assertFalse(state.canPublish(tracked, 100, 101))
        val rotated = state.revision()
        state.trackingChanged(false)
        state.trackingChanged(true)
        assertFalse(state.canPublish(rotated, 100, 101))
        val recovered = state.revision()
        assertFalse(state.canPublish(recovered, 100, 250_000_101))
        state.stop()
        state.trackingChanged(true)
        assertFalse(state.canPublish(state.revision(), 100, 101))
    }
}
