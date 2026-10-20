package com.triguide.ai.diagnostics

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for bounded YUV_420_888 → ARGB8888 conversion (limited-range BT.601).
 *
 * Helpers: [YuvRgbPlane], [Yuv420Rgb.convert].
 *
 * Pure JVM: no Android / Camera / Image / network. Diagnostic RGB only — never
 * clearance, walkability, or navigation authority.
 *
 * Conversion (per pixel, integer):
 *   C=max(0,Y-16), D=U-128, E=V-128
 *   R=(298*C+409*E+128)>>8, G=(298*C-100*D-208*E+128)>>8, B=(298*C+516*D+128)>>8
 *   clamp 0..255, A=255. Full unrotated image, row-major IntArray.
 */
class Yuv420RgbTest {

    // Hand-computed ARGB8888 literals (0xAARRGGBB). Do not derive via the SUT.
    private companion object {
        const val BLACK = 0xFF000000.toInt() // Y=16,U=128,V=128 → C=0,D=0,E=0
        const val WHITE = 0xFFFFFFFF.toInt() // Y=235,U=128,V=128 → C=219 → R=G=B=255
        const val RED = 0xFFFF0000.toInt() // Y=81,U=90,V=240 → R=255,G=0,B=0
        /** Y=128,U=90,V=240 → R=255,G=54,B=54 */
        const val ASYM_LEFT = 0xFFFF3636.toInt()
        /** Y=128,U=240,V=90 → R=70,G=118,B=255 */
        const val ASYM_RIGHT = 0xFF4676FF.toInt()
        const val MAX_AREA = 1_048_576
    }

    private fun plane(
        bytes: ByteArray,
        rowStride: Int,
        pixelStride: Int = 1,
        position: Int = 0,
        limit: Int = bytes.size,
        order: ByteOrder = ByteOrder.BIG_ENDIAN,
    ): YuvRgbPlane {
        val buffer = ByteBuffer.wrap(bytes)
        buffer.position(position)
        buffer.limit(limit)
        buffer.order(order)
        return YuvRgbPlane(buffer = buffer, rowStride = rowStride, pixelStride = pixelStride)
    }

    private fun planeFromBuffer(
        buffer: ByteBuffer,
        rowStride: Int,
        pixelStride: Int = 1,
    ): YuvRgbPlane = YuvRgbPlane(buffer = buffer, rowStride = rowStride, pixelStride = pixelStride)

    private fun convert(
        width: Int,
        height: Int,
        y: YuvRgbPlane,
        u: YuvRgbPlane,
        v: YuvRgbPlane,
    ): IntArray = Yuv420Rgb.convert(width, height, y, u, v)

    private fun chromaWidth(width: Int): Int = (width + 1) / 2

    private fun chromaHeight(height: Int): Int = (height + 1) / 2

    /** Contiguous Y plane, pixelStride=1, rowStride=width. */
    private fun contiguousY(width: Int, height: Int, fill: Int): ByteArray =
        ByteArray(width * height) { fill.toByte() }

    /** Contiguous U or V plane for YUV420 size. */
    private fun contiguousChroma(width: Int, height: Int, fill: Int): ByteArray =
        ByteArray(chromaWidth(width) * chromaHeight(height)) { fill.toByte() }

    private fun snapshot(buffer: ByteBuffer): BufferSnapshot =
        BufferSnapshot(
            position = buffer.position(),
            limit = buffer.limit(),
            order = buffer.order(),
            arrayCopy = if (buffer.hasArray() && !buffer.isReadOnly) {
                buffer.array().copyOf()
            } else {
                null
            },
            directBytes = if (!buffer.hasArray() || buffer.isReadOnly) {
                val dup = buffer.duplicate()
                val bytes = ByteArray(dup.remaining())
                dup.get(bytes)
                bytes
            } else {
                null
            },
        )

    private fun assertUnchanged(before: BufferSnapshot, buffer: ByteBuffer) {
        assertEquals(before.position, buffer.position())
        assertEquals(before.limit, buffer.limit())
        assertEquals(before.order, buffer.order())
        if (before.arrayCopy != null) {
            assertArrayEquals(before.arrayCopy, buffer.array())
        }
        if (before.directBytes != null) {
            val dup = buffer.duplicate()
            val after = ByteArray(dup.remaining())
            dup.get(after)
            assertArrayEquals(before.directBytes, after)
            assertEquals(before.position, buffer.position())
        }
    }

