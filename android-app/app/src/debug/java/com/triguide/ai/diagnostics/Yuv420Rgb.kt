package com.triguide.ai.diagnostics

import java.nio.ByteBuffer

/**
 * One Y, U, or V plane view: [buffer] current [ByteBuffer.position] is the plane
 * origin; [rowStride]/[pixelStride] may describe padded or interleaved layouts.
 * Diagnostic only — never CLEAR / walkability / navigation authority.
 */
internal data class YuvRgbPlane(
    val buffer: ByteBuffer,
    val rowStride: Int,
    val pixelStride: Int,
)

/**
 * Bounded YUV_420_888 → ARGB8888 converter (limited-range BT.601 integers).
 * Full unrotated image, row-major output. Pure JVM — no Android SDK types.
 * Never mutates plane buffers (position / limit / order / bytes).
 *
 * Hot path: writable heap arrays (`ByteBuffer.wrap` / slice) index `array()` with
 * `arrayOffset + position` and precomputed row/chroma offsets. Direct and
 * read-only planes use absolute [ByteBuffer.get]; same BT.601 pixels.
 */
internal object Yuv420Rgb {
    private const val MAX_AREA: Long = 1_048_576L

    fun convert(
        width: Int,
        height: Int,
        y: YuvRgbPlane,
        u: YuvRgbPlane,
        v: YuvRgbPlane,
    ): IntArray {
        if (width <= 0 || height <= 0) {
            throw IllegalArgumentException("width and height must be positive")
        }
        val area = width.toLong() * height.toLong()
        if (area > MAX_AREA) {
            throw IllegalArgumentException("image area exceeds $MAX_AREA")
        }

        val chromaW = (width + 1) / 2
        val chromaH = (height + 1) / 2

        // Validate all planes before any sample access or output allocation.
        validatePlane(y, width, height, "Y")
        validatePlane(u, chromaW, chromaH, "U")
        validatePlane(v, chromaW, chromaH, "V")

        val out = IntArray(area.toInt())
        val yBuf = y.buffer
        val uBuf = u.buffer
        val vBuf = v.buffer
        val yOrigin = writableHeapOrigin(yBuf)
        val uOrigin = writableHeapOrigin(uBuf)
        val vOrigin = writableHeapOrigin(vBuf)
        if (yOrigin != null && uOrigin != null && vOrigin != null) {
            convertHeap(
                out,
                width,
                height,
                yBuf.array(),
                yOrigin,
                y.rowStride,
                y.pixelStride,
                uBuf.array(),
                uOrigin,
                u.rowStride,
                u.pixelStride,
                vBuf.array(),
                vOrigin,
                v.rowStride,
                v.pixelStride,
            )
        } else {
            convertBuffers(
                out,
                width,
                height,
                yBuf,
                yBuf.position(),
                y.rowStride,
                y.pixelStride,
                uBuf,
                uBuf.position(),
                u.rowStride,
                u.pixelStride,
                vBuf,
                vBuf.position(),
                v.rowStride,
                v.pixelStride,
            )
        }
        return out
    }

    /**
     * Writable heap origin, or null when the plane must use absolute buffer gets
     * (direct / read-only / non-array). Uses Long so `arrayOffset + position`
     * cannot silently wrap before the fast path.
     */
    private fun writableHeapOrigin(buffer: ByteBuffer): Int? {
        if (!buffer.hasArray() || buffer.isReadOnly) {
            return null
        }
        val origin = buffer.arrayOffset().toLong() + buffer.position().toLong()
        if (origin < 0L || origin > Int.MAX_VALUE.toLong()) {
            return null
        }
        return origin.toInt()
    }

    /**
     * Copied/wrapped heap planes: ByteArray indexing only. Stride validation has
     * already proved every final sample index fits in Int relative to position;
     * origins are Int-checked above.
     */
    private fun convertHeap(
        out: IntArray,
        width: Int,
        height: Int,
        yArr: ByteArray,
        yBase: Int,
        yRow: Int,
        yPix: Int,
        uArr: ByteArray,
        uBase: Int,
        uRow: Int,
        uPix: Int,
        vArr: ByteArray,
        vBase: Int,
        vRow: Int,
        vPix: Int,
    ) {
        var i = 0
        var row = 0
        while (row < height) {
            val cy = row shr 1
            val uRowOff = uBase + cy * uRow
            val vRowOff = vBase + cy * vRow
            val yRow0 = yBase + row * yRow
            i = fillRowHeap(out, i, width, yArr, yRow0, yPix, uArr, uRowOff, uPix, vArr, vRowOff, vPix)
            row++
            // Odd-height last row: cy does not match a following pair member.
            if (row < height && (row shr 1) == cy) {
                val yRow1 = yBase + row * yRow
                i = fillRowHeap(out, i, width, yArr, yRow1, yPix, uArr, uRowOff, uPix, vArr, vRowOff, vPix)
                row++
            }
        }
    }

