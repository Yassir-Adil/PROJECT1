package com.triguide.ai.diagnostics

import org.junit.Assert.*
import org.junit.Test

class SemanticGroundReasonTest {
    @Test fun malformedReasonsPreserveInvalidStateAndUnknownHeights() {
        assertEquals("invalid_cell_count",SemanticGroundDiagnostic.evaluate(emptyList()).reason)
        val duplicate=cells().toMutableList().apply { this[0]=this[1] }
        assertEquals("invalid_cell_structure",SemanticGroundDiagnostic.evaluate(duplicate).reason)
        val partial=cells().toMutableList().apply { this[0]=this[0].copy(worldX=1.0) }
        val reading=SemanticGroundDiagnostic.evaluate(partial)
        assertEquals("partial_or_nonfinite_geometry",reading.reason)
        assertEquals("invalid",reading.status); assertEquals(64,reading.unknownSamples)
    }
    @Test fun finiteCoordinatesWithOverflowingSpanAreNotCalledMerelySparse() {
        val reading=SemanticGroundDiagnostic.evaluate(cells({x,r->
            Triple(if(x==3)-Double.MAX_VALUE else Double.MAX_VALUE,0.0,(7-r)*.3)}))
        assertEquals("nonfinite_xz_span",reading.reason)
        assertNull(reading.referenceYMeters)
    }
    private fun cells(points: (Int,Int) -> Triple<Double,Double,Double>? = {_,_->null}, floor: Boolean = true) =
        List(64) { i ->
            val x=i%8; val r=i/8; val p=if(x in 3..4 && r in 5..7) points(x,r) else null
            SemanticDepthCell(x,r,if(floor && x in 3..4 && r in 5..7)4 else 0,p?.first,p?.second,p?.third)
        }
    @Test fun absentFloorAndSparseGeometryAreDifferentReasons() {
        assertEquals("no_bottom_floor_component", SemanticGroundDiagnostic.evaluate(cells(floor=false)).reason)
        assertEquals("too_few_geometry_samples", SemanticGroundDiagnostic.evaluate(cells()).reason)
    }
    @Test fun sixSamplesDoNotHideSpanFailureBehindSampleCount() {
        val reading=SemanticGroundDiagnostic.evaluate(cells({x,r->Triple((x-3)*.05,0.0,(7-r)*.05)}))
        assertEquals(6,reading.supportCount); assertEquals("insufficient_xz_span",reading.reason)
        assertNull(reading.referenceYMeters); assertEquals(64,reading.unknownSamples)
    }
    @Test fun areaAndNonHorizontalHeightHaveSeparateReasons() {
        val line=SemanticGroundDiagnostic.evaluate(cells({x,r->val d=(x-3+r-5)*.3;Triple(d,0.0,d)}))
        assertEquals("collinear_or_small_area",line.reason)
        val step=SemanticGroundDiagnostic.evaluate(cells({x,r->Triple((x-3)*.3,if(r==5).3 else 0.0,(7-r)*.3)}))
        assertEquals("non_horizontal_floor_samples",step.reason)
    }
    @Test fun candidateReasonStillStatesSingleFrameOnly() {
        val reading=SemanticGroundDiagnostic.evaluate(cells({x,r->Triple((x-3)*.3,0.0,(7-r)*.3)}))
        assertEquals("single_frame_floor_like_only",reading.reason)
        assertEquals("floor_like_candidate",reading.status)
    }
}