    private data class BufferSnapshot(
        val position: Int,
        val limit: Int,
        val order: ByteOrder,
        val arrayCopy: ByteArray?,
        val directBytes: ByteArray?,
    )

    // --- solid limited-range primaries ---

    @Test
    fun blackTwoByTwoIsOpaqueBlackArgb() {
        val width = 2
        val height = 2
        val y = plane(contiguousY(width, height, 16), rowStride = width)
        val u = plane(contiguousChroma(width, height, 128), rowStride = chromaWidth(width))
        val v = plane(contiguousChroma(width, height, 128), rowStride = chromaWidth(width))
        assertArrayEquals(intArrayOf(BLACK, BLACK, BLACK, BLACK), convert(width, height, y, u, v))
    }

    @Test
    fun whiteTwoByTwoIsOpaqueWhiteArgb() {
        val width = 2
        val height = 2
        val y = plane(contiguousY(width, height, 235), rowStride = width)
        val u = plane(contiguousChroma(width, height, 128), rowStride = chromaWidth(width))
        val v = plane(contiguousChroma(width, height, 128), rowStride = chromaWidth(width))
        assertArrayEquals(intArrayOf(WHITE, WHITE, WHITE, WHITE), convert(width, height, y, u, v))
    }

    @Test
    fun studioRedTwoByTwoIsOpaqueRedArgb() {
        // Classic limited BT.601 red: Y=81, U=90, V=240
        val width = 2
        val height = 2
        val y = plane(contiguousY(width, height, 81), rowStride = width)
        val u = plane(contiguousChroma(width, height, 90), rowStride = chromaWidth(width))
        val v = plane(contiguousChroma(width, height, 240), rowStride = chromaWidth(width))
        assertArrayEquals(intArrayOf(RED, RED, RED, RED), convert(width, height, y, u, v))
    }

    @Test
    fun oneByOneNeutralLumaUsesSingleChromaSample() {
        val y = plane(byteArrayOf(16), rowStride = 1)
        val u = plane(byteArrayOf(128.toByte()), rowStride = 1)
        val v = plane(byteArrayOf(128.toByte()), rowStride = 1)
        assertArrayEquals(intArrayOf(BLACK), convert(1, 1, y, u, v))
    }

    // --- asymmetric U/V (chroma grid indexing) ---

    @Test
    fun asymmetricUvFourByTwoSplitsLeftAndRightChromaBlocks() {
        // Y=128 everywhere. Left chroma U=90,V=240; right U=240,V=90.
        // Hand values: left → 0xFFFF3636; right → 0xFF4676FF.
        val width = 4
        val height = 2
        val yBytes = ByteArray(width * height) { 128.toByte() }
        val uBytes = byteArrayOf(90.toByte(), 240.toByte()) // chroma 2×1
        val vBytes = byteArrayOf(240.toByte(), 90.toByte())
        val y = plane(yBytes, rowStride = width)
        val u = plane(uBytes, rowStride = 2)
        val v = plane(vBytes, rowStride = 2)
        val argb = convert(width, height, y, u, v)
        assertEquals(8, argb.size)
        // row 0
        assertEquals(ASYM_LEFT, argb[0])
        assertEquals(ASYM_LEFT, argb[1])
        assertEquals(ASYM_RIGHT, argb[2])
        assertEquals(ASYM_RIGHT, argb[3])
        // row 1 shares the same chroma row
        assertEquals(ASYM_LEFT, argb[4])
        assertEquals(ASYM_LEFT, argb[5])
        assertEquals(ASYM_RIGHT, argb[6])
        assertEquals(ASYM_RIGHT, argb[7])
    }

    // --- odd dimensions (ceil chroma) ---

