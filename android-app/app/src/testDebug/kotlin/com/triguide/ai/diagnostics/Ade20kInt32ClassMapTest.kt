package com.triguide.ai.diagnostics

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class Ade20kInt32ClassMapTest {
    @Test
    fun preservesLittleEndianBoundaryIdsAndDoesNotConsumeCallerBuffer() {
        val buffer = classMapBuffer()
        buffer.putInt(0, 31)
        buffer.putInt(Int.SIZE_BYTES, 4)
        val decoded = Ade20kInt32ClassMap.decode(buffer)
        assertEquals(31, decoded[0])
        assertEquals(4, decoded[1])
        assertEquals(0, decoded.last())
        assertEquals(0, buffer.position())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTruncatedTensor() {
        Ade20kInt32ClassMap.decode(ByteBuffer.allocate(4))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownPositiveClassId() {
        Ade20kInt32ClassMap.decode(classMapBuffer().apply { putInt(0, 32) })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeClassId() {
        Ade20kInt32ClassMap.decode(classMapBuffer().apply { putInt(0, -1) })
    }

    private fun classMapBuffer() =
        ByteBuffer.allocate(Ade20kInt32ClassMap.PIXEL_COUNT * Int.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
}
