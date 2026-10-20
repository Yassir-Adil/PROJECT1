package com.triguide.ai.diagnostics

import org.junit.Assert.*
import org.junit.Test

class SemanticImageOrientationTest {
    @Test fun sdkAxesWithCropOffsetsStillIdentifyFourRotations() {
        assertEquals(0, SemanticImageOrientation.fromViewAxes(floatArrayOf(-.2f,-.1f,1.2f,-.1f,-.2f,1.1f)))
        assertEquals(1, SemanticImageOrientation.fromViewAxes(floatArrayOf(1f,0f,1f,2f,-1f,0f)))
        assertEquals(2, SemanticImageOrientation.fromViewAxes(floatArrayOf(1f,1f,0f,1f,1f,0f)))
        assertEquals(3, SemanticImageOrientation.fromViewAxes(floatArrayOf(0f,1f,0f,0f,1f,1f)))
    }
    @Test fun mirroredSkewedDegenerateNonfiniteOrMissingTransformsAreUnknown() {
        for (a in listOf(floatArrayOf(0f,0f,-1f,0f,0f,1f), floatArrayOf(0f,0f,1f,.1f,0f,1f),
            FloatArray(6), FloatArray(6) { Float.NaN }, FloatArray(5))) assertNull(SemanticImageOrientation.fromViewAxes(a))
    }
    @Test fun integerCpuPixelRotationKeepsEdgesInsideWithoutClampingBadUv() {
        // CPU4x2 pixel(0,0) rotated clockwise becomes upright2x4 pixel(1,0).
        assertEquals(OrientedSemanticPixel(2,4,1,0), SemanticImageOrientation.pixel(4,2,0.0,0.0,1))
        assertEquals(OrientedSemanticPixel(4,2,3,1), SemanticImageOrientation.pixel(4,2,0.0,0.0,2))
        assertEquals(OrientedSemanticPixel(2,4,0,3), SemanticImageOrientation.pixel(4,2,0.0,0.0,3))
        assertEquals(OrientedSemanticPixel(4,2,3,1), SemanticImageOrientation.pixel(4,2,.99,.99,0))
        for (turn in 0..3) {
            assertNull(SemanticImageOrientation.pixel(4,2,1.0,0.0,turn))
            assertNull(SemanticImageOrientation.pixel(4,2,Double.NaN,0.0,turn))
        }
        assertNull(SemanticImageOrientation.pixel(4,2,0.0,0.0,4))
    }
    @Test fun everyPixelRotatesBijectivelyForOddAndEvenImages() {
        for ((w,h) in listOf(4 to 2, 3 to 5)) for (turn in 0..3) {
            val seen = mutableSetOf<OrientedSemanticPixel>()
            for (y in 0 until h) for (x in 0 until w) {
                val p = SemanticImageOrientation.pixel(w,h,(x+.1)/w,(y+.1)/h,turn)!!
                assertTrue(p.x in 0 until p.width && p.y in 0 until p.height)
                seen += p
            }
            assertEquals(w*h,seen.size)
        }
    }
}
