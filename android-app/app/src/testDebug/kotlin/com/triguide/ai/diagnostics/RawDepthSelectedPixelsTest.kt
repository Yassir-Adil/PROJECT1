package com.triguide.ai.diagnostics

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class RawDepthSelectedPixelsTest {
    private fun sample(points: List<Pair<Int,Int>>, depth: ByteBuffer = ByteBuffer.wrap(byteArrayOf(1,0,2,0,3,0,4,0)),
        confidence: ByteBuffer = ByteBuffer.wrap(byteArrayOf(-1,0,-128,-1))) =
        RawDepthFrameStats.sampleConfidentPixels(2, 2, depth, 4, 2, confidence, 2, 1, points)

    @Test fun selectedSdkMappedPixelsReuseUnsignedDepthAndConfidence() {
        assertEquals(listOf(RawDepthPixel(1,1,4), RawDepthPixel(0,1,3)), sample(listOf(1 to 1, 1 to 0, 0 to 1)))
    }
    @Test fun invalidCoordinatesRejectedRatherThanClamped() {
        for (p in listOf(-1 to 0, 2 to 0, 0 to 2)) {
            assertThrows(IllegalArgumentException::class.java) { sample(listOf(p)) }
        }
    }
    @Test fun boundedEvenWithDuplicatesAndEmptyAllowed() {
        assertEquals(emptyList<RawDepthPixel>(), sample(emptyList()))
        assertThrows(IllegalArgumentException::class.java) { sample(List(65) { 0 to 0 }) }
    }
    @Test fun malformedPlaneFailsBeforeReadingAndStateIsUnchanged() {
        assertThrows(IllegalArgumentException::class.java) { sample(listOf(0 to 0), ByteBuffer.allocate(7)) }
        val d = ByteBuffer.wrap(byteArrayOf(77,1,0,2,0,3,0,4,0,88)).apply { position(1); limit(9) }
        assertEquals(listOf(RawDepthPixel(0,0,1)), sample(listOf(0 to 0), d))
        assertEquals(1, d.position()); assertEquals(9, d.limit())
    }
}