    private fun fillRowHeap(
        out: IntArray,
        start: Int,
        width: Int,
        yArr: ByteArray,
        yRowOff: Int,
        yPix: Int,
        uArr: ByteArray,
        uRowOff: Int,
        uPix: Int,
        vArr: ByteArray,
        vRowOff: Int,
        vPix: Int,
    ): Int {
        var i = start
        var col = 0
        while (col < width) {
            val cx = col shr 1
            val uVal = uArr[uRowOff + cx * uPix].toInt() and 0xFF
            val vVal = vArr[vRowOff + cx * vPix].toInt() and 0xFF
            out[i++] = yuvToArgb(yArr[yRowOff + col * yPix].toInt() and 0xFF, uVal, vVal)
            col++
            if (col < width && (col shr 1) == cx) {
                out[i++] = yuvToArgb(yArr[yRowOff + col * yPix].toInt() and 0xFF, uVal, vVal)
                col++
            }
        }
        return i
    }

    /** Direct / read-only / non-heap: absolute get; same loop structure and BT.601. */
    private fun convertBuffers(
        out: IntArray,
        width: Int,
        height: Int,
        yBuf: ByteBuffer,
        yBase: Int,
        yRow: Int,
        yPix: Int,
        uBuf: ByteBuffer,
        uBase: Int,
        uRow: Int,
        uPix: Int,
        vBuf: ByteBuffer,
        vBase: Int,
        vRow: Int,
        vPix: Int,
    ) {
        var i = 0
        var row = 0
        while (row < height) {
            val cy = row shr 1
            val uRowOff = uBase + cy * uRow
            val vRowOff = vBase + cy * vRow
            val yRow0 = yBase + row * yRow
            i = fillRowBuffers(out, i, width, yBuf, yRow0, yPix, uBuf, uRowOff, uPix, vBuf, vRowOff, vPix)
            row++
            if (row < height && (row shr 1) == cy) {
                val yRow1 = yBase + row * yRow
                i = fillRowBuffers(out, i, width, yBuf, yRow1, yPix, uBuf, uRowOff, uPix, vBuf, vRowOff, vPix)
                row++
            }
        }
    }

    private fun fillRowBuffers(
        out: IntArray,
        start: Int,
        width: Int,
        yBuf: ByteBuffer,
        yRowOff: Int,
        yPix: Int,
        uBuf: ByteBuffer,
        uRowOff: Int,
        uPix: Int,
        vBuf: ByteBuffer,
        vRowOff: Int,
        vPix: Int,
    ): Int {
        var i = start
        var col = 0
        while (col < width) {
            val cx = col shr 1
            val uVal = uBuf.get(uRowOff + cx * uPix).toInt() and 0xFF
            val vVal = vBuf.get(vRowOff + cx * vPix).toInt() and 0xFF
            out[i++] = yuvToArgb(yBuf.get(yRowOff + col * yPix).toInt() and 0xFF, uVal, vVal)
            col++
            if (col < width && (col shr 1) == cx) {
                out[i++] = yuvToArgb(yBuf.get(yRowOff + col * yPix).toInt() and 0xFF, uVal, vVal)
                col++
            }
        }
        return i
    }

    private fun validatePlane(plane: YuvRgbPlane, planeWidth: Int, planeHeight: Int, label: String) {
        val pixelStride = plane.pixelStride
        if (pixelStride < 1) {
            throw IllegalArgumentException("$label pixelStride must be >= 1")
        }
        val rowStride = plane.rowStride
        val minRow = (planeWidth - 1L) * pixelStride.toLong() + 1L
        if (rowStride.toLong() < minRow) {
            throw IllegalArgumentException("$label rowStride too small for plane width")
        }
        val required = requiredBytes(planeWidth, planeHeight, rowStride, pixelStride)
        if (required > plane.buffer.remaining().toLong()) {
            throw IllegalArgumentException("$label buffer truncated for strides")
        }
    }

    /**
     * Bytes needed from the current position to cover the last sample:
     * `(planeHeight-1)*rowStride + (planeWidth-1)*pixelStride + 1`.
     */
    private fun requiredBytes(
        planeWidth: Int,
        planeHeight: Int,
        rowStride: Int,
        pixelStride: Int,
    ): Long {
        val rows = planeHeight - 1L
        val cols = planeWidth - 1L
        if (rows > 0 && rowStride > 0 && rows > Long.MAX_VALUE / rowStride) {
            throw IllegalArgumentException("row span overflows")
        }
        val rowSpan = rows * rowStride.toLong()
        if (cols > 0 && pixelStride > 0 && cols > Long.MAX_VALUE / pixelStride) {
            throw IllegalArgumentException("column span overflows")
        }
        val colSpan = cols * pixelStride.toLong()
        if (rowSpan > Long.MAX_VALUE - colSpan - 1L) {
            throw IllegalArgumentException("plane span overflows")
        }
        return rowSpan + colSpan + 1L
    }

    /** Limited-range BT.601 integer YUV → packed ARGB8888. */
    private fun yuvToArgb(y: Int, u: Int, v: Int): Int {
        val c = (y - 16).coerceAtLeast(0)
        val d = u - 128
        val e = v - 128
        val r = clampChannel((298 * c + 409 * e + 128) shr 8)
        val g = clampChannel((298 * c - 100 * d - 208 * e + 128) shr 8)
        val b = clampChannel((298 * c + 516 * d + 128) shr 8)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun clampChannel(value: Int): Int = when {
        value < 0 -> 0
        value > 255 -> 255
        else -> value
    }
}