    @Test
    fun oddWidthThreeByOneSharesChromaZeroForFirstTwoPixels() {
        // Y=[16,235,81], U=[128,90], V=[128,240]
        // x0 → black (chroma0), x1 → white (chroma0), x2 → red (chroma1)
        val y = plane(byteArrayOf(16, 235.toByte(), 81), rowStride = 3)
        val u = plane(byteArrayOf(128.toByte(), 90), rowStride = 2)
        val v = plane(byteArrayOf(128.toByte(), 240.toByte()), rowStride = 2)
        assertArrayEquals(intArrayOf(BLACK, WHITE, RED), convert(3, 1, y, u, v))
    }

    @Test
    fun oddHeightAndWidthThreeByThreeUsesCeilChromaGrid() {
        // chroma 2×2. Fill Y black except bottom-right 2×2 block studio-red via chroma(1,1).
        // Y layout row-major; chroma(cx,cy) shared by x in[2*cx,2*cx+1], y in[2*cy,2*cy+1]
        // clipped to image.
        val yBytes = byteArrayOf(
            16, 16, 16,
            16, 16, 16,
            16, 16, 81,
        )
        val uBytes = byteArrayOf(
            128.toByte(), 128.toByte(),
            128.toByte(), 90,
        )
        val vBytes = byteArrayOf(
            128.toByte(), 128.toByte(),
            128.toByte(), 240.toByte(),
        )
        val argb = convert(
            3,
            3,
            plane(yBytes, rowStride = 3),
            plane(uBytes, rowStride = 2),
            plane(vBytes, rowStride = 2),
        )
        assertEquals(9, argb.size)
        // (0,0)(1,0)(2,0)(0,1)(1,1)(2,1)(0,2)(1,2) black; (2,2) red
        for (i in 0 until 8) {
            assertEquals("index=$i", BLACK, argb[i])
        }
        assertEquals(RED, argb[8])
    }

    // --- padded / interleaved strides ---

    @Test
    fun paddedYRowsSkipTrailingRowBytes() {
        // 2×2, Y rowStride=5: indices 0,1 then 5,6 used; pads 2,3,4 and 7 must be ignored.
        val yBytes = byteArrayOf(
            16, 235.toByte(), 7, 7, 7,
            81, 16, 7, 7, 7,
        )
        val u = plane(byteArrayOf(128.toByte()), rowStride = 1)
        val v = plane(byteArrayOf(128.toByte()), rowStride = 1)
        // chroma shared: all neutral → black, white, red, black
        // Wait: single chroma for 2×2 is one sample — cannot get different colours per pixel
        // from chroma. Different Y with neutral UV:
        // (0,0) Y=16 → BLACK; (1,0) Y=235 → WHITE; (0,1) Y=81 → not red without red chroma.
        // With U=V=128: Y=81 → C=65 → R=G=B=(298*65+128)>>8=76 → 0xFF4C4C4C
        val gray81 = 0xFF4C4C4C.toInt()
        val y = plane(yBytes, rowStride = 5, limit = 10)
        assertArrayEquals(
            intArrayOf(BLACK, WHITE, gray81, BLACK),
            convert(2, 2, y, u, v),
        )
    }

    @Test
    fun interleavedUvPixelStrideTwoReadsSeparatePlaneViews() {
        // 2×2 solid studio red. Backing UV: [U, V] with pixelStride=2 on each plane.
        val uv = byteArrayOf(90, 240.toByte())
        val uBuf = ByteBuffer.wrap(uv)
        uBuf.position(0)
        uBuf.limit(2)
        val vBuf = ByteBuffer.wrap(uv)
        vBuf.position(1)
        vBuf.limit(2)
        val y = plane(contiguousY(2, 2, 81), rowStride = 2)
        val u = planeFromBuffer(uBuf, rowStride = 2, pixelStride = 2)
        val v = planeFromBuffer(vBuf, rowStride = 2, pixelStride = 2)
        assertArrayEquals(intArrayOf(RED, RED, RED, RED), convert(2, 2, y, u, v))
    }

