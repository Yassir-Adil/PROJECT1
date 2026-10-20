package com.triguide.ai.diagnostics

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

/** Host/JVM cost only, not a Redmi or sensor benchmark. No timing pass threshold. */
class Yuv420RgbEquivalenceTest {
    @Test fun heapSlicesMustUseArrayOffsetPlusCurrentPosition() {
        fun plane(bytes: ByteArray, stride: Int): YuvRgbPlane {
            val original = ByteBuffer.wrap(byteArrayOf(33, 44) + bytes + byteArrayOf(55)).apply {
                position(1); limit(2 + bytes.size)
            }
            val slice = original.slice().apply { position(1) }
            return YuvRgbPlane(slice, stride, 1)
        }
        assertArrayEquals(intArrayOf(0xff000000.toInt(), 0xffffffff.toInt()), Yuv420Rgb.convert(2, 1,
            plane(byteArrayOf(16, 235.toByte()), 2), plane(byteArrayOf(128.toByte()), 1), plane(byteArrayOf(128.toByte()), 1)))
    }
    @Test fun heapFastPathMustEqualReadOnlyDirectAtCameraSize() {
        val width = 640; val height = 480
        val y = ByteArray(width * height) { (16 + (it % 220)).toByte() }
        val u = ByteArray(width * height / 4) { (it % 256).toByte() }
        val v = ByteArray(width * height / 4) { (255 - it % 256).toByte() }
        fun heap(bytes: ByteArray, stride: Int) = YuvRgbPlane(ByteBuffer.wrap(bytes), stride, 1)
        fun direct(bytes: ByteArray, stride: Int): YuvRgbPlane {
            val b = ByteBuffer.allocateDirect(bytes.size).apply { put(bytes); rewind() }.asReadOnlyBuffer()
            return YuvRgbPlane(b, stride, 1)
        }
        val yp = heap(y, width); val up = heap(u, width / 2); val vp = heap(v, width / 2)
        val reference = Yuv420Rgb.convert(width, height, direct(y, width), direct(u, width / 2), direct(v, width / 2))
        repeat(10) { Yuv420Rgb.convert(width, height, yp, up, vp) }
        val samples = LongArray(20) {
            val start = System.nanoTime()
            val actual = Yuv420Rgb.convert(width, height, yp, up, vp)
            val elapsed = System.nanoTime() - start
            assertArrayEquals(reference, actual)
            elapsed
        }.sorted()
        println("YUV_HOST_BENCH width=$width height=$height iterations=20 warmups=10 " +
            "p50_ms=${samples[9] / 1_000_000.0} p90_ms=${samples[17] / 1_000_000.0} " +
            "scope=host_jvm_only not_phone_performance=true")
        assertEquals(0, yp.buffer.position()); assertEquals(0, up.buffer.position()); assertEquals(0, vp.buffer.position())
    }
}
