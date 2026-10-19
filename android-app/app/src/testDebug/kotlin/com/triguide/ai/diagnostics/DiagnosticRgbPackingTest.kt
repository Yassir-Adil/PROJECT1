package com.triguide.ai.diagnostics

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class DiagnosticRgbPackingTest {
    @Test fun preservesChannelOrderAndUnsignedBoundaryCodes() {
        val source = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0x80807FFF.toInt())
        val rgb = ByteArray(12)
        DiagnosticRgbPacking.pack(source, rgb)
        assertArrayEquals(byteArrayOf(-1, 0, 0, 0, -1, 0, 0, 0, -1, -128, 127, -1), rgb)
    }
    @Test fun bulkBufferMatchesPreviousScalarPathOnDeterministicPixels() {
        val pixels = IntArray(4096) { (it * 1103515245 + 12345) }
        val expected = ByteBuffer.allocate(pixels.size * 3)
        pixels.forEach { expected.put(((it ushr 16) and 255).toByte()).put(((it ushr 8) and 255).toByte()).put((it and 255).toByte()) }
        val packed = ByteArray(pixels.size * 3)
        DiagnosticRgbPacking.pack(pixels, packed)
        assertArrayEquals(expected.array(), ByteBuffer.allocate(packed.size).put(packed).array())
    }
    @Test fun supportsReusedDestinationWithoutPreviousFrameContamination() {
        val packed = ByteArray(3)
        DiagnosticRgbPacking.pack(intArrayOf(-1), packed)
        DiagnosticRgbPacking.pack(intArrayOf(0), packed)
        assertArrayEquals(ByteArray(3), packed)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsWrongDestinationLength() {
        DiagnosticRgbPacking.pack(intArrayOf(0), ByteArray(4))
    }
}