    @Test
    fun interleavedUvWithPaddedChromaRows() {
        // chroma 2×1 for 4×1 image; U/V interleaved in one row with pad byte after pair.
        // Layout: U0,V0,U1,V1,pad → rowStride=5, pixelStride=2
        // Left black (Y=16,U=128,V=128), right red (Y=81,U=90,V=240)
        val y = plane(byteArrayOf(16, 16, 81, 81), rowStride = 4)
        val uv = byteArrayOf(128.toByte(), 128.toByte(), 90, 240.toByte(), 0x55)
        val uBuf = ByteBuffer.wrap(uv)
        uBuf.position(0)
        uBuf.limit(5)
        val vBuf = ByteBuffer.wrap(uv)
        vBuf.position(1)
        vBuf.limit(5)
        val argb = convert(
            4,
            1,
            y,
            planeFromBuffer(uBuf, rowStride = 5, pixelStride = 2),
            planeFromBuffer(vBuf, rowStride = 5, pixelStride = 2),
        )
        assertArrayEquals(intArrayOf(BLACK, BLACK, RED, RED), argb)
    }

    // --- buffer position / limit / order / mutation ---

    @Test
    fun offsetPositionAndRestrictedLimitAreHonouredWithoutMutation() {
        val yRaw = byteArrayOf(9, 9, 16, 235.toByte(), 81, 16, 9, 9)
        val yBuf = ByteBuffer.wrap(yRaw)
        yBuf.position(2)
        yBuf.limit(6)
        yBuf.order(ByteOrder.LITTLE_ENDIAN)
        val uRaw = byteArrayOf(1, 128.toByte(), 2)
        val uBuf = ByteBuffer.wrap(uRaw)
        uBuf.position(1)
        uBuf.limit(2)
        val vRaw = byteArrayOf(3, 128.toByte(), 4)
        val vBuf = ByteBuffer.wrap(vRaw)
        vBuf.position(1)
        vBuf.limit(2)
        val beforeY = snapshot(yBuf)
        val beforeU = snapshot(uBuf)
        val beforeV = snapshot(vBuf)
        // 2×2 Y at offset: 16,235 / 81,16 with neutral chroma
        val gray81 = 0xFF4C4C4C.toInt()
        assertArrayEquals(
            intArrayOf(BLACK, WHITE, gray81, BLACK),
            convert(
                2,
                2,
                planeFromBuffer(yBuf, rowStride = 2),
                planeFromBuffer(uBuf, rowStride = 1),
                planeFromBuffer(vBuf, rowStride = 1),
            ),
        )
        assertUnchanged(beforeY, yBuf)
        assertUnchanged(beforeU, uBuf)
        assertUnchanged(beforeV, vBuf)
    }

    @Test
    fun exactRemainingBytesSucceedWhenTightlyLimited() {
        // required Y = (2-1)*2+(2-1)*1+1 = 4; U/V required = 1
        val y = plane(byteArrayOf(16, 16, 16, 16, 99), rowStride = 2, position = 0, limit = 4)
        val u = plane(byteArrayOf(128.toByte(), 99), rowStride = 1, position = 0, limit = 1)
        val v = plane(byteArrayOf(128.toByte(), 99), rowStride = 1, position = 0, limit = 1)
        assertArrayEquals(intArrayOf(BLACK, BLACK, BLACK, BLACK), convert(2, 2, y, u, v))
    }

    @Test
    fun directByteBuffersAreAcceptedWithoutArrayAccess() {
        val y = ByteBuffer.allocateDirect(4)
        y.put(0, 235.toByte())
        y.put(1, 235.toByte())
        y.put(2, 235.toByte())
        y.put(3, 235.toByte())
        val u = ByteBuffer.allocateDirect(1)
        u.put(0, 128.toByte())
        val v = ByteBuffer.allocateDirect(1)
        v.put(0, 128.toByte())
        val beforeY = snapshot(y)
        val beforeU = snapshot(u)
        val beforeV = snapshot(v)
        assertArrayEquals(
            intArrayOf(WHITE, WHITE, WHITE, WHITE),
            convert(
                2,
                2,
                planeFromBuffer(y, rowStride = 2),
                planeFromBuffer(u, rowStride = 1),
                planeFromBuffer(v, rowStride = 1),
            ),
        )
        assertUnchanged(beforeY, y)
        assertUnchanged(beforeU, u)
        assertUnchanged(beforeV, v)
        assertTrue(y.isDirect)
    }

