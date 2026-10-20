package com.triguide.ai.diagnostics

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

/** Strided raw-depth sampling for a diagnostic projection, not coverage or clearance. */
class RawDepthGridTest {
    private fun grid(
        width: Int = 2, height: Int = 2,
        depth: ByteBuffer = ByteBuffer.wrap(byteArrayOf(0, 1, 0, 2, 0, 3, -1, -1)),
        confidence: ByteBuffer = ByteBuffer.wrap(byteArrayOf(-1, -128, 127, -1)),
        depthRow: Int = width * 2, depthPixel: Int = 2,
        confidenceRow: Int = width, confidencePixel: Int = 1,
        size: Int = 3, threshold: Int = 128,
    ) = RawDepthFrameStats.sampleConfidentGrid(
        width, height, depth, depthRow, depthPixel,
        confidence, confidenceRow, confidencePixel, size, threshold,
    )

    @Test fun fullUint16AndUnsignedConfidenceNoDuplicateTinyGrid() {
        val samples = grid()
        assertEquals(listOf(RawDepthPixel(0, 0, 256), RawDepthPixel(1, 0, 512), RawDepthPixel(1, 1, 65535)), samples)
    }

    @Test fun zeroDepthNeverProjectsEvenWithMaximumConfidence() {
        assertTrue(grid(1, 1, ByteBuffer.wrap(byteArrayOf(0, 0)), ByteBuffer.wrap(byteArrayOf(-1))).isEmpty())
    }

    @Test fun centerOnlyAndDeterministicEndpoints() {
        val depth = ByteBuffer.allocate(50)
        for (i in 0 until 25) { depth.put(i * 2, (i + 1).toByte()) }
        val confidence = ByteBuffer.wrap(ByteArray(25) { -1 })
        assertEquals(listOf(RawDepthPixel(2, 2, 13)), grid(5, 5, depth, confidence, size = 1))
        val points = grid(5, 5, depth, confidence)
        assertEquals(9, points.size)
        assertEquals(RawDepthPixel(0, 0, 1), points.first())
        assertEquals(RawDepthPixel(4, 4, 25), points.last())
    }

    @Test fun stridesPositionsLimitOrderAndBytesArePreserved() {
        val depth = ByteBuffer.wrap(byteArrayOf(99, 1, 0, 9, 2, 0, 9, 8, 3, 0, 9, -1, -1, 9, 77))
        depth.position(1); depth.limit(13); depth.order(ByteOrder.BIG_ENDIAN)
        val confidence = ByteBuffer.wrap(byteArrayOf(8, -1, 7, -1, 6, 9, -1, 7, -1, 5))
        confidence.position(1); confidence.limit(9)
        val beforeD = depth.array().copyOf(); val beforeC = confidence.array().copyOf()
        assertEquals(listOf(RawDepthPixel(0, 0, 1), RawDepthPixel(1, 0, 2), RawDepthPixel(0, 1, 3), RawDepthPixel(1, 1, 65535)),
            grid(depth = depth, confidence = confidence, depthRow = 7, depthPixel = 3, confidenceRow = 5, confidencePixel = 2))
        assertEquals(1, depth.position()); assertEquals(13, depth.limit()); assertEquals(ByteOrder.BIG_ENDIAN, depth.order())
        assertEquals(1, confidence.position()); assertEquals(9, confidence.limit())
        assertArrayEquals(beforeD, depth.array()); assertArrayEquals(beforeC, confidence.array())
    }

    @Test fun malformedAndOversizedInputsAreRejectedBeforeSampling() {
        assertThrows(IllegalArgumentException::class.java) { grid(depthRow = 3) }
        assertThrows(IllegalArgumentException::class.java) { grid(depthPixel = 1) }
        assertThrows(IllegalArgumentException::class.java) { grid(confidenceRow = 1) }
        assertThrows(IllegalArgumentException::class.java) { grid(depth = ByteBuffer.allocate(7)) }
        assertThrows(IllegalArgumentException::class.java) { grid(confidence = ByteBuffer.allocate(3)) }
        assertThrows(IllegalArgumentException::class.java) { grid(width = 0) }
        assertThrows(IllegalArgumentException::class.java) { grid(width = Int.MAX_VALUE, height = Int.MAX_VALUE) }
    }

    @Test fun gridAndThresholdBoundsEnforced() {
        for (size in listOf(0, -1, 9, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { grid(size = size) }
        for (threshold in listOf(-1, 256)) assertThrows(IllegalArgumentException::class.java) { grid(threshold = threshold) }
        assertEquals(4, grid(threshold = 0).size)
        assertEquals(2, grid(threshold = 255).size)
    }

    @Test fun maximumGridIsBoundedTo64WithoutMutatingDirectBuffers() {
        val depth = ByteBuffer.allocateDirect(20000)
        for (i in 0 until 10000) depth.put(i * 2, 1)
        val confidence = ByteBuffer.allocateDirect(10000)
        for (i in 0 until 10000) confidence.put(i, -1)
        val samples = grid(100, 100, depth, confidence, size = 8)
        assertEquals(64, samples.size)
        assertEquals(RawDepthPixel(99, 99, 1), samples.last())
        assertEquals(0, depth.position()); assertEquals(0, confidence.position())
    }
}