    @Test
    fun readOnlyBuffersAreAcceptedWithoutMutation() {
        val y = ByteBuffer.wrap(contiguousY(2, 2, 81)).asReadOnlyBuffer()
        val u = ByteBuffer.wrap(byteArrayOf(90)).asReadOnlyBuffer()
        val v = ByteBuffer.wrap(byteArrayOf(240.toByte())).asReadOnlyBuffer()
        val beforeY = snapshot(y)
        val beforeU = snapshot(u)
        val beforeV = snapshot(v)
        assertArrayEquals(
            intArrayOf(RED, RED, RED, RED),
            convert(
                2,
                2,
                planeFromBuffer(y, rowStride = 2),
                planeFromBuffer(u, rowStride = 1),
                planeFromBuffer(v, rowStride = 1),
            ),
        )
        assertUnchanged(beforeY, y)
        assertUnchanged(beforeU, u)
        assertUnchanged(beforeV, v)
        assertTrue(y.isReadOnly)
    }

    // --- rejection before access / allocation ---

    @Test
    fun nonPositiveDimensionsAreRejected() {
        val y = plane(byteArrayOf(16), rowStride = 1)
        val u = plane(byteArrayOf(128.toByte()), rowStride = 1)
        val v = plane(byteArrayOf(128.toByte()), rowStride = 1)
        assertThrows(IllegalArgumentException::class.java) { convert(0, 1, y, u, v) }
        assertThrows(IllegalArgumentException::class.java) { convert(1, 0, y, u, v) }
        assertThrows(IllegalArgumentException::class.java) { convert(-1, 2, y, u, v) }
        assertThrows(IllegalArgumentException::class.java) { convert(2, -3, y, u, v) }
    }

    @Test
    fun areaAboveCapIsRejectedViaLongWithoutAllocatingOutput() {
        // 1025*1024 = 1_049_600 > 1_048_576; empty planes still reject on area first.
        val empty = plane(byteArrayOf(), rowStride = 1)
        assertThrows(IllegalArgumentException::class.java) {
            convert(1025, 1024, empty, empty, empty)
        }
        assertThrows(IllegalArgumentException::class.java) {
            convert(Int.MAX_VALUE, Int.MAX_VALUE, empty, empty, empty)
        }
    }

    @Test
    fun areaExactlyAtCapIsAcceptedWhenPlanesAreValid() {
        // 1024*1024 = MAX_AREA. Tiny chroma/luma fill — large but JVM-ok for one gate test.
        val width = 1024
        val height = 1024
        assertEquals(MAX_AREA, width * height)
        val yBytes = ByteArray(MAX_AREA) { 16 }
        val cw = chromaWidth(width)
        val ch = chromaHeight(height)
        val uBytes = ByteArray(cw * ch) { 128.toByte() }
        val vBytes = ByteArray(cw * ch) { 128.toByte() }
        val argb = convert(
            width,
            height,
            plane(yBytes, rowStride = width),
            plane(uBytes, rowStride = cw),
            plane(vBytes, rowStride = cw),
        )
        assertEquals(MAX_AREA, argb.size)
        assertEquals(BLACK, argb[0])
        assertEquals(BLACK, argb[MAX_AREA - 1])
    }

    @Test
    fun pixelStrideBelowOneIsRejected() {
        val y = plane(contiguousY(2, 2, 16), rowStride = 2)
        val u = plane(byteArrayOf(128.toByte()), rowStride = 1)
        val v = plane(byteArrayOf(128.toByte()), rowStride = 1)
        assertThrows(IllegalArgumentException::class.java) {
            convert(2, 2, YuvRgbPlane(y.buffer, rowStride = 2, pixelStride = 0), u, v)
        }
        assertThrows(IllegalArgumentException::class.java) {
            convert(2, 2, y, YuvRgbPlane(u.buffer, rowStride = 1, pixelStride = -1), v)
        }
    }

    @Test
    fun rowStrideTooSmallForPlaneWidthIsRejected() {
        // Y width=3, pixelStride=1 → need rowStride >= 3
        val yBuf = ByteBuffer.wrap(ByteArray(9))
        val u = plane(
            byteArrayOf(128.toByte(), 128.toByte(), 128.toByte(), 128.toByte()),
            rowStride = 2,
        )
        val v = plane(
            byteArrayOf(128.toByte(), 128.toByte(), 128.toByte(), 128.toByte()),
            rowStride = 2,
        )
        assertThrows(IllegalArgumentException::class.java) {
            convert(3, 3, YuvRgbPlane(yBuf, rowStride = 2, pixelStride = 1), u, v)
        }
    }

    @Test
    fun rowStrideTooSmallForInterleavedChromaIsRejected() {
        // chromaWidth=2, pixelStride=2 → need rowStride >= (2-1)*2+1 = 3
        val y = plane(contiguousY(4, 2, 16), rowStride = 4)
        val uv = ByteBuffer.wrap(ByteArray(4))
        assertThrows(IllegalArgumentException::class.java) {
            convert(
                4,
                2,
                y,
                YuvRgbPlane(uv, rowStride = 2, pixelStride = 2),
                YuvRgbPlane(uv.duplicate().apply { position(1) }, rowStride = 2, pixelStride = 2),
            )
        }
    }

    @Test
    fun insufficientRemainingBytesAreRejectedBeforeRead() {
        // Y required for 2×2 stride2 = 4; provide remaining=3
        val yBuf = ByteBuffer.wrap(byteArrayOf(16, 16, 16, 16))
        yBuf.position(1) // remaining=3
        val u = plane(byteArrayOf(128.toByte()), rowStride = 1)
        val v = plane(byteArrayOf(128.toByte()), rowStride = 1)
        val before = snapshot(yBuf)
        assertThrows(IllegalArgumentException::class.java) {
            convert(2, 2, planeFromBuffer(yBuf, rowStride = 2), u, v)
        }
        assertUnchanged(before, yBuf)
    }

    @Test
    fun insufficientChromaRemainingIsRejected() {
        val y = plane(contiguousY(2, 2, 16), rowStride = 2)
        val uBuf = ByteBuffer.wrap(byteArrayOf()) // remaining 0 < 1
        val v = plane(byteArrayOf(128.toByte()), rowStride = 1)
        assertThrows(IllegalArgumentException::class.java) {
            convert(2, 2, y, planeFromBuffer(uBuf, rowStride = 1), v)
        }
    }

    @Test
    fun chromaRowStrideMustCoverCeilDimensions() {
        // 3×1 → chromaWidth=2; U rowStride=1 with pixelStride=1 is too small
        val y = plane(byteArrayOf(16, 16, 16), rowStride = 3)
        val uBuf = ByteBuffer.wrap(byteArrayOf(128.toByte(), 128.toByte()))
        val v = plane(byteArrayOf(128.toByte(), 128.toByte()), rowStride = 2)
        assertThrows(IllegalArgumentException::class.java) {
            convert(3, 1, y, YuvRgbPlane(uBuf, rowStride = 1, pixelStride = 1), v)
        }
    }

    @Test
    fun rejectPathsDoNotMutateBufferState() {
        val yRaw = byteArrayOf(16, 16, 16, 16)
        val yBuf = ByteBuffer.wrap(yRaw)
        yBuf.position(0)
        yBuf.limit(4)
        yBuf.order(ByteOrder.LITTLE_ENDIAN)
        val u = plane(byteArrayOf(128.toByte()), rowStride = 1)
        val v = plane(byteArrayOf(128.toByte()), rowStride = 1)
        val before = snapshot(yBuf)
        assertThrows(IllegalArgumentException::class.java) {
            convert(2, 2, YuvRgbPlane(yBuf, rowStride = 1, pixelStride = 1), u, v)
        }
        assertUnchanged(before, yBuf)
    }

    @Test
    fun yBelowStudioBlackClampsCToZeroWithNeutralChroma() {
        // Y=0 → C=max(0,-16)=0 → same RGB as studio black when U=V=128
        val y = plane(byteArrayOf(0), rowStride = 1)
        val u = plane(byteArrayOf(128.toByte()), rowStride = 1)
        val v = plane(byteArrayOf(128.toByte()), rowStride = 1)
        assertArrayEquals(intArrayOf(BLACK), convert(1, 1, y, u, v))
    }
}
